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
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

import hudson.util.VersionNumber;
import javax.annotation.CheckForNull;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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

        @Override public boolean start() throws Exception {
            EnvVars envReduced = new EnvVars(env);
            EnvVars envHost = computer.getEnvironment();
            envReduced.entrySet().removeAll(envHost.entrySet());
            LOGGER.log(Level.FINE, "reduced environment: {0}", envReduced);
            workspace.mkdirs(); // otherwise it may be owned by root when created for -v
            String ws = workspace.getRemote();
            toolName = step.toolName;
            DockerClient dockerClient = new DockerClient(launcher, node, toolName);

            // Add a warning if the docker version is less than 1.4
            VersionNumber dockerVersion = dockerClient.version();
            if (dockerVersion != null) {
                if (dockerVersion.isOlderThan(new VersionNumber("1.4"))) {
                    throw new AbortException("The docker version is less than v1.4. Pipeline functions requiring 'docker exec' will not work e.g. 'docker.inside'.");
                } else if (dockerVersion.isOlderThan(new VersionNumber("1.8"))) {
                    listener.error("The docker version is less than v1.8. Running a 'docker.inside' from inside a container will not work.");
                }
            } else {
                listener.error("Failed to parse docker version. Please note there is a minimum docker version requirement of v1.4.");
            }

            FilePath tempDir = tempDir(workspace);
            tempDir.mkdirs();
            String tmp = tempDir.getRemote();

            Map<String, String> volumes = new LinkedHashMap<String, String>();
            Collection<String> volumesFromContainers = new LinkedHashSet<String>();
            Optional<String> containerId = dockerClient.getContainerIdIfContainerized();
            if (containerId.isPresent()) {
                listener.getLogger().println(node.getDisplayName() + " seems to be running inside container " + containerId.get());
                final Collection<String> mountedVolumes = dockerClient.getVolumes(envHost, containerId.get());
                final String[] dirs = {ws, tmp};
                for (String dir : dirs) {
                    // check if there is any volume which contains the directory
                    boolean found = false;
                    for (String vol : mountedVolumes) {
                        if (dir.startsWith(vol)) {
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

            container = dockerClient.run(env, step.image, step.args, ws, volumes, volumesFromContainers, envReduced, dockerClient.whoAmI(), /* expected to hang until killed */ "cat");
            DockerFingerprints.addRunFacet(dockerClient.getContainerRecord(env, container), run);
            ImageAction.add(step.image, run);
            getContext().newBodyInvoker().
                    withContext(BodyInvoker.mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new Decorator(container, envHost, ws, toolName))).
                    withCallback(new Callback(container, toolName)).
                    start();
            return false;
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

        Decorator(String container, EnvVars envHost, String ws, String toolName) {
            this.container = container;
            this.envHost = Util.mapToEnv(envHost);
            this.ws = ws;
            this.toolName = toolName;
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
                    List<String> prefix = new ArrayList<>(Arrays.asList(executable, "exec", container, "env"));
                    if (ws != null) {
                        FilePath cwd = starter.pwd();
                        if (cwd != null) {
                            String path = cwd.getRemote();
                            if (!path.equals(ws)) {
                                launcher.getListener().getLogger().println("JENKINS-33510: working directory will be " + ws + " not " + path);
                            }
                        }
                    } // otherwise we are loading an old serialized Decorator
                    Set<String> envReduced = new TreeSet<String>(Arrays.asList(starter.envs()));
                    envReduced.removeAll(Arrays.asList(envHost));
                    prefix.addAll(envReduced);
                    // Adapted from decorateByPrefix:
                    starter.cmds().addAll(0, prefix);
                    if (starter.masks() != null) {
                        boolean[] masks = new boolean[starter.masks().length + prefix.size()];
                        System.arraycopy(starter.masks(), 0, masks, prefix.size(), starter.masks().length);
                        starter.masks(masks);
                    }
                    return super.launch(starter);
                }
                @Override public void kill(Map<String,String> modelEnvVars) throws IOException, InterruptedException {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    String executable = getExecutable();
                    if (getInner().launch().cmds(executable, "exec", container, "ps", "-A", "-o", "pid,command", "e").stdout(baos).quiet(true).join() != 0) {
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
                        if (getInner().launch().cmds(cmds).quiet(true).join() != 0) {
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
