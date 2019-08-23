/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.docker.workflow;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.VersionNumber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.docker.workflow.client.WindowsDockerClient;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WithContainerStep extends AbstractStepImpl {
    
    private static final Logger LOGGER = Logger.getLogger(WithContainerStep.class.getName());
    private final @Nonnull String image;
    private String args;
    private String toolName;

    @DataBoundConstructor public WithContainerStep(@Nonnull String image) {
        this.image = image;
    }
    
    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setArgs(String args) {
        this.args = Util.fixEmpty(args);
    }

    public String getArgs() {
        return args;
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    private static void destroy(String container, Launcher launcher, Node node, EnvVars launcherEnv, String toolName) throws Exception {
        new DockerClient(launcher, node, toolName).stop(launcherEnv, container);
    }

    // TODO switch to GeneralNonBlockingStepExecution
    public static class Execution extends AbstractStepExecutionImpl {
        private static final long serialVersionUID = 1;
        @Inject(optional=true) private transient WithContainerStep step;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Computer computer;
        @StepContextParameter private transient Node node;
        @SuppressWarnings("rawtypes") // TODO not compiling on cloudbees.ci
        @StepContextParameter private transient Run run;
        private String container;
        private String toolName;

        public Execution() {
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
            toolName = step.toolName;
            DockerClient dockerClient = launcher.isUnix()
                ? new DockerClient(launcher, node, toolName)
                : new WindowsDockerClient(launcher, node, toolName);

            VersionNumber dockerVersion = dockerClient.version();
            if (dockerVersion != null) {
                if (dockerVersion.isOlderThan(new VersionNumber("1.7"))) {
                    throw new AbortException("The docker version is less than v1.7. Pipeline functions requiring 'docker exec' (e.g. 'docker.inside') or SELinux labeling will not work.");
                } else if (dockerVersion.isOlderThan(new VersionNumber("1.8"))) {
                    listener.error("The docker version is less than v1.8. Running a 'docker.inside' from inside a container will not work.");
                } else if (dockerVersion.isOlderThan(new VersionNumber("1.13"))) {
                    if (!launcher.isUnix())
                        throw new AbortException("The docker version is less than v1.13. Running a 'docker.inside' from inside a Windows container will not work.");
                }
            } else {
                listener.error("Failed to parse docker version. Please note there is a minimum docker version requirement of v1.7.");
            }

            FilePath tempDir = tempDir(workspace);
            tempDir.mkdirs();
            String tmp = getPath(tempDir);

            Map<String, String> volumes = new LinkedHashMap<String, String>();
            Collection<String> volumesFromContainers = new LinkedHashSet<String>();
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
            container = dockerClient.run(env, step.image, step.args, ws, volumes, volumesFromContainers, envReduced, dockerClient.whoAmI(), /* expected to hang until killed */ command);
            final List<String> ps = dockerClient.listProcess(env, container);
            if (!ps.contains(command)) {
                listener.error(
                    "The container started but didn't run the expected command. " +
                        "Please double check your ENTRYPOINT does execute the command passed as docker run argument, " +
                        "as required by official docker images (see https://github.com/docker-library/official-images#consistency for entrypoint consistency requirements).\n" +
                        "Alternatively you can force image entrypoint to be disabled by adding option `--entrypoint=''`.");
            }

            DockerFingerprints.addRunFacet(dockerClient.getContainerRecord(env, container), run);
            ImageAction.add(step.image, run);
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
                destroy(container, launcher, getContext().get(Node.class), env, toolName);
            }
        }

    }

    private static class Decorator extends LauncherDecorator implements Serializable {

        private static final long serialVersionUID = 1;
        private final String container;
        private final String[] envHost;
        private final String ws;
        private final @CheckForNull String toolName;
        private final boolean hasEnv;
        private final boolean hasWorkdir;

        Decorator(String container, EnvVars envHost, String ws, String toolName, VersionNumber dockerVersion) {
            this.container = container;
            this.envHost = Util.mapToEnv(envHost);
            this.ws = ws;
            this.toolName = toolName;
            this.hasEnv = dockerVersion != null && dockerVersion.compareTo(new VersionNumber("1.13.0")) >= 0;
            this.hasWorkdir = dockerVersion != null && dockerVersion.compareTo(new VersionNumber("17.12")) >= 0;
        }

        @Override public Launcher decorate(final Launcher launcher, final Node node) {
            return new Launcher.DecoratedLauncher(launcher) {
                @Override public Proc launch(Launcher.ProcStarter starter) throws IOException {
                    String executable;
                    try {
                        executable = getExecutable();
                    } catch (InterruptedException x) {
                        throw new IOException(x);
                    }
                    List<String> prefix = new ArrayList<>(Arrays.asList(executable, "exec"));
                    List<Boolean> masksPrefixList = new ArrayList<>(Arrays.asList(false, false));
                    if (ws != null) {
                        FilePath cwd = starter.pwd();
                        if (cwd != null) {
                            String path = cwd.getRemote();
                            if (!path.equals(ws)) {
                                if (hasWorkdir) {
                                    prefix.add("--workdir");
                                    masksPrefixList.add(false);
                                    prefix.add(path);
                                    masksPrefixList.add(false);
                                } else {
                                    String safePath = path.replace("'", "'\"'\"'");
                                    starter.cmds().addAll(0, Arrays.asList("sh", "-c", "cd '" + safePath + "'; exec \"$@\"", "--"));
                                }
                            }
                        }
                    } // otherwise we are loading an old serialized Decorator
                    Set<String> envReduced = new TreeSet<String>(Arrays.asList(starter.envs()));
                    envReduced.removeAll(Arrays.asList(envHost));

                    // Remove PATH during `exec` as well.
                    Iterator<String> it = envReduced.iterator();
                    while (it.hasNext()) {
                        if (it.next().startsWith("PATH=")) {
                            it.remove();
                        }
                    }
                    LOGGER.log(Level.FINE, "(exec) reduced environment: {0}", envReduced);
                    if (hasEnv) {
                        for (String e : envReduced) {
                            prefix.add("--env");
                            masksPrefixList.add(false);
                            prefix.add(e);
                            masksPrefixList.add(true);
                        }
                        prefix.add(container);
                        masksPrefixList.add(false);
                    } else {
                        prefix.add(container);
                        masksPrefixList.add(false);
                        prefix.add("env");
                        masksPrefixList.add(false);
                        prefix.addAll(envReduced);
                        masksPrefixList.addAll(envReduced.stream()
                                                         .map(v -> true)
                                                         .collect(Collectors.toList()));
                    }

                    boolean[] originalMasks = starter.masks();
                    if (originalMasks == null) {
                        originalMasks = new boolean[starter.cmds().size()];
                    }

                    // Adapted from decorateByPrefix:
                    starter.cmds().addAll(0, prefix);

                    boolean[] masks = new boolean[originalMasks.length + prefix.size()];
                    boolean[] masksPrefix = new boolean[masksPrefixList.size()];
                    for (int i = 0; i < masksPrefix.length; i++) {
                        masksPrefix[i] = masksPrefixList.get(i);
                    }
                    System.arraycopy(masksPrefix, 0, masks, 0, masksPrefix.length);
                    System.arraycopy(originalMasks, 0, masks, prefix.size(), originalMasks.length);
                    starter.masks(masks);

                    return super.launch(starter);
                }
                @Override public void kill(Map<String,String> modelEnvVars) throws IOException, InterruptedException {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    String executable = getExecutable();
                    if (getInner().launch().cmds(executable, "exec", container, "ps", "-A", "-o", "pid,command", "e").stdout(baos).quiet(true).start().joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, listener) != 0) {
                        throw new IOException("failed to run ps");
                    }
                    List<String> pids = new ArrayList<String>();
                    LINE: for (String line : baos.toString(Charset.defaultCharset().name()).split("\n")) {
                        for (Map.Entry<String,String> entry : modelEnvVars.entrySet()) {
                            // TODO this is imprecise: false positive when argv happens to match KEY=value even if environment does not. Cf. trick in BourneShellScript.
                            if (!line.contains(entry.getKey() + "=" + entry.getValue())) {
                                continue LINE;
                            }
                        }
                        line = line.trim();
                        int spc = line.indexOf(' ');
                        if (spc == -1) {
                            continue;
                        }
                        pids.add(line.substring(0, spc));
                    }
                    LOGGER.log(Level.FINE, "killing {0}", pids);
                    if (!pids.isEmpty()) {
                        List<String> cmds = new ArrayList<>(Arrays.asList(executable, "exec", container, "kill"));
                        cmds.addAll(pids);
                        if (getInner().launch().cmds(cmds).quiet(true).start().joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, listener) != 0) {
                            throw new IOException("failed to run kill");
                        }
                    }
                }
                private String getExecutable() throws IOException, InterruptedException {
                    EnvVars env = new EnvVars();
                    for (String pair : envHost) {
                        env.addLine(pair);
                    }
                    return DockerTool.getExecutable(toolName, node, getListener(), env);
                }
            };
        }

    }

    private static class Callback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 1;
        private final String container;
        private final String toolName;

        Callback(String container, String toolName) {
            this.container = container;
            this.toolName = toolName;
        }

        @Override protected void finished(StepContext context) throws Exception {
            destroy(container, context.get(Launcher.class), context.get(Node.class), context.get(EnvVars.class), toolName);
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "withDockerContainer";
        }

        @Override public String getDisplayName() {
            return "Run build steps inside a Docker container";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public boolean isAdvanced() {
            return true;
        }

    }

}
