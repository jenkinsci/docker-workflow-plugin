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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Optional;
import com.google.inject.Inject;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.VersionNumber;

public class RunContainerStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(WithContainerStep.class.getName());
    private final @Nonnull String image;
    private String args;
    private String toolName;
    private String command;

    @DataBoundConstructor
    public RunContainerStep(@Nonnull String image) {
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

    @DataBoundSetter
    public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return this.command;
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<String> {

        private static final long serialVersionUID = 1;
        @Inject(optional = true) private transient RunContainerStep step;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Computer computer;
        @StepContextParameter private transient Node node;
        @SuppressWarnings("rawtypes")
        @StepContextParameter private transient Run run;
        private String container;
        private String toolName;

        @Override
        protected String run() throws Exception {
            EnvVars envReduced = new EnvVars(env);
            EnvVars envHost = computer.getEnvironment();
            envReduced.entrySet().removeAll(envHost.entrySet());
            LOGGER.log(Level.FINE, "reduced environment: {0}", envReduced);
            workspace.mkdirs();
            String ws = workspace.getRemote();
            toolName = step.toolName;
            DockerClient dockerClient = new DockerClient(launcher, node, toolName);

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

            FilePath tempDir = tempDir(workspace);
            tempDir.mkdirs();
            String tmp = tempDir.getRemote();

            Map<String, String> volumes = new LinkedHashMap<String, String>();
            Collection<String> volumesFromContainers = new LinkedHashSet<String>();
            Optional<String> containerId = dockerClient.getContainerIdIfContainerized();
            if (containerId.isPresent()) {
                final Collection<String> mountedVolumes = dockerClient.getVolumes(envHost, containerId.get());
                final String[] dirs = {ws, tmp};
                for (String dir : dirs) {
                    boolean found = false;
                    for (String vol : mountedVolumes) {
                        if (dir.startsWith(vol)) {
                            volumesFromContainers.add(containerId.get());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        volumes.put(dir, dir);
                    }
                }
            } else {
                volumes.put(ws, ws);
                volumes.put(tmp, tmp);
            }

            String command = (step.command == null || step.command.trim().length() == 0) ? "cat" : step.command;

            container = dockerClient.run(env, step.image, step.args, ws, volumes, volumesFromContainers, envReduced,
                dockerClient.whoAmI(), command);
            DockerFingerprints.addRunFacet(dockerClient.getContainerRecord(env, container), run);
            ImageAction.add(step.image, run);
            return container;
        }

        private static FilePath tempDir(FilePath ws) {
            return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
        }

    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "runDockerContainer";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps inside a Docker container";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

    }


}
