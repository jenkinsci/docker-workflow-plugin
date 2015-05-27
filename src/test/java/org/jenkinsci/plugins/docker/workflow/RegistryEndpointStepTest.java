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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.workflow.BuildWatcher;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class RegistryEndpointStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void configRoundTrip() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "pass");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);
                StepConfigTester sct = new StepConfigTester(story.j);
                Map<String,Object> registryConfig = new TreeMap<String,Object>();
                registryConfig.put("url", "https://docker.my.com/");
                registryConfig.put("credentialsId", registryCredentials.getId());
                Map<String,Object> config = Collections.<String,Object>singletonMap("registry", registryConfig);
                RegistryEndpointStep step = DescribableHelper.instantiate(RegistryEndpointStep.class, config);
                step = sct.configRoundTrip(step);
                DockerRegistryEndpoint registry = step.getRegistry();
                assertNotNull(registry);
                assertEquals("https://docker.my.com/", registry.getUrl());
                assertEquals(registryCredentials.getId(), registry.getCredentialsId());
                assertEquals(config, DescribableHelper.uninstantiate(step));
           }
        });
    }

}