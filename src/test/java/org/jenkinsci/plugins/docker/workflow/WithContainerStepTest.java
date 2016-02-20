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

import hudson.model.Result;
import hudson.tools.ToolProperty;
import java.util.Collections;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.workflow.BuildWatcher;
import org.jenkinsci.plugins.workflow.JenkinsRuleExt;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class WithContainerStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    
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
                JenkinsRuleExt.waitForMessage("sleeping now", b);
                b.doStop();
                story.j.assertBuildStatus(Result.ABORTED, JenkinsRuleExt.waitForCompletion(b));
                story.j.assertLogContains("script returned exit code 99", b);
            }
        });
    }

    @Test public void death() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('httpd:2.4.12') {\n" +
                    "    sh 'sleep 5; kill -9 `cat .*/pid`'\n" +
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
                story.j.assertLogContains("Require method GET POST OPTIONS", story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b)));
            }
        });
    }

}
