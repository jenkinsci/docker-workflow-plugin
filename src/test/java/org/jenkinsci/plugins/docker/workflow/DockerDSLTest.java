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

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Result;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringRegularExpression.matchesRegex;
import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeDocker;
import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeNotWindows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DockerDSLTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();

    @Test
    void firstDoNoHarm() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        );
        story.then(r -> {
                WorkflowJob p = r.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), "org.jenkinsci.plugins.docker.workflow.Docker"));
                SemaphoreStep.success("wait/1", null);
                r.assertBuildStatusSuccess(r.waitForCompletion(b));
            }
        );
    }
    // TODO copied from BindingStepTest, should be made into a utility in Jenkins test harness perhaps (or JenkinsRuleExt as a first step)
    private static Set<String> grep(File dir, String text) throws IOException {
        Set<String> matches = new TreeSet<>();
        grep(dir, text, "", matches);
        return matches;
    }
    private static void grep(File dir, String text, String prefix, Set<String> matches) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            String qualifiedName = prefix + kid.getName();
            if (kid.isDirectory()) {
                grep(kid, text, qualifiedName + "/", matches);
            } else {
                try {
                    if (FileUtils.readFileToString(kid, StandardCharsets.UTF_8).contains(text)) {
                        matches.add(qualifiedName);
                    }
                } catch (FileNotFoundException | NoSuchFileException x) {
                    // ignore, e.g. tmp file
                }
            }
        }
    }


    @Test
    void inside() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        def r = docker.image('httpd:2.4.62').inside {
                          semaphore 'wait'
                          sh 'cat /usr/local/apache2/conf/extra/httpd-userdir.conf'
                          42
                        }; echo "the answer is ${r}\"""", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        );
        story.then(r -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = r.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                r.assertLogContains("Require method GET POST OPTIONS", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
                r.assertLogContains("the answer is 42", b);
            }
        );
    }

    @Issue("JENKINS-37987")
    @Test
    void entrypoint() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        docker.image('maven:3.9.9-eclipse-temurin-17').inside {
                          sh 'mvn -version'
                        }""", true));

                r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        );
    }

    @Test
    void endpoints() throws Throwable {
        story.then(r -> {
                assumeNotWindows();

                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        docker.withServer('tcp://host:1234') {
                          docker.withRegistry('https://docker.my.com/') {
                            semaphore 'wait'
                            sh 'echo would be connecting to $DOCKER_HOST'
                            echo "image name is ${docker.image('whatever').imageName()}"
                          }
                        }""", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        );
        story.then(r -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = r.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = r.assertBuildStatusSuccess(r.waitForCompletion(p.getLastBuild()));
                r.assertLogContains("would be connecting to tcp://host:1234", b);
                r.assertLogContains("image name is docker.my.com/whatever", b);
            }
        );
    }

    @Test
    void runArgs() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          def img = docker.image('httpd:2.4.62')
                          img.run().stop()
                          img.run('--memory-swap=-1').stop()
                          img.withRun {}
                          img.withRun('--memory-swap=-1') {}
                          img.inside {}
                          img.inside('--memory-swap=-1') {}
                        }""", true));
                r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        );
    }

    @Test
    void withRun() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        def r = docker.image('httpd:2.4.62').withRun {c ->
                          semaphore 'wait'
                          sh "docker exec ${c.id} cat /usr/local/apache2/conf/extra/httpd-userdir.conf"
                          42
                        }; echo "the answer is ${r}\"""", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        );
        story.then(r -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = r.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                r.assertLogContains("Require method GET POST OPTIONS", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
                r.assertLogContains("the answer is 42", b);
            }
        );
    }

    @Test
    void withRunCommand() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        " docker.image('maven:3.9.9-eclipse-temurin-17').withRun(\"--entrypoint mvn\", \"-version\") {c ->\n" +
                                "  sh \"docker logs ${c.id}\"" +
                                "}", true));
                r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        );
    }

    @Test
    void build() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          writeFile file: 'stuff1', text: 'hello'
                          writeFile file: 'stuff2', text: 'world'
                          writeFile file: 'stuff3', text: env.BUILD_NUMBER
                          writeFile file: 'Dockerfile', text: '# This is a test.\\n\\nFROM hello-world\\nCOPY stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'
                          def built = docker.build 'hello-world-stuff'
                          echo "built ${built.id}"
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);

                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff", ".Id");
                r.assertLogContains("built hello-world-stuff", b);
                r.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);

                b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                String descendantImageId2 = client.inspect(new EnvVars(), "hello-world-stuff", ".Id");
                r.assertLogContains("built hello-world-stuff", b);
                r.assertLogContains(descendantImageId2.replaceFirst("^sha256:", "").substring(0, 12), b);
            }
        );
    }

    @Test
    void buildWithMultiStage() throws Throwable {
        story.then(r -> {
                assumeDocker(DockerTestUtil.DockerOsMode.LINUX, new VersionNumber("17.05"));
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          sh 'mkdir -p child'
                          writeFile file: 'child/stuff1', text: 'hello'
                          writeFile file: 'child/stuff2', text: 'world'
                          writeFile file: 'child/stuff3', text: env.BUILD_NUMBER
                          writeFile file: 'child/Dockerfile.other', \
                        text: '# This is a test.\\n\\nFROM hello-world AS one\\nARG stuff4=4\\nARG stuff5=5\\nCOPY stuff1 /\\nFROM scratch\\nCOPY --from=one /stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'
                          def built = docker.build 'hello-world-stuff-arguments', '-f child/Dockerfile.other --build-arg stuff4=build4 --build-arg stuff5=build5 child'
                          echo "built ${built.id}"
                        }""", true));

// Note the absence '--pull' in the above docker build ags as compared to other tests.
// This is due to a Docker bug: https://github.com/docker/for-mac/issues/1751
// It can be re-added when that is fixed

                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff-arguments", ".Id");
                r.assertLogContains("built hello-world-stuff-arguments", b);
                r.assertLogNotContains(" --no-cache ", b);
                r.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                r.assertLogContains(" --build-arg stuff4=build4 ", b);
                r.assertLogContains(" --build-arg stuff5=build5 ", b);
            }
        );
    }

    @Test
    void buildArguments() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          sh 'mkdir -p child'
                          writeFile file: 'child/stuff1', text: 'hello'
                          writeFile file: 'child/stuff2', text: 'world'
                          writeFile file: 'child/stuff3', text: env.BUILD_NUMBER
                          writeFile file: 'child/Dockerfile.other', text: '# This is a test.\\n\\nFROM hello-world\\nARG stuff4=4\\nARG stuff5=5\\nCOPY stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'
                          def built = docker.build 'hello-world-stuff-arguments', '-f child/Dockerfile.other --pull --build-arg stuff4=build4 --build-arg stuff5=build5 child'
                          echo "built ${built.id}"
                        }""", true));

                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff-arguments", ".Id");
                r.assertLogContains("built hello-world-stuff-arguments", b);
                r.assertLogContains(" --pull ", b);
                r.assertLogNotContains(" --no-cache ", b);
                r.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                r.assertLogContains(" --build-arg stuff4=build4 ", b);
                r.assertLogContains(" --build-arg stuff5=build5 ", b);
            }
        );
    }

    @Test
    void withTool() throws Throwable {
        story.then(r -> {
                assumeDocker();
                assumeTrue(new File("/usr/bin/docker").canExecute()); // TODO generalize to find docker in $PATH
                r.jenkins.getDescriptorByType(DockerTool.DescriptorImpl.class).setInstallations(new DockerTool("default", "/usr", Collections.emptyList()));
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        docker.withTool('default') {
                          docker.image('httpd:2.4.62').withRun {}
                          sh 'echo PATH=$PATH'
                        }""", true));
                r.assertLogContains("PATH=/usr/bin:", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
            }
        );
    }

    @Test
    void tag() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                             try { sh 'docker rmi busybox:test' } catch (Exception e) {}
                             def busybox = docker.image('busybox');
                             busybox.pull();
                             // tag it
                             busybox.tag('test', /* ignored but to test that the argument is accepted */false);
                             // retag it
                             busybox.tag('test');
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        );
    }

    @Test
    @Issue("SECURITY-1878")
    void tagInjection() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                             try { sh 'docker rmi busybox:test' } catch (Exception e) {}
                             def busybox = docker.image('busybox');
                             busybox.pull();
                             // tag it
                             busybox.tag("test\\necho haxored", false);
                        }""", true));
                WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
                final String log = JenkinsRule.getLog(b);
                assertThat(log, not(matchesRegex("^haxored")));
                assertThat(log, containsString("ERROR: Tag must follow the pattern"));
            }
        );
    }

    @Test
    @Issue("JENKINS-57366")
    void imageInjectionOr() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                             try { sh 'docker rmi busybox:test' } catch (Exception e) {}
                             def busybox = docker.image('busybox|echo haxored');
                             busybox.pull();
                        }""", true));
                WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
                final String log = JenkinsRule.getLog(b);
                assertThat(log, not(matchesRegex("^haxored")));
                assertThat(log, containsString("ERROR: Name must follow the pattern"));
            }
        );
    }

    @Test
    void run() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                             def busybox = docker.image('busybox');
                             busybox.run('--tty', 'echo "Hello"').stop();
                        }""", true));
                WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("Hello", b);
            }
        );
    }

    @Test
    void port() throws Throwable {
        story.then(r -> {
                assumeDocker();
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    """
                        node {
                          def img = docker.image('httpd:2.4.62')
                          def port = img.withRun('-p 12345:80') { c -> c.port(80) }
                          echo "container running on ${port}"\
                        }""", true));
                WorkflowRun run = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
                r.assertLogContains("container running on 0.0.0.0:12345", run);
            }
        );
    }
}
