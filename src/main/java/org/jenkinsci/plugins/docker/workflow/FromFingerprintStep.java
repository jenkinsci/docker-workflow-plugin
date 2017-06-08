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
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class FromFingerprintStep extends AbstractStepImpl {

    private final String dockerfile;
    private final String image;
    private final List<String> buildArgs;
    private String toolName;

    @DataBoundConstructor public FromFingerprintStep(String dockerfile, String image, List<String> buildArgs) {
        this.dockerfile = dockerfile;
        this.image = image;
        this.buildArgs = buildArgs;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public String getImage() {
        return image;
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject(optional=true) private transient FromFingerprintStep step;
        @SuppressWarnings("rawtypes") // TODO not compiling on cloudbees.ci
        @StepContextParameter private transient Run run;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient EnvVars env;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Node node;

        @Override protected Void run() throws Exception {
            Set<String> beforeFromArgs = new HashSet<>();
            String fromImage = null;
            FilePath dockerfile = workspace.child(step.dockerfile);
            InputStream is = dockerfile.read();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(is, "ISO-8859-1")); // encoding probably irrelevant since image/tag names must be ASCII
                try {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("#")) {
                            continue;
                        }
                        if (line.startsWith("ARG ")) {
                            beforeFromArgs.add(line.substring(4));
                            continue;
                        }
                        if (line.startsWith("FROM ")) {
                            fromImage = line.substring(5);
                            break;
                        }
                    }
                } finally {
                    r.close();
                }
            } finally {
                is.close();
            }
            if (fromImage == null) {
                throw new AbortException("could not find FROM instruction in " + dockerfile);
            }
            DockerClient client = new DockerClient(launcher, node, step.toolName);
            String descendantImageId = client.inspectRequiredField(env, step.image, ".Id");
            if (fromImage.equals("scratch")) { // we just made a base image
                DockerFingerprints.addFromFacet(null, descendantImageId, run);
            } else {
                final HashMap<String, String> argsMap = new HashMap<>();
                // add entry for each ARG clause defined before FROM in Dockerfile (with default value if exist)
                for(String beforeFromArg : beforeFromArgs) {
                    String[] splittedArg = beforeFromArg.split("=");
                    argsMap.put(splittedArg[0], splittedArg.length > 1 ? splittedArg[1] : "");
                }
                // update existing entries with values passed in docker build command using '--build-arg'
                for(String buildArg : step.buildArgs) {
                    String[] splittedArg = buildArg.split("=");
                    if (argsMap.containsKey(splittedArg[0])) {
                        argsMap.put(splittedArg[0], splittedArg.length > 1 ? splittedArg[1] : "");
                    }
                }
                Set<Map.Entry<String, String>> argEntries = argsMap.entrySet();
                for (Map.Entry<String, String> argEntry : argEntries) {
                    fromImage = fromImage.replaceAll("\\$" + argEntry.getKey(), argEntry.getValue());
                }
                DockerFingerprints.addFromFacet(client.inspectRequiredField(env, fromImage, ".Id"), descendantImageId, run);
                ImageAction.add(fromImage, run);
            }
            return null;
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "dockerFingerprintFrom";
        }

        @Override public String getDisplayName() {
            return "Record trace of a Docker image used in FROM";
        }

        @Override public boolean isAdvanced() {
            return true;
        }

    }

}
