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
import com.google.common.collect.ImmutableSet;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.*;

import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeNotWindows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

public class RegistryEndpointStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-51395")
    @Test public void configRoundTrip() throws Exception {
        { // Recommended syntax.
            SnippetizerTester st = new SnippetizerTester(r);
            RegistryEndpointStep step = new RegistryEndpointStep(new DockerRegistryEndpoint("https://myreg/", null));
            step.setToolName("");
            st.assertRoundTrip(step, "withDockerRegistry(url: 'https://myreg/') {\n    // some block\n}");
            step = new RegistryEndpointStep(new DockerRegistryEndpoint(null, "hubcreds"));
            st.assertRoundTrip(step, "withDockerRegistry(credentialsId: 'hubcreds') {\n    // some block\n}");
            step = new RegistryEndpointStep(new DockerRegistryEndpoint("https://myreg/", "mycreds"));
            step.setToolName("ce");
            st.assertRoundTrip(step, "withDockerRegistry(credentialsId: 'mycreds', toolName: 'ce', url: 'https://myreg/') {\n    // some block\n}");
        }
        { // Older syntax.
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {withDockerRegistry(registry: [url: 'https://docker.my.com/'], toolName: 'irrelevant') {}}", true));
            r.buildAndAssertSuccess(p);
            p.setDefinition(new CpsFlowDefinition("node {withDockerRegistry(registry: [url: 'https://docker.my.com/']) {}}", true));
            r.buildAndAssertSuccess(p);
            p.setDefinition(new CpsFlowDefinition("node {withDockerRegistry([url: 'https://docker.my.com/']) {}}", true));
            r.buildAndAssertSuccess(p);
            // and new, just in case SnippetizerTester is faking it:
            p.setDefinition(new CpsFlowDefinition("node {withDockerRegistry(url: 'https://docker.my.com/') {}}", true));
            r.buildAndAssertSuccess(p);
        }
        { // UI form.
            IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "pass");
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);
            StepConfigTester sct = new StepConfigTester(r);
            RegistryEndpointStep step = new RegistryEndpointStep(new DockerRegistryEndpoint("https://docker.my.com/", "registryCreds"));
            step = sct.configRoundTrip(step);
            DockerRegistryEndpoint registry = step.getRegistry();
            assertNotNull(registry);
            assertEquals("https://docker.my.com/", registry.getUrl());
            assertEquals("registryCreds", registry.getCredentialsId());
            // TODO check toolName
        }
    }

    @Test
    public void stepExecutionWithCredentials() throws Exception {
        assumeNotWindows();
        
        IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "pass");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);

        WorkflowJob p = r.createProject(WorkflowJob.class, "prj");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  mockDockerLoginWithEcho {\n" +
                        "    withDockerRegistry(url: 'https://my-reg:1234', credentialsId: 'registryCreds') {\n" +
                        "       echo 'config would be set up to connect to https://my-reg:1234'\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("docker login -u me -p pass https://my-reg:1234", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        r.assertLogContains("config would be set up to connect to https://my-reg:1234", b);
    }

    @Test
    public void stepExecutionWithCredentialsAndQueueItemAuthenticator() throws Exception {
        assumeNotWindows();

        r.getInstance().setSecurityRealm(r.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice")
                .grant(Computer.BUILD).everywhere().to("alice")
                .grant(Item.CONFIGURE).everywhere().to("alice");
        r.getInstance().setAuthorizationStrategy(auth);

        IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "pass");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);

        WorkflowJob p = r.createProject(WorkflowJob.class, "prj");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  mockDockerLoginWithEcho {\n" +
                        "    withDockerRegistry(url: 'https://my-reg:1234', credentialsId: 'registryCreds') {\n" +
                        "       echo 'config would be set up to connect to https://my-reg:1234'\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true));

        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(
                Collections.singletonMap(p.getName(), User.getById("alice", true).impersonate())));

        WorkflowRun b;
        try (ACLContext as = ACL.as(User.getById("alice", false))) {
            b = r.buildAndAssertSuccess(p);
        }
        r.assertLogContains("docker login -u me -p pass https://my-reg:1234", b);
        r.assertLogContains("config would be set up to connect to https://my-reg:1234", b);
    }

    public static class MockLauncherWithEchoStep extends Step {
        
        @DataBoundConstructor
        public MockLauncherWithEchoStep() {}

        @Override
        public StepExecution start(StepContext stepContext) {
            return new Execution(this, stepContext);
        }

        public static class Execution extends StepExecution {
            private static final long serialVersionUID = 1;

            private final transient MockLauncherWithEchoStep step;

            Execution(MockLauncherWithEchoStep step, StepContext context) {
                super(context);
                this.step = step;
            }
            
            @Override public boolean start() throws Exception {
                getContext().newBodyInvoker().
                        withContext(BodyInvoker.mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new Decorator())).
                        withCallback(BodyExecutionCallback.wrap(getContext())).
                        start();
                return false;
            }

            @Override
            public void stop(@Nonnull Throwable throwable) {
                
            }
        }
        private static class Decorator extends LauncherDecorator implements Serializable {
            private static final long serialVersionUID = 1;
            @Override public Launcher decorate(Launcher launcher, Node node) {
                return launcher.decorateByPrefix("echo");
            }
        }
        @TestExtension public static class DescriptorImpl extends StepDescriptor {

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return ImmutableSet.of(Launcher.class);
            }

            @Override public String getFunctionName() {
                return "mockDockerLoginWithEcho";
            }
            @Override public String getDisplayName() {
                return "Mock Docker Login with Echo";
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }
}