package org.jenkinsci.plugins.docker.workflow;

import com.google.inject.Inject;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.docker.workflow.client.WindowsDockerClient;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Execution extends GeneralNonBlockingStepExecution {

    private static final Logger LOGGER = Logger.getLogger(Execution.class.getName());

    private static final long serialVersionUID = 1;
    @Inject(optional=true) private transient WithContainerStep step;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient FilePath workspace;
    @StepContextParameter private transient EnvVars env;
    @StepContextParameter private transient Computer computer;
    @StepContextParameter private transient Node node;
    @SuppressWarnings("rawtypes") // TODO not compiling on cloudbees.ci
    @StepContextParameter private transient Run run;
    private String container;
    private String toolName;

    protected Execution(StepContext context) {
        super(context);
    }

    @Override public boolean start() throws Exception {
        EnvVars envReduced = new EnvVars(env);
        EnvVars envHost = computer.getEnvironment();
        envReduced.entrySet().removeAll(envHost.entrySet());

        // Remove PATH during cat.
        envReduced.remove("PATH");
        envReduced.remove("");

        LOGGER.log(Level.FINE, "reduced environment: {0}", envReduced);
        workspace.mkdirs(); // otherwise it may be owned by root when created for -v
        String ws = getPath(workspace);
        toolName = step.getToolName();
        DockerClient dockerClient = launcher.isUnix()
            ? new DockerClient(launcher, node, toolName)
            : new WindowsDockerClient(launcher, node, toolName);

        VersionNumber dockerVersion = dockerClient.version();
        if (dockerVersion != null) {
            if (dockerVersion.isOlderThan(new VersionNumber("1.7"))) {
                throw new AbortException("The docker version is less than v1.7. Pipeline functions requiring 'docker exec' (e.g. 'docker.inside') or SELinux labeling will not work.");
            } else if (dockerVersion.isOlderThan(new VersionNumber("1.8"))) {
                listener.error("The docker version is less than v1.8. Running a 'docker.inside' from inside a container will not work.");
            } else if (dockerVersion.isOlderThan(new VersionNumber("1.13")) && !launcher.isUnix()) {
                throw new AbortException("The docker version is less than v1.13. Running a 'docker.inside' from inside a Windows container will not work.");
            }
        }
        else {
            listener.error("Failed to parse docker version. Please note there is a minimum docker version requirement of v1.7.");
        }

        FilePath tempDir = tempDir(workspace);
        tempDir.mkdirs();
        String tmp = getPath(tempDir);

        Map<String, String> volumes = new LinkedHashMap<>();
        Collection<String> volumesFromContainers = new LinkedHashSet<>();
        Optional<String> containerId = dockerClient.getContainerIdIfContainerized();
        if (containerId.isPresent()) {
            listener.getLogger().println(node.getDisplayName() + " seems to be running inside container " + containerId.get());
            final Collection<String> mountedVolumes = dockerClient.getVolumes(env, containerId.get());
            final String[] dirs = {ws, tmp};
            for (String dir : dirs) {
                // check if there is any volume which contains the directory
                boolean found = false;
                for (String vol : mountedVolumes) {
                    boolean dirStartsWithVol = launcher.isUnix()
                        ? dir.startsWith(vol) // Linux
                        : dir.toLowerCase().startsWith(vol.toLowerCase()); // Windows

                    if (dirStartsWithVol) {
                        volumesFromContainers.add(containerId.get());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    listener.getLogger().println("but " + dir + " could not be found among " + mountedVolumes);
                    volumes.put(dir, dir);
                }
            }
        } else {
            listener.getLogger().println(node.getDisplayName() + " does not seem to be running inside a container");
            volumes.put(ws, ws);
            volumes.put(tmp, tmp);
        }

        String command = launcher.isUnix() ? "cat" : "cmd.exe";
        container = dockerClient.run(env, step.getImage(), step.getArgs(), ws, volumes, volumesFromContainers, envReduced, dockerClient.whoAmI(), /* expected to hang until killed */ command);
        final List<String> ps = dockerClient.listProcess(env, container);
        if (!ps.contains(command)) {
            listener.error(
                "The container started but didn't run the expected command. " +
                    "Please double check your ENTRYPOINT does execute the command passed as docker run argument, " +
                    "as required by official docker images (see https://github.com/docker-library/official-images#consistency for entrypoint consistency requirements).\n" +
                    "Alternatively you can force image entrypoint to be disabled by adding option `--entrypoint=''`.");
        }

        DockerFingerprints.addRunFacet(dockerClient.getContainerRecord(env, container), run);
        ImageAction.add(step.getImage(), run);
        getContext().newBodyInvoker().
            withContext(BodyInvoker.mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new Decorator(container, envHost, ws, toolName, dockerVersion))).
            withCallback(new Callback(container, toolName)).
            start();
        return false;
    }

    private String getPath(FilePath filePath)
        throws IOException, InterruptedException {
        if (launcher.isUnix()) {
            return filePath.getRemote();
        } else {
            return filePath.toURI().getPath().substring(1).replace("\\", "/");
        }
    }

    // TODO use 1.652 use WorkspaceList.tempDir
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    @Override public void stop(@Nonnull Throwable cause) throws Exception {
        if (container != null) {
            LOGGER.log(Level.FINE, "stopping container " + container, cause);
            WithContainerStep.destroy(container, launcher, getContext().get(Node.class), env, toolName);
        }
    }

}
