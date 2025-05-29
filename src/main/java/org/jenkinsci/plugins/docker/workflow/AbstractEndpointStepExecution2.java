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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterial;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterial2;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterialFactory;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

abstract class AbstractEndpointStepExecution2 extends GeneralNonBlockingStepExecution {

    private static final long serialVersionUID = 1;

    protected AbstractEndpointStepExecution2(StepContext context) {
        super(context);
    }

    protected abstract KeyMaterialFactory newKeyMaterialFactory() throws IOException, InterruptedException;

    @Override public final boolean start() throws Exception {
        run(this::doStart);
        return false;
    }

    private void doStart() throws Exception {
        KeyMaterialFactory keyMaterialFactory = newKeyMaterialFactory();
        KeyMaterial2 material = keyMaterialFactory.materialize2();
        getContext().newBodyInvoker().
                withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new Expander2(material))).
                withCallback(new Callback2(material)).
                start();
    }

    private static class Expander2 extends EnvironmentExpander {

        private static final long serialVersionUID = 1;
        private final KeyMaterial2 material;

        Expander2(KeyMaterial2 material) {
            this.material = material;
        }

        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            env.putAll(material.env());
        }

    }

    private class Callback2 extends TailCall {

        private static final long serialVersionUID = 1;
        private final KeyMaterial2 material;

        Callback2(KeyMaterial2 material) {
            this.material = material;
        }

        @Override protected void finished(StepContext context) throws Exception {
            try {
                material.close(context.get(FilePath.class).getChannel());
            } catch (IOException | InterruptedException x) {
                Logger.getLogger(AbstractEndpointStepExecution2.class.getName()).log(Level.WARNING, null, x);
            }
        }

    }

    @SuppressFBWarnings(value = {"NP_UNWRITTEN_FIELD", "UWF_NULL_FIELD"})
    @Deprecated
    private static class Expander extends EnvironmentExpander {

        private static final long serialVersionUID = 1;
        private final KeyMaterial material = null;

        private Expander() {
            assert false : "only deserialized";
        }

        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            env.putAll(material.env());
        }

    }

    @SuppressFBWarnings(value = {"NP_UNWRITTEN_FIELD", "UWF_NULL_FIELD"})
    @Deprecated
    private class Callback extends TailCall {

        private static final long serialVersionUID = 1;
        private final KeyMaterial material = null;

        private Callback() {
            assert false : "only deserialized";
        }

        @Override protected void finished(StepContext context) throws Exception {
            try {
                material.close();
            } catch (IOException x) {
                Logger.getLogger(AbstractEndpointStepExecution2.class.getName()).log(Level.WARNING, null, x);
            }
        }

    }

}
