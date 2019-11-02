package org.jenkinsci.plugins.docker.workflow;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Node;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.logging.Logger;

public class Callback extends BodyExecutionCallback.TailCall {

    private static final Logger LOGGER = Logger.getLogger(Callback.class.getName());

    private static final long serialVersionUID = 1;
    private final String container;
    private final String toolName;

    Callback(String container, String toolName) {
        this.container = container;
        this.toolName = toolName;
    }

    @Override protected void finished(StepContext context) throws Exception {
        WithContainerStep.destroy(container, context.get(Launcher.class), context.get(Node.class), context.get(EnvVars.class), toolName);
    }

}
