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

import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.inject.Inject;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.VersionNumber;

public class InsideContainerStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(WithContainerStep.class.getName());
    private final
    @Nonnull
    String containerId;
    private String toolName;

    @DataBoundConstructor
    public InsideContainerStep(@Nonnull String containerId) {
        this.containerId = containerId;
    }

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

        @Inject(optional = true) private transient InsideContainerStep step;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Computer computer;
        @StepContextParameter private transient Node node;
        private String toolName;

        @Override
        public boolean start() throws Exception {
            DockerClient dockerClient = new DockerClient(launcher, node, toolName);

            // Add a warning if the docker version is less than 1.4
            VersionNumber dockerVersion = dockerClient.version();
            if (dockerVersion != null) {
                if (dockerVersion.isOlderThan(new VersionNumber("1.4"))) {
                    throw new AbortException(
                        "The docker version is less than v1.4. Pipeline functions requiring 'docker exec' will not work e.g. 'docker.inside'.");
                }
            } else {
                listener.error(
                    "Failed to parse docker version. Please note there is a minimum docker version requirement of v1.4.");
            }

            getContext()
                .newBodyInvoker()
                .withContext(BodyInvoker.mergeLauncherDecorators(
                    getContext().get(LauncherDecorator.class),
                    new WithContainerStep.Decorator(step.containerId, computer.getEnvironment(), workspace.getRemote(),
                        toolName))
                )
                .withCallback(new LogFinishCallback())
                .start();

            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
        }
    }

    private static class LogFinishCallback extends BodyExecutionCallback.TailCall {

        @Override
        protected void finished(StepContext context) throws Exception {
            TaskListener listener = context.get(TaskListener.class);
            if (listener != null) {
                listener.getLogger().print("Task finished");
            }
        }

    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "insideDockerContainer";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps inside running Docker container";
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
