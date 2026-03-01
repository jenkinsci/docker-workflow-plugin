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
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.collect.ImmutableSet;
import hudson.Launcher;
import hudson.model.FileParameterValue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

import hudson.util.VersionNumber;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;

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
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.kohsuke.stapler.DataBoundConstructor;

class WithContainerStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();
    @TempDir
    private File tmp;
    private final LogRecorder logging = new LogRecorder();

    @Test
    void configRoundTrip() throws Throwable {
        story.then(r -> {
                WithContainerStep s1 = new WithContainerStep("java");
                s1.setArgs("--link db:db");
                r.assertEqualDataBoundBeans(s1, new StepConfigTester(r).configRoundTrip(s1));
                r.jenkins.getDescriptorByType(DockerTool.DescriptorImpl.class).setInstallations(new DockerTool("docker15", "/usr/local/docker15", Collections.emptyList()));
                s1.setToolName("docker15");
                r.assertEqualDataBoundBeans(s1, new StepConfigTester(r).configRoundTrip(s1));
            }
        );
    }

    @Test
    void basics() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('httpd:2.4.59') {
                            sh 'cp /usr/local/apache2/conf/extra/httpd-userdir.conf .; ls -la'
                          }
                          sh 'ls -la; cat *.conf'
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("Require method GET POST OPTIONS", b);
            }
        );
    }

    @Issue("JENKINS-37719")
    @Disabled("Not working locally for cert release")
    @Test
    void hungDaemon() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                Process proc = new ProcessBuilder("sudo", "pgrep", "dockerd").inheritIO().start();
                proc.waitFor(15, TimeUnit.SECONDS);
                assumeTrue(proc.exitValue() == 0, "we are in an interactive environment and can pause dockerd");
                logging.record("org.jenkinsci.plugins.workflow.support.concurrent.Timeout", Level.FINE); // TODO use Timeout.class when workflow-support 2.13+
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          timeout(time: 20, unit: 'SECONDS') {
                            withDockerContainer('httpd:2.4.59') {
                              sh 'sleep infinity'
                            }
                          }
                        }""", true));
                int origTimeout = DockerClient.CLIENT_TIMEOUT;
                DockerClient.CLIENT_TIMEOUT = 10;
                try {
                    WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                    r.waitForMessage("+ sleep infinity", b);
                    proc = new ProcessBuilder("sudo", "killall", "-STOP", "dockerd").inheritIO().start();
                    proc.waitFor(15, TimeUnit.SECONDS);
                    assumeTrue(proc.exitValue() == 0, "could suspend dockerd");
                    try {
                        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
                    } finally {
                        proc = new ProcessBuilder("sudo", "killall", "-CONT", "dockerd").inheritIO().start();
                        proc.waitFor(15, TimeUnit.SECONDS);
                        assumeTrue(proc.exitValue() == 0, "could resume dockerd");
                    }
                } finally {
                    DockerClient.CLIENT_TIMEOUT = origTimeout;
                }
            }
        );
    }

    @Test
    void stop() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('ubuntu:kinetic-20220602') {
                            sh 'trap \\'echo got SIGTERM\\' TERM; trap \\'echo exiting; exit 99\\' EXIT; echo sleeping now with JENKINS_SERVER_COOKIE=$JENKINS_SERVER_COOKIE; sleep 999'
                          }
                        }""", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                r.waitForMessage("sleeping now", b);
                b.doStop();
                r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
                r.assertLogContains("script returned exit code 99", b);
            }
        );
    }

    @Test
    void death() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                logging.record(BourneShellScript.class, Level.FINE);
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('httpd:2.4.54-alpine') {
                            sh "set -e; sleep 5; ps -e -o pid,args | egrep '${pwd tmp: true}/durable-.+/script.sh' | fgrep -v grep | sort -n | tr -s ' ' | cut -d ' ' -f2 | xargs kill -9"
                          }
                        }""", true));
                Field hci = BourneShellScript.class.getDeclaredField("HEARTBEAT_CHECK_INTERVAL");
                hci.setAccessible(true);
                int orig = (int) hci.get(null);
                hci.set(null, 5);
                try {
                    WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
                    r.assertLogContains("script returned exit code -1", b);
                } finally {
                    hci.set(null, orig);
                }
            }
        );
    }

    @Test
    void restart() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('httpd:2.4.59') {
                            semaphore 'wait'
                            sh 'cat /usr/local/apache2/conf/extra/httpd-userdir.conf'
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
                r.assertLogContains("Require method GET POST OPTIONS", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
            }
        );
    }

    @Issue("JENKINS-32943")
    @Test
    void fileCredentials() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                File f = newFile(tmp, "some-file");
                FileUtils.write(f, "some-content\n", StandardCharsets.UTF_8);
                FileItem fi = new FileParameterValue.FileItemImpl(f);
                FileCredentialsImpl fc = new FileCredentialsImpl(CredentialsScope.GLOBAL, "secretfile", "", fi, fi.getName(), (SecretBytes) null);
                CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), fc);
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('ubuntu') {
                            withCredentials([file(credentialsId: 'secretfile', variable: 'FILE')]) {
                              sh 'cat "$FILE"'
                            }
                          }
                          withCredentials([file(credentialsId: 'secretfile', variable: 'FILE')]) {
                            withDockerContainer('ubuntu') {
                              sh 'tr "a-z" "A-Z" < "$FILE"'
                            }
                          }
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("some-content", b);
                r.assertLogContains("SOME-CONTENT", b);
            }
        );
    }

    @Issue("JENKINS-27152")
    @Test
    void configFile() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                ConfigProvider configProvider = r.jenkins.getExtensionList(ConfigProvider.class).get(CustomConfig.CustomConfigProvider.class);
                String id = configProvider.getProviderId() + "myfile";
                Config config = new CustomConfig(id, "My File", "", "some-content");
                configProvider.save(config);
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withDockerContainer('ubuntu') {\n" +
                        "  wrap([$class: 'ConfigFileBuildWrapper', managedFiles: [[fileId: '" + config.id + "', variable: 'FILE']]]) {\n" +
                        "    sh 'cat \"$FILE\"'\n" +
                        "  }\n" +
                    "  }\n" +
                    "  wrap([$class: 'ConfigFileBuildWrapper', managedFiles: [[fileId: '" + config.id + "', variable: 'FILE']]]) {\n" +
                    "    withDockerContainer('ubuntu') {\n" +
                    "      sh 'tr \"a-z\" \"A-Z\" < \"$FILE\"'\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("some-content", b);
                r.assertLogContains("SOME-CONTENT", b);
            }
        );
    }

    @Issue("JENKINS-33510")
    @Test
    void cd() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker(DockerTestUtil.DockerOsMode.LINUX, new VersionNumber("17.12"));
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('ubuntu') {
                            sh 'mkdir subdir && echo somecontent > subdir/file'
                            dir('subdir') {
                              sh 'pwd; tr "a-z" "A-Z" < file'
                            }
                          }
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("SOMECONTENT", b);
            }
        );
    }

    @Disabled("TODO reproducible")
    @Issue("JENKINS-40101")
    @Test
    void wheezy() throws Throwable {
        story.then(r -> {
                DockerTestUtil.assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          withDockerContainer('debian:wheezy') {
                            sh 'sleep 30s && echo ran OK'
                          }
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("ran OK", b);
            }
        );
    }

    @Issue("JENKINS-56674")
    @Test
    void envMasking() throws Throwable {
        story.then(r -> {
            DockerTestUtil.assumeDocker();
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                """
                    node {
                      withDockerContainer('ubuntu') {
                        stepWithLauncher false
                        stepWithLauncher true
                      }
                    }""", true));
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            r.assertLogContains("hello from some step", b);
            r.assertLogContains("goodbye from ******** step", b);
            r.assertLogContains("goodbye from mystery step", b);
            r.assertLogNotContains("s3cr3t", b);
        });
    }

    public static final class StepWithLauncher extends Step {
        public final boolean masking;
        
        @DataBoundConstructor
        public StepWithLauncher(boolean masking) {
            this.masking = masking;
        }
        
        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new Execution(context, masking);
        }
        
        private static final class Execution extends SynchronousNonBlockingStepExecution<Void> {
            private final boolean masking;
            
            Execution(StepContext context, boolean masking) {
                super(context);
                this.masking = masking;
            }
            
            @Override
            protected Void run() throws Exception {
                Launcher.ProcStarter ps = getContext().get(Launcher.class).launch();
                ps.envs("SENSITIVE=s3cr3t");
                if (masking) {
                    ps.cmds(new ArgumentListBuilder("echo", "goodbye", "from").addMasked(Secret.fromString("mystery")).add("step"));
                } else {
                    ps.cmds("echo", "hello", "from", "some", "step");
                }
                ps.stdout(getContext().get(TaskListener.class));
                if (ps.join() != 0) {
                    throw new IOException("failed to run echo");
                }
                return null;
            }
        }
        
        @TestExtension("envMasking")
        public static final class DescriptorImpl extends StepDescriptor {
            
            @Override
            public String getFunctionName() {
                return "stepWithLauncher";
            }
            
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return ImmutableSet.of(Launcher.class, TaskListener.class);
            }
        }
    }

    @Issue("JENKINS-52264")
    @Test
    void removeInvalidEnvVars() throws Throwable {
        story.then(r -> {
            DockerTestUtil.assumeDocker();
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                    "  withDockerContainer('ubuntu') {\n" +
                    "    removeInvalidEnvVarsStep([]) \n" + //without envVars to overwrite
                    "    removeInvalidEnvVarsStep([\"other=only_valid_value\"]) \n" + //only valid envVar
                    "    removeInvalidEnvVarsStep([\"=\", \"other=with_empty_var\"]) \n" + //with empty variable
                    "    removeInvalidEnvVarsStep([\"PATH=ignored_value\", \"other=with_path\"]) \n" + //with PATH variable
                    "    removeInvalidEnvVarsStep([\"=\", \"PATH=ignored_value\", \"other=with_path_and_empty\"]) \n" + //both invalid variables
                    "  }\n" +
                    "}", true));
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            r.assertLogContains("working with empty environment.", b);
            r.assertLogContains("only_valid_value", b);
            r.assertLogContains("with_empty_var", b);
            r.assertLogContains("with_path", b);
            r.assertLogContains("with_path_and_empty", b);
            r.assertLogNotContains("ignored_value", b);
        });
    }

    public static final class RemoveInvalidEnvVarsStep extends Step {
        public final Collection<String> envVars;
        
        @DataBoundConstructor
        public RemoveInvalidEnvVarsStep(Collection<String> envVars) {
            this.envVars = envVars;
        }
        
        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new Execution(context, envVars);
        }
        
        private static final class Execution extends SynchronousNonBlockingStepExecution<Void> {
            private final Collection<String> envVars;
            
            Execution(StepContext context, Collection<String> envVars) {
                super(context);
                this.envVars = envVars;
            }
            
            @Override
            protected Void run() throws Exception {
                Launcher.ProcStarter ps = getContext().get(Launcher.class).launch();
                ps.envs(envVars.toArray(new String[0]));
                ArgumentListBuilder args = new ArgumentListBuilder("sh", "-c");
                if(envVars.isEmpty()) {
                    args.add("echo working with empty environment.");
                } else {
                    args.add("printenv other && printenv PATH");
                }
                final int exitCode = ps
                    .cmds(args)
                    .stdout(getContext().get(TaskListener.class))
                    .join();
                if (exitCode != 0) {
                    throw new IOException("failed to run exec command with vars: "+envVars);
                }
                return null;
            }
        }
        
        @TestExtension("removeInvalidEnvVars")
        public static final class DescriptorImpl extends StepDescriptor {
            
            @Override
            public String getFunctionName() {
                return "removeInvalidEnvVarsStep";
            }
            
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return ImmutableSet.of(Launcher.class, TaskListener.class);
            }
        }
    }

    @Issue("JENKINS-64608")
    @Test
    void runningInsideContainer() throws Throwable {
        story.then(r -> {
            DockerTestUtil.assumeDocker();
            assumeTrue(new File("/var/run/docker.sock").exists(), "have docker.sock");
            TaskListener taskListener = StreamTaskListener.fromStderr();
            Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);
            int status = launcher.launch().stdout(taskListener).
                pwd(new File(WithContainerStepTest.class.getResource("agent-with-docker/Dockerfile").toURI()).getParentFile()).
                cmds(DockerTool.getExecutable(null, null, null, null), "build", "-t", "agent-with-docker", ".").
                start().
                joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, taskListener);
            assumeTrue(status == 0, "Built agent-with-docker image");
            DumbSlave s = new DumbSlave("dockerized", "/home/jenkins/agent",
                new SimpleCommandLauncher("docker run -i --rm --init -v /var/run/docker.sock:/var/run/docker.sock agent-with-docker java -jar /usr/share/jenkins/agent.jar"));
            r.jenkins.addNode(s);
            r.waitOnline(s);
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                """
                    node('dockerized') {
                      sh 'which docker && docker version'
                      withDockerContainer('httpd:2.4.59') {
                        sh 'cp /usr/local/apache2/conf/extra/httpd-userdir.conf .; ls -la'
                      }
                      sh 'ls -la; cat *.conf'
                    }""", true));
            WorkflowRun b = r.buildAndAssertSuccess(p);
            r.assertLogContains("dockerized seems to be running inside container ", b);
        });
    }

    @Disabled("TODO currently broken in CI")
    @Issue("JENKINS-75102")
    @Test
    void windowsRunningWindowsContainerSpaceInPath() throws Throwable {
        // Launching batch scripts through cmd /c in docker exec gets tricky with special characters
        // By default, the path of the temporary Jenkins install and workspace have a space in a folder name and a prj@tmp folder
        story.then(r -> {
                DockerTestUtil.assumeWindows();
                DockerTestUtil.assumeDocker(DockerTestUtil.DockerOsMode.WINDOWS);

                // Kernel must match when running Windows containers on docker on Windows
                String releaseTag = DockerTestUtil.getWindowsImageTag();

                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                        "  withDockerContainer('mcr.microsoft.com/windows/nanoserver:" + releaseTag + "') { \n" +
                        "    bat 'echo ran OK' \n" +
                        "  }\n" +
                        "}", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("ran OK", b);
            }
        );
    }

    private static File newFile(File parent, String child) throws Exception {
        File result = new File(parent, child);
        assertTrue(result.createNewFile(), "Could not create " + result);
        return result;
    }
}
