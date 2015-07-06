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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
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

            // Add a warning if the docker version is less than 1.3
            VersionNumber dockerVersion = dockerClient.version();
            if (dockerVersion != null) {
                if (dockerVersion.isOlderThan(new VersionNumber("1.3"))) {
                    throw new AbortException("The docker version is less than v1.3. Workflow functions requiring 'docker exec' will not work e.g. 'docker.inside'.");
                }
            } else {
                listener.error("Failed to parse docker version. Please note there is a minimum docker version requirement of v1.3.");
            }
            
            container = dockerClient.run(env, step.image, step.args, ws, Collections.singletonMap(ws, ws), envReduced, dockerClient.whoAmI(), /* expected to hang until killed */ "cat");
            DockerFingerprints.addRunFacet(dockerClient.getContainerRecord(env, container), run);
            ImageAction.add(step.image, run);
            getContext().newBodyInvoker().
                    withContext(BodyInvoker.mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new Decorator(container, envHost))).
                    withCallback(new Callback(container, toolName)).
                    start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            if (container != null) {
                destroy(container, launcher, getContext().get(Node.class), env, toolName);
            }
        }

    }

    private static class Decorator extends LauncherDecorator implements Serializable {

        private static final long serialVersionUID = 1;
        private final String container;
        private final String[] envHost;

        Decorator(String container, EnvVars envHost) {
            this.container = container;
            this.envHost = Util.mapToEnv(envHost);
        }

        @Override public Launcher decorate(Launcher launcher, Node node) {
            return new Launcher.DecoratedLauncher(launcher) {
                @Override public Proc launch(Launcher.ProcStarter starter) throws IOException {
                    List<String> prefix = new ArrayList<String>(Arrays.asList("docker", "exec", container, "env"));
                    Set<String> envReduced = new TreeSet<String>(Arrays.asList(starter.envs()));
                    envReduced.removeAll(Arrays.asList(envHost));
                    starter.envs(new String[0]);
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
                    if (getInner().launch().cmds("docker", "exec", container, "ps", "-A", "-o", "pid,command", "e").stdout(baos).quiet(true).join() != 0) {
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
                        List<String> cmds = new ArrayList<String>(Arrays.asList("docker", "exec", container, "kill"));
                        cmds.addAll(pids);
                        if (getInner().launch().cmds(cmds).quiet(true).join() != 0) {
                            throw new IOException("failed to run kill");
                        }
                    }
                }
            };
        }

    }

    private static class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1;
        private final String container;
        private final String toolName;

        Callback(String container, String toolName) {
            this.container = container;
            this.toolName = toolName;
        }

        private void stopContainer(StepContext context) {
            Launcher launcher;
            TaskListener listener;
            try {
                launcher = context.get(Launcher.class);
                listener = context.get(TaskListener.class);
            } catch (Exception x2) {
                LOGGER.log(Level.WARNING, null, x2);
                return;
            }
            try {
                EnvVars launcherEnv = context.get(EnvVars.class);
                Node node = context.get(Node.class);
                destroy(container, launcher, node, launcherEnv, toolName);
            } catch (Exception x) {
                x.printStackTrace(listener.error("Could not kill container " + container));
            }
        }

        @Override public void onSuccess(StepContext context, Object result) {
            stopContainer(context);
            context.onSuccess(result);
        }

        @Override public void onFailure(StepContext context, Throwable t) {
            stopContainer(context);
            context.onFailure(t);
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
