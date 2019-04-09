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

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterialFactory;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.structs.describable.CustomDescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class RegistryEndpointStep extends Step {
    
    private final @Nonnull DockerRegistryEndpoint registry;
    private @CheckForNull String toolName;

    @DataBoundConstructor public RegistryEndpointStep(@Nonnull DockerRegistryEndpoint registry) {
        assert registry != null;
        this.registry = registry;
    }
    
    public DockerRegistryEndpoint getRegistry() {
        return registry;
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution2(this, context);
    }

    private static final class Execution2 extends AbstractEndpointStepExecution2 {

        private static final long serialVersionUID = 1;

        private transient RegistryEndpointStep step;

        Execution2(RegistryEndpointStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected KeyMaterialFactory newKeyMaterialFactory() throws IOException, InterruptedException {
            TaskListener listener = getContext().get(TaskListener.class);
            EnvVars envVars = getContext().get(EnvVars.class);
            String executable = DockerTool.getExecutable(step.toolName, getContext().get(Node.class), listener, envVars);
            return step.registry.newKeyMaterialFactory(getContext().get(Run.class), getContext().get(FilePath.class), getContext().get(Launcher.class), envVars, listener, executable);
        }

    }

    /** @deprecated only here for binary compatibility */
    @Deprecated
    public static class Execution extends AbstractEndpointStepExecution {

        private static final long serialVersionUID = 1;

    }

    @Extension public static class DescriptorImpl extends StepDescriptor implements CustomDescribableModel {

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

        @Override public Map<String, Object> customInstantiate(Map<String, Object> arguments) {
            arguments = new HashMap<>(arguments);
            if (arguments.containsKey("url") || arguments.containsKey("credentialsId")) {
                if (arguments.containsKey("registry")) {
                    throw new IllegalArgumentException("cannot mix url/credentialsId with registry");
                }
                arguments.put("registry", new DockerRegistryEndpoint((String) arguments.remove("url"), (String) arguments.remove("credentialsId")));
            } else if (!arguments.containsKey("registry")) {
                throw new IllegalArgumentException("must specify url/credentialsId (or registry)");
            }
            return arguments;
        }

        @Override public UninstantiatedDescribable customUninstantiate(UninstantiatedDescribable ud) {
            Object registry = ud.getArguments().get("registry");
            if (registry instanceof UninstantiatedDescribable) {
                Map<String, Object> arguments = new TreeMap<>(ud.getArguments());
                arguments.remove("registry");
                arguments.putAll(((UninstantiatedDescribable) registry).getArguments());
                return ud.withArguments(arguments);
            }
            return ud;
        }
        
        @SuppressWarnings("unchecked")
        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, EnvVars.class, Node.class, Run.class, FilePath.class, Launcher.class);
        }

    }

}
