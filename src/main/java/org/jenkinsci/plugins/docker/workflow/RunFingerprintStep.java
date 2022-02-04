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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @deprecated Fingerprints produced by this step are not used anywhere, and the parsing code has major limitations. See https://github.com/jenkinsci/docker-workflow-plugin/pull/149#issuecomment-451305522 and https://groups.google.com/d/msg/jenkinsci-dev/k13SfZcBWVg/iQghmCQrEAAJ
 */
@Deprecated
public class RunFingerprintStep extends AbstractStepImpl {

    private final String containerId;
    private String toolName;

    @DataBoundConstructor public RunFingerprintStep(String containerId) {
        this.containerId = containerId;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        
        private static final long serialVersionUID = 1L;

        @Inject(optional=true) private transient RunFingerprintStep step;
        @SuppressWarnings("rawtypes") // TODO not compiling on cloudbees.ci
        @StepContextParameter private transient Run run;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient Node node;

        @SuppressWarnings("SynchronizeOnNonFinalField") // run is quasi-final
        @Override protected Void run() throws Exception {
            DockerClient client = new DockerClient(launcher, node, step.toolName);
            DockerFingerprints.addRunFacet(client.getContainerRecord(env, step.containerId), run);
            String image = client.inspect(env, step.containerId, ".Config.Image");
            if (image != null) {
                ImageAction.add(image, run);
            }
            return null;
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "dockerFingerprintRun";
        }

        @NonNull
        @Override public String getDisplayName() {
            return "Record trace of a Docker image run in a container";
        }

        @Override public boolean isAdvanced() {
            return true;
        }

    }

}
