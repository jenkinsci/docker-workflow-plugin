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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.User;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.structs.DescribableHelper;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;

import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeNotWindows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class ServerEndpointStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();


    @Test
    void configRoundTrip() throws Throwable {
        story.then(r -> {
                IdCredentials serverCredentials = new DockerServerCredentials(CredentialsScope.GLOBAL, "serverCreds", null, "clientKey", "clientCertificate", "serverCaCertificate");
                CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), serverCredentials);
                StepConfigTester sct = new StepConfigTester(r);
                Map<String,Object> serverConfig = new TreeMap<>();
                serverConfig.put("uri", "tcp://host:2375");
                serverConfig.put("credentialsId", serverCredentials.getId());
                Map<String,Object> config = Collections.singletonMap("server", serverConfig);
                ServerEndpointStep step = DescribableHelper.instantiate(ServerEndpointStep.class, config);
                step = sct.configRoundTrip(step);
                DockerServerEndpoint server = step.getServer();
                assertNotNull(server);
                assertEquals("tcp://host:2375", server.getUri());
                assertEquals(serverCredentials.getId(), server.getCredentialsId());
                assertEquals(config, DescribableHelper.uninstantiate(step));
           }
        );
    }

    @Test
    void variables() throws Throwable {
        story.then(r -> {
                assumeNotWindows();

                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerServer(server: [uri: 'tcp://host:1234']) {
                            semaphore 'wait'
                            sh 'echo would be connecting to $DOCKER_HOST'
                          }
                        }""", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        );
        story.then(r -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = r.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                r.assertLogContains("would be connecting to tcp://host:1234", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
            }
        );
    }

    @Test
    void stepExecutionWithCredentials() throws Throwable {
        story.then(r -> {
                assumeNotWindows();
                IdCredentials serverCredentials = new DockerServerCredentials(CredentialsScope.GLOBAL, "serverCreds", null, "clientKey", "clientCertificate", "serverCaCertificate");
                CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), serverCredentials);
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerServer(server: [uri: 'tcp://host:1234', credentialsId: 'serverCreds']) {
                            sh 'echo would be connecting to $DOCKER_HOST'
                            sh 'echo DOCKER_TLS_VERIFY=$DOCKER_TLS_VERIFY'
                            sh 'echo DOCKER_CERT_PATH=$DOCKER_CERT_PATH is not empty'
                          }
                        }""", true));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogContains("would be connecting to tcp://host:1234", b);
                r.assertLogContains("DOCKER_TLS_VERIFY=1", b);
                r.assertLogNotContains("DOCKER_CERT_PATH= is not empty", b);
            }
        );
    }

    @Test
    void stepExecutionWithCredentialsAndQueueItemAuthenticator() throws Throwable {
        assumeNotWindows();
        story.then(r -> {
            r.getInstance().setSecurityRealm(r.createDummySecurityRealm());
            MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                    .grant(Jenkins.READ).everywhere().to("alice", "bob")
                    .grant(Computer.BUILD).everywhere().to("alice", "bob")
                    // Item.CONFIGURE implies Credentials.USE_ITEM, which is what CredentialsProvider.findCredentialById
                    // uses when determining whether to include item-scope credentials in the search.
                    .grant(Item.CONFIGURE).everywhere().to("alice");
            r.getInstance().setAuthorizationStrategy(auth);

            IdCredentials serverCredentials = new DockerServerCredentials(CredentialsScope.GLOBAL, "serverCreds", null, "clientKey", "clientCertificate", "serverCaCertificate");
            CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), serverCredentials);

            String script = """
                node {
                  withDockerServer(server: [uri: 'tcp://host:1234', credentialsId: 'serverCreds']) {
                    sh 'echo would be connecting to $DOCKER_HOST'
                    sh 'echo DOCKER_TLS_VERIFY=$DOCKER_TLS_VERIFY'
                    sh 'echo DOCKER_CERT_PATH=$DOCKER_CERT_PATH is not empty'
                  }
                }""";
            WorkflowJob p1 = r.jenkins.createProject(WorkflowJob.class, "prj1");
            p1.setDefinition(new CpsFlowDefinition(script, true));
            WorkflowJob p2 = r.jenkins.createProject(WorkflowJob.class, "prj2");
            p2.setDefinition(new CpsFlowDefinition(script, true));

            QueueItemAuthenticatorConfiguration.get()
                    .getAuthenticators()
                    .replace(new MockQueueItemAuthenticator()
                            .authenticate(
                                    p1.getFullName(),
                                    User.getById("alice", true).impersonate2())
                            .authenticate(
                                    p2.getFullName(), User.getById("bob", true).impersonate2()));

            // Alice has Credentials.USE_ITEM permission and should be able to use the credential.
            WorkflowRun b1 = r.buildAndAssertSuccess(p1);
            r.assertLogContains("would be connecting to tcp://host:1234", b1);
            r.assertLogContains("DOCKER_TLS_VERIFY=1", b1);
            r.assertLogNotContains("DOCKER_CERT_PATH= is not empty", b1);

            // Bob does not have Credentials.USE_ITEM permission and should not be able to use the credential.
            WorkflowRun b2 = r.buildAndAssertSuccess(p2);
            r.assertLogContains("would be connecting to tcp://host:1234", b2);
            r.assertLogContains("DOCKER_TLS_VERIFY=\n", b2);
            r.assertLogContains("DOCKER_CERT_PATH= is not empty", b2);
        });
    }

}
