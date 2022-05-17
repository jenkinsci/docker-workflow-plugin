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
import hudson.EnvVars;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterial;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterialFactory;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/** @deprecated only here for binary compatibility */
@Deprecated
abstract class AbstractEndpointStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1;

    protected KeyMaterialFactory newKeyMaterialFactory() throws IOException, InterruptedException {
        throw new AssertionError();
    }

    @Override public final boolean start() throws Exception {
        throw new AssertionError();
    }

    @Override public final void stop(@NonNull Throwable cause) throws Exception {
        // should not need to do anything special
    }

    private static class Expander extends EnvironmentExpander {

        private static final long serialVersionUID = 1;
        private final KeyMaterial material;

        Expander(KeyMaterial material) {
            this.material = material;
        }

        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            env.putAll(material.env());
        }

    }

    private static class Callback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 1;
        private final KeyMaterial material;

        Callback(KeyMaterial material) {
            this.material = material;
        }

        @Override protected void finished(StepContext context) throws Exception {
            try {
                material.close();
            } catch (IOException x) {
                Logger.getLogger(AbstractEndpointStepExecution.class.getName()).log(Level.WARNING, null, x);
            }
        }

    }

}
