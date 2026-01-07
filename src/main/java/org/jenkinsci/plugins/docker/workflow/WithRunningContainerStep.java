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

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.docker.workflow.client.WindowsDockerClient;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Step to run build steps inside an already-running Docker container.
 * Unlike {@link WithContainerStep}, this step does not create or destroy the container.
 */
public class WithRunningContainerStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(WithRunningContainerStep.class.getName());
    private final @NonNull String containerId;
    private String toolName;

    @DataBoundConstructor
    public WithRunningContainerStep(@NonNull String containerId) {
        this.containerId = containerId;
    }

    @NonNull
    public String getContainerId() {
        return containerId;
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter
    public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    public static class Execution extends AbstractStepExecutionImpl {
        private static final long serialVersionUID = 1;
        @Inject(optional = true) private transient WithRunningContainerStep step;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Computer computer;
        @StepContextParameter private transient Node node;
        private String toolName;

        public Execution() {
        }

        @Override
        public boolean start() throws Exception {
            EnvVars envHost = computer.getEnvironment();
            String ws = getPath(workspace);
            toolName = step.toolName;

            DockerClient dockerClient = launcher.isUnix()
                    ? new DockerClient(launcher, node, toolName)
                    : new WindowsDockerClient(launcher, node, toolName);

            VersionNumber dockerVersion = dockerClient.version();

            // Validate that the container exists and is running
            String runningState = dockerClient.inspect(env, step.containerId, ".State.Running");
            if (runningState == null) {
                throw new AbortException("Container " + step.containerId + " does not exist");
            }
            if (!"true".equals(runningState.trim())) {
                throw new AbortException("Container " + step.containerId + " is not running");
            }

            // Use the Decorator from WithContainerStep to wrap commands with docker exec
            getContext().newBodyInvoker()
                    .withContext(BodyInvoker.mergeLauncherDecorators(
                            getContext().get(LauncherDecorator.class),
                            new WithContainerStep.Decorator(step.containerId, envHost, ws, toolName, dockerVersion)))
                    .start();
            return false;
        }

        private String getPath(FilePath filePath) throws IOException, InterruptedException {
            if (launcher.isUnix()) {
                return filePath.getRemote();
            } else {
                return filePath.toURI().getPath().substring(1).replace("\\", "/");
            }
        }

        @Override
        public void stop(@NonNull Throwable cause) throws Exception {
            // Do not stop the container - it is managed externally
            LOGGER.log(Level.FINE, "withRunningDockerContainer step stopped", cause);
        }
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "withRunningDockerContainer";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Run build steps inside an already-running Docker container";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
