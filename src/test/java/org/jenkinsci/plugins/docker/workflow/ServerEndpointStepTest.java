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
import hudson.security.ACL;
import hudson.security.ACLContext;
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
import org.junit.Test;

import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeNotWindows;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class ServerEndpointStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void configRoundTrip() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                IdCredentials serverCredentials = new DockerServerCredentials(CredentialsScope.GLOBAL, "serverCreds", null, "clientKey", "clientCertificate", "serverCaCertificate");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), serverCredentials);
                StepConfigTester sct = new StepConfigTester(story.j);
                Map<String,Object> serverConfig = new TreeMap<String,Object>();
                serverConfig.put("uri", "tcp://host:2375");
                serverConfig.put("credentialsId", serverCredentials.getId());
                Map<String,Object> config = Collections.<String,Object>singletonMap("server", serverConfig);
                ServerEndpointStep step = DescribableHelper.instantiate(ServerEndpointStep.class, config);
                step = sct.configRoundTrip(step);
                DockerServerEndpoint server = step.getServer();
                assertNotNull(server);
                assertEquals("tcp://host:2375", server.getUri());
                assertEquals(serverCredentials.getId(), server.getCredentialsId());
                assertEquals(config, DescribableHelper.uninstantiate(step));
           }
        });
    }

    @Test public void variables() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeNotWindows();

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerServer(server: [uri: 'tcp://host:1234']) {\n" +
                    "    semaphore 'wait'\n" +
                    "    sh 'echo would be connecting to $DOCKER_HOST'\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertLogContains("would be connecting to tcp://host:1234", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Test public void stepExecutionWithCredentials() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                IdCredentials serverCredentials = new DockerServerCredentials(CredentialsScope.GLOBAL, "serverCreds", null, "clientKey", "clientCertificate", "serverCaCertificate");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), serverCredentials);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "  withDockerServer(server: [uri: 'tcp://host:1234', credentialsId: 'serverCreds']) {\n" +
                                "    sh 'echo would be connecting to $DOCKER_HOST'\n" +
                                "  }\n" +
                                "}", true));
                WorkflowRun b = story.j.buildAndAssertSuccess(p);
                story.j.assertLogContains("would be connecting to tcp://host:1234", b);
            }
        });
    }
    
    @Test public void stepExecutionWithCredentialsAndQueueItemAuthenticator() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                story.j.getInstance().setSecurityRealm(story.j.createDummySecurityRealm());
                MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                        .grant(Jenkins.READ).everywhere().to("alice")
                        .grant(Computer.BUILD).everywhere().to("alice")
                        .grant(Item.CONFIGURE).everywhere().to("alice");
                story.j.getInstance().setAuthorizationStrategy(auth);
                
                IdCredentials serverCredentials = new DockerServerCredentials(CredentialsScope.GLOBAL, "serverCreds", null, "clientKey", "clientCertificate", "serverCaCertificate");
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), serverCredentials);
                
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "  withDockerServer(server: [uri: 'tcp://host:1234', credentialsId: 'serverCreds']) {\n" +
                                "    sh 'echo would be connecting to $DOCKER_HOST'\n" +
                                "  }\n" +
                                "}", true));

                QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(
                        Collections.singletonMap(p.getName(), User.getById("alice", true).impersonate())));

                WorkflowRun b;
                try (ACLContext as = ACL.as(User.getById("alice", false))) {
                    b = story.j.buildAndAssertSuccess(p);
                }
                story.j.assertLogContains("would be connecting to tcp://host:1234", b);
            }
        });
    }

}
