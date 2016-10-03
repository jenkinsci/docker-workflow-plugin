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
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.TaskListener;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterialFactory;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

public class RegistryEndpointStep extends AbstractStepImpl {
    
    private final @Nonnull DockerRegistryEndpoint registry;

    @DataBoundConstructor public RegistryEndpointStep(@Nonnull DockerRegistryEndpoint registry) {
        assert registry != null;
        this.registry = registry;
    }
    
    public DockerRegistryEndpoint getRegistry() {
        return registry;
    }

    public static class Execution extends AbstractEndpointStepExecution {

        private static final long serialVersionUID = 1;

        @Inject(optional=true) private transient RegistryEndpointStep step;
        @StepContextParameter private transient Job<?,?> job;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;

        @Override protected KeyMaterialFactory newKeyMaterialFactory() throws IOException, InterruptedException {
            return step.registry.newKeyMaterialFactory(job, workspace.getChannel(), launcher, listener);
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "withDockerRegistry";
        }

        @Override public String getDisplayName() {
            return "Sets up Docker registry endpoint";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public boolean isAdvanced() {
            return true;
        }

    }

}
