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
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class RegistryEndpointStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Ignore("fails to run withDockerRegistry without registry: prefix, which is what Snippetizer offers")
    @Issue("JENKINS-51395")
    @Test public void configRoundTrip() throws Exception {
        SnippetizerTester st = new SnippetizerTester(r);
        RegistryEndpointStep step = new RegistryEndpointStep(new DockerRegistryEndpoint("https://myreg/", null));
        st.assertRoundTrip(step, "withDockerRegistry([url: 'https://myreg/']) {\n    // some block\n}");
        step = new RegistryEndpointStep(new DockerRegistryEndpoint(null, "hubcreds"));
        st.assertRoundTrip(step, "withDockerRegistry([credentialsId: 'hubcreds']) {\n    // some block\n}");
        step = new RegistryEndpointStep(new DockerRegistryEndpoint("https://myreg/", "mycreds"));
        step.setToolName("ce");
        st.assertRoundTrip(step, "withDockerRegistry(registry: [credentialsId: 'mycreds', url: 'https://myreg/'], toolName: 'ce') {\n    // some block\n}");
        IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "pass");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);
        StepConfigTester sct = new StepConfigTester(r);
        // TODO use of DescribableModel here is gratuitous; the rest should just test the UI
        Map<String,Object> registryConfig = new TreeMap<>();
        registryConfig.put("url", "https://docker.my.com/");
        registryConfig.put("credentialsId", registryCredentials.getId());
        Map<String,Object> config = Collections.singletonMap("registry", registryConfig);
        step = new DescribableModel<>(RegistryEndpointStep.class).instantiate(config);
        step = sct.configRoundTrip(step);
        DockerRegistryEndpoint registry = step.getRegistry();
        assertNotNull(registry);
        assertEquals("https://docker.my.com/", registry.getUrl());
        assertEquals(registryCredentials.getId(), registry.getCredentialsId());
        assertEquals(config, new DescribableModel<>(RegistryEndpointStep.class).uninstantiate(step));
    }

}
