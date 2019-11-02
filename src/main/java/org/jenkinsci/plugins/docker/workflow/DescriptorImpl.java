package org.jenkinsci.plugins.docker.workflow;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;

@Extension
public class DescriptorImpl extends AbstractStepDescriptorImpl {

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
