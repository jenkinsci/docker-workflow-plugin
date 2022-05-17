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
import groovy.lang.Binding;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import java.util.logging.Logger;

/**
 * Something you should <strong>not copy</strong>. Write plain old {@code Step}s and leave it at that.
 */
@Extension public class DockerDSL extends GlobalVariable {

    /**
     * Escape hatch to restore the old parameter behaviour in <code>docker</code> to &lt;= 1.26 state.
     * Except {@link org.jenkinsci.plugins.docker.commons.credentials.ImageNameValidator#checkUserAndRepo(String)}
     * will still be checked unless it is also bypassed with {@link org.jenkinsci.plugins.docker.commons.credentials.ImageNameValidator#SKIP}.
     */
    /*package*/ static /*almost final*/ boolean UNSAFE_PARAMETER_EXPANDING = Boolean.getBoolean(DockerDSL.class.getName() + ".UNSAFE_PARAMETER_EXPANDING");

    @NonNull
    @Override public String getName() {
        return "docker";
    }

    @NonNull
    @Override public Object getValue(CpsScript script) throws Exception {
        Binding binding = script.getBinding();
        Object docker;
        if (binding.hasVariable(getName())) {
            docker = binding.getVariable(getName());
        } else {
            // Note that if this were a method rather than a constructor, we would need to mark it @NonCPS lest it throw CpsCallableInvocation.
            docker = script.getClass().getClassLoader().loadClass("org.jenkinsci.plugins.docker.workflow.Docker").getConstructor(CpsScript.class).newInstance(script);
            binding.setVariable(getName(), docker);
        }
        return docker;
    }

    private static final Logger log = Logger.getLogger(DockerDSL.class.getName());

    public static boolean isUnsafeParameterExpanding() {
        if (UNSAFE_PARAMETER_EXPANDING) {
            log.fine("WARNING! Unsafe parameter expansion is enabled. (legacy mode)");
        }
        return UNSAFE_PARAMETER_EXPANDING;
    }

}
