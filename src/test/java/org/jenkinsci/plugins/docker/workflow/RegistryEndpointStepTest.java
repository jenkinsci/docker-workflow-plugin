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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.User;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;

public class RegistryEndpointStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();
    @Rule public BuildWatcher bw = new BuildWatcher();

    @Issue("JENKINS-51395")
    @Test public void configRoundTrip() throws Exception {
        logging.record(DescribableModel.class, Level.FINE);
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

        IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "s3cr3t");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);

        WorkflowJob p = r.createProject(WorkflowJob.class, "prj");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  mockDockerLogin {\n" +
                        "    withDockerRegistry(url: 'https://my-reg:1234', credentialsId: 'registryCreds') {\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("docker login -u me -p ******** https://my-reg:1234", b);
        r.assertLogNotContains("s3cr3t", b);
    }

    @Test
    public void stepExecutionWithCredentialsAndQueueItemAuthenticator() throws Exception {
        assumeNotWindows();

        r.getInstance().setSecurityRealm(r.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice", "bob")
                .grant(Computer.BUILD).everywhere().to("alice", "bob")
                // Item.CONFIGURE implies Credentials.USE_ITEM, which is what CredentialsProvider.findCredentialById
                // uses when determining whether to include item-scope credentials in the search.
                .grant(Item.CONFIGURE).everywhere().to("alice");
        r.getInstance().setAuthorizationStrategy(auth);

        IdCredentials registryCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "registryCreds", null, "me", "s3cr3t");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), registryCredentials);

        String script = "node {\n" +
                "  mockDockerLogin {\n" +
                "    withDockerRegistry(url: 'https://my-reg:1234', credentialsId: 'registryCreds') {\n" +
                "    }\n" +
                "  }\n" +
                "}";
        WorkflowJob p1 = r.createProject(WorkflowJob.class, "prj1");
        p1.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowJob p2 = r.createProject(WorkflowJob.class, "prj2");
        p2.setDefinition(new CpsFlowDefinition(script, true));

        Map<String, Authentication> jobsToAuths = new HashMap<>();
        jobsToAuths.put(p1.getFullName(), User.getById("alice", true).impersonate());
        jobsToAuths.put(p2.getFullName(), User.getById("bob", true).impersonate());
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(jobsToAuths));

        // Alice has Credentials.USE_ITEM permission and should be able to use the credential.
        WorkflowRun b1 = r.buildAndAssertSuccess(p1);
        r.assertLogContains("docker login -u me -p ******** https://my-reg:1234", b1);
        r.assertLogNotContains("s3cr3t", b1);

        // Bob does not have Credentials.USE_ITEM permission and should not be able to use the credential.
        r.assertBuildStatus(Result.FAILURE, p2.scheduleBuild2(0));
    }

    public static class MockLauncherStep extends Step {
        
        @DataBoundConstructor
        public MockLauncherStep() {}

        @Override
        public StepExecution start(StepContext stepContext) {
            return new Execution(this, stepContext);
        }

        public static class Execution extends StepExecution {
            private static final long serialVersionUID = 1;

            private final transient MockLauncherStep step;

            Execution(MockLauncherStep step, StepContext context) {
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
            public void stop(@NonNull Throwable throwable) {
                
            }
        }
        private static class Decorator extends LauncherDecorator implements Serializable {
            private static final long serialVersionUID = 1;
            @NonNull
            @Override public Launcher decorate(@NonNull Launcher launcher, @NonNull Node node) {
                return launcher.decorateByPrefix("true");
            }
        }
        @TestExtension public static class DescriptorImpl extends StepDescriptor {

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return ImmutableSet.of(Launcher.class);
            }

            @Override public String getFunctionName() {
                return "mockDockerLogin";
            }
            @NonNull
            @Override public String getDisplayName() {
                return "Mock Docker Login";
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }
}
