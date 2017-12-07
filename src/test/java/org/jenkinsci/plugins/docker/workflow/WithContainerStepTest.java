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
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.FileParameterValue;
import hudson.model.Result;
import hudson.tools.ToolProperty;
import java.io.File;
import java.util.Collections;
import java.util.logging.Level;

import hudson.util.VersionNumber;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.custom.CustomConfig;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.durabletask.BourneShellScript;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class WithContainerStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public LoggerRule logging = new LoggerRule();
    
    @Test public void configRoundTrip() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WithContainerStep s1 = new WithContainerStep("java");
                s1.setArgs("--link db:db");
                story.j.assertEqualDataBoundBeans(s1, new StepConfigTester(story.j).configRoundTrip(s1));
                story.j.jenkins.getDescriptorByType(DockerTool.DescriptorImpl.class).setInstallations(new DockerTool("docker15", "/usr/local/docker15", Collections.<ToolProperty<?>>emptyList()));
                s1.setToolName("docker15");
                story.j.assertEqualDataBoundBeans(s1, new StepConfigTester(story.j).configRoundTrip(s1));
            }
        });
    }

    @Test public void basics() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('httpd:2.4.12') {\n" +
                    "    sh 'cp /usr/local/apache2/conf/extra/httpd-userdir.conf .; ls -la'\n" +
                    "  }\n" +
                    "  sh 'ls -la; cat *.conf'\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("Require method GET POST OPTIONS", b);
            }
        });
    }

    @Issue("JENKINS-37719")
    @Test public void hungDaemon() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                Assume.assumeThat("we are in an interactive environment and can pause dockerd", new ProcessBuilder("sudo", "pgrep", "dockerd").inheritIO().start().waitFor(), Matchers.is(0));
                logging.record("org.jenkinsci.plugins.workflow.support.concurrent.Timeout", Level.FINE); // TODO use Timeout.class when workflow-support 2.13+
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  timeout(time: 20, unit: 'SECONDS') {\n" +
                    "    withDockerContainer('httpd:2.4.12') {\n" +
                    "      sh 'sleep infinity'\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
                int origTimeout = DockerClient.CLIENT_TIMEOUT;
                DockerClient.CLIENT_TIMEOUT = 10;
                try {
                    WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                    story.j.waitForMessage("+ sleep infinity", b);
                    Assume.assumeThat(new ProcessBuilder("sudo", "killall", "-STOP", "dockerd").inheritIO().start().waitFor(), Matchers.is(0));
                    try {
                        story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b));
                    } finally {
                        Assume.assumeThat(new ProcessBuilder("sudo", "killall", "-CONT", "dockerd").inheritIO().start().waitFor(), Matchers.is(0));
                    }
                } finally {
                    DockerClient.CLIENT_TIMEOUT = origTimeout;
                }
            }
        });
    }

    @Test public void stop() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('httpd:2.4.12') {\n" +
                    "    sh 'trap \\'echo got SIGTERM\\' TERM; trap \\'echo exiting; exit 99\\' EXIT; echo sleeping now with JENKINS_SERVER_COOKIE=$JENKINS_SERVER_COOKIE; sleep 999'\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                story.j.waitForMessage("sleeping now", b);
                b.doStop();
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b));
                story.j.assertLogContains("script returned exit code 99", b);
            }
        });
    }

    @Test public void death() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                logging.record(BourneShellScript.class, Level.FINE);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('httpd:2.4.12') {\n" +
                    "    sh \"sleep 5; ps -e -o pid,command | egrep '${pwd tmp: true}/durable-.+/script.sh' | fgrep -v grep | sort -n | tr -s ' ' | cut -d ' ' -f2 | xargs kill -9\"\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
                story.j.assertLogContains("script returned exit code -1", b);
            }
        });
    }

    @Test public void restart() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('httpd:2.4.12') {\n" +
                    "    semaphore 'wait'\n" +
                    "    sh 'cat /usr/local/apache2/conf/extra/httpd-userdir.conf'\n" +
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
                story.j.assertLogContains("Require method GET POST OPTIONS", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Issue("JENKINS-32943")
    @Test public void fileCredentials() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                File f = tmp.newFile("some-file");
                FileUtils.write(f, "some-content");
                FileItem fi = new FileParameterValue.FileItemImpl(f);
                FileCredentialsImpl fc = new FileCredentialsImpl(CredentialsScope.GLOBAL, "secretfile", "", fi, fi.getName(), null);
                CredentialsProvider.lookupStores(story.j.jenkins).iterator().next().addCredentials(Domain.global(), fc);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('ubuntu') {\n" +
                    "    withCredentials([[$class: 'FileBinding', credentialsId: 'secretfile', variable: 'FILE']]) {\n" +
                    "      sh 'cat $FILE'\n" +
                    "    }\n" +
                    "  }\n" +
                    "  withCredentials([[$class: 'FileBinding', credentialsId: 'secretfile', variable: 'FILE']]) {\n" +
                    "    withDockerContainer('ubuntu') {\n" +
                    "      sh 'tr \"a-z\" \"A-Z\" < $FILE'\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("some-content", b);
                story.j.assertLogContains("SOME-CONTENT", b);
            }
        });
    }

    @Issue("JENKINS-27152")
    @Test public void configFile() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                ConfigProvider configProvider = story.j.jenkins.getExtensionList(ConfigProvider.class).get(CustomConfig.CustomConfigProvider.class);
                String id = configProvider.getProviderId() + "myfile";
                Config config = new CustomConfig(id, "My File", "", "some-content");
                configProvider.save(config);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('ubuntu') {\n" +
                        "  wrap([$class: 'ConfigFileBuildWrapper', managedFiles: [[fileId: '" + config.id + "', variable: 'FILE']]]) {\n" +
                        "    sh 'cat $FILE'\n" +
                        "  }\n" +
                    "  }\n" +
                    "  wrap([$class: 'ConfigFileBuildWrapper', managedFiles: [[fileId: '" + config.id + "', variable: 'FILE']]]) {\n" +
                    "    withDockerContainer('ubuntu') {\n" +
                    "      sh 'tr \"a-z\" \"A-Z\" < $FILE'\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("some-content", b);
                story.j.assertLogContains("SOME-CONTENT", b);
            }
        });
    }

    @Issue("JENKINS-33510")
    @Test public void cd() throws Exception {

        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker(new VersionNumber("17.12"));
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('ubuntu') {\n" +
                    "    sh 'mkdir subdir && echo somecontent > subdir/file'\n" +
                    "    dir('subdir') {\n" +
                    "      sh 'pwd; tr \"a-z\" \"A-Z\" < file'\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("SOMECONTENT", b);
            }
        });
    }

    @Ignore("TODO reproducible")
    @Issue("JENKINS-40101")
    @Test public void wheezy() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('debian:wheezy') {\n" +
                    "    sh 'sleep 30s && echo ran OK'\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("ran OK", b);
            }
        });
    }

}
