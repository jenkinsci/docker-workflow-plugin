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

import groovy.lang.Binding;
import hudson.Extension;
import java.io.IOException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.StaticWhitelist;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

@Extension public class DockerDSL extends GlobalVariable {

    @Override public String getName() {
        return "docker";
    }

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

    @Extension public static class MiscWhitelist extends ProxyWhitelist {
        public MiscWhitelist() throws IOException {
            super(new StaticWhitelist(
                    // TODO should docker-commons just get a script-security dependency and mark these things @Whitelisted?
                    "new org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint java.lang.String java.lang.String",
                    "method org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint imageName java.lang.String"));
        }
    }

}
