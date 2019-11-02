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

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;

import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

public class WithContainerStep extends AbstractStepImpl {
    
    private static final Logger LOGGER = Logger.getLogger(WithContainerStep.class.getName());
    private final @Nonnull String image;
    private String args;
    private String toolName;

    @DataBoundConstructor
    public WithContainerStep(@Nonnull String image) {
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

    public static void destroy(String container, Launcher launcher, Node node, EnvVars launcherEnv, String toolName) throws Exception {
        new DockerClient(launcher, node, toolName).stop(launcherEnv, container);
    }

}
