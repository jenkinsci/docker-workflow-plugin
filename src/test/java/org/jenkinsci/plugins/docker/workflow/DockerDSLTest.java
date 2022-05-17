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
import hudson.tools.ToolProperty;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.LoggerRule;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInRelativeOrder.containsInRelativeOrder;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringRegularExpression.matchesRegex;
import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeDocker;
import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeNotWindows;
import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.randomTcpPort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class DockerDSLTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule story = new JenkinsSessionRule();
    @Rule public LoggerRule logger = new LoggerRule();

    private boolean unsafeParameterExpansion;

    public DockerDSLTest(final boolean unsafeParameterExpansion) {
        this.unsafeParameterExpansion = unsafeParameterExpansion;
    }

    @Parameterized.Parameters(name = "upx:{0}")
    public static Object[] data() {
        return new Object[]{false, true};
    }

    @Before
    public void setUp() {
        DockerDSL.UNSAFE_PARAMETER_EXPANDING = unsafeParameterExpansion;
        logger.record(DockerDSL.class.getName(), Level.FINE).capture(1000);
    }

    @After
    public void tearDown() {
        DockerDSL.UNSAFE_PARAMETER_EXPANDING = false;
    }

    @Test public void firstDoNoHarm() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);

        });
        story.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), "org.jenkinsci.plugins.docker.workflow.Docker"));
                SemaphoreStep.success("wait/1", null);
                j.assertBuildStatusSuccess(j.waitForCompletion(b));

        });
    }
    // TODO copied from BindingStepTest, should be made into a utility in Jenkins test harness perhaps (or JenkinsRuleExt as a first step)
    private static Set<String> grep(File dir, String text) throws IOException {
        Set<String> matches = new TreeSet<String>();
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
            } else if (kid.isFile() && FileUtils.readFileToString(kid).contains(text)) {
                matches.add(qualifiedName);
            }
        }
    }


    @Test public void inside() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "def r = docker.image('httpd:2.4.12').inside {\n" +
                    "  semaphore 'wait'\n" +
                    "  sh 'cat /usr/local/apache2/conf/extra/httpd-userdir.conf'\n" +
                    "  42\n" +
                    "}; echo \"the answer is ${r}\"", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);

        });
        story.then(j -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = j.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                j.assertLogContains("Require method GET POST OPTIONS", j.assertBuildStatusSuccess(j.waitForCompletion(b)));
                j.assertLogContains("the answer is 42", b);
                Matcher<String> matcher = containsString("WARNING! Unsafe parameter expansion is enabled.");
                if (unsafeParameterExpansion) {
                    assertThat(logger.getMessages(), containsInRelativeOrder(matcher));
                } else {
                    assertThat(logger.getMessages(), not(containsInRelativeOrder(matcher)));
                }
        });
    }

    @Issue("JENKINS-37987")
    @Test public void entrypoint() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "docker.image('maven:3.5.3-jdk-8').inside {\n" +
                        "  sh 'mvn -version'\n" +
                        "}", true));

                j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        });
    }

    @Test public void endpoints() throws Throwable {
        final int port = randomTcpPort();
        story.then(j -> {
                assumeNotWindows();

                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "docker.withServer('tcp://host:"+port+"') {\n" +
                    "  docker.withRegistry('https://docker.my.com/') {\n" +
                    "    semaphore 'wait'\n" +
                    "    sh 'echo would be connecting to $DOCKER_HOST'\n" +
                    "    echo \"image name is ${docker.image('whatever').imageName()}\"\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
        });
        story.then(j -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = j.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = j.assertBuildStatusSuccess(j.waitForCompletion(p.getLastBuild()));
                j.assertLogContains("would be connecting to tcp://host:" + port, b);
                j.assertLogContains("image name is docker.my.com/whatever", b);
        });
    }

    @Test public void runArgs() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  def img = docker.image('httpd:2.4.12')\n" +
                    "  img.run().stop()\n" +
                    "  img.run('--memory-swap=-1').stop()\n" +
                    "  img.withRun {}\n" +
                    "  img.withRun('--memory-swap=-1') {}\n" +
                    "  img.inside {}\n" +
                    "  img.inside('--memory-swap=-1') {}\n" +
                "}", true));
                j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                final Matcher<String> stringMatcher = containsString("WARNING! Unsafe parameter expansion is enabled.");
                if (unsafeParameterExpansion) {
                    assertThat(logger.getMessages(), containsInRelativeOrder(stringMatcher));
                } else {
                    assertThat(logger.getMessages(), not(containsInRelativeOrder(stringMatcher)));
                }
        });
    }

    @Test public void withRun() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "def r = docker.image('httpd:2.4.12').withRun {c ->\n" +
                    "  semaphore 'wait'\n" +
                    "  sh \"docker exec ${c.id} cat /usr/local/apache2/conf/extra/httpd-userdir.conf\"\n" +
                    "  42\n" +
                    "}; echo \"the answer is ${r}\"", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
        });
        story.then(j -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = j.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                j.assertLogContains("Require method GET POST OPTIONS", j.assertBuildStatusSuccess(j.waitForCompletion(b)));
                j.assertLogContains("the answer is 42", b);
        });
    }

    @Test public void withRunCommand() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        " docker.image('maven:3.3.9-jdk-8').withRun(\"--entrypoint mvn\", \"-version\") {c ->\n" +
                                "  sh \"docker logs ${c.id}\"" +
                                "}", true));
                j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        });
    }

    @Test public void build() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  writeFile file: 'stuff1', text: 'hello'\n" +
                    "  writeFile file: 'stuff2', text: 'world'\n" +
                    "  writeFile file: 'stuff3', text: env.BUILD_NUMBER\n" +
                    "  writeFile file: 'Dockerfile', text: '# This is a test.\\n\\nFROM hello-world\\nCOPY stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'\n" +
                    "  def built = docker.build 'hello-world-stuff'\n" +
                    "  echo \"built ${built.id}\"\n" +
                    "}", true));
                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);

                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff", ".Id");
                j.assertLogContains("built hello-world-stuff", b);
                j.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);

                b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                String descendantImageId2 = client.inspect(new EnvVars(), "hello-world-stuff", ".Id");
                j.assertLogContains("built hello-world-stuff", b);
                j.assertLogContains(descendantImageId2.replaceFirst("^sha256:", "").substring(0, 12), b);
                final Matcher<String> stringMatcher = containsString("WARNING! Unsafe parameter expansion is enabled.");
                if (unsafeParameterExpansion) {
                    assertThat(logger.getMessages(), containsInRelativeOrder(stringMatcher));
                } else {
                    assertThat(logger.getMessages(), not(containsInRelativeOrder(stringMatcher)));
                }
        });
    }

    @Test public void buildWithMultiStage() throws Throwable {
        story.then(j -> {
                assumeDocker(new VersionNumber("17.05"));
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "  sh 'mkdir -p child'\n" +
                                "  writeFile file: 'child/stuff1', text: 'hello'\n" +
                                "  writeFile file: 'child/stuff2', text: 'world'\n" +
                                "  writeFile file: 'child/stuff3', text: env.BUILD_NUMBER\n" +
                                "  writeFile file: 'child/Dockerfile.other', " +
                                     "text: '# This is a test.\\n" +
                                            "\\n" +
                                            "FROM hello-world AS one\\n" +
                                            "ARG stuff4=4\\n" +
                                            "ARG stuff5=5\\n" +
                                            "COPY stuff1 /\\n" +
                                            "FROM scratch\\n" +
                                            "COPY --from=one /stuff1 /\\n" +
                                            "COPY stuff2 /\\nCOPY stuff3 /\\n'\n" +
                                "  def built = docker.build 'hello-world-stuff-arguments', '-f child/Dockerfile.other --build-arg stuff4=build4 --build-arg stuff5=build5 child'\n" +
                                "  echo \"built ${built.id}\"\n" +
                                "}", true));

// Note the absence '--pull' in the above docker build ags as compared to other tests.
// This is due to a Docker bug: https://github.com/docker/for-mac/issues/1751
// It can be re-added when that is fixed

                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff-arguments", ".Id");
                j.assertLogContains("built hello-world-stuff-arguments", b);
                j.assertLogNotContains(" --no-cache ", b);
                j.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                j.assertLogContains(" --build-arg stuff4=build4 ", b);
                j.assertLogContains(" --build-arg stuff5=build5 ", b);
        });
    }

    @Test public void buildArguments() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                        "  sh 'mkdir -p child'\n" +
                        "  writeFile file: 'child/stuff1', text: 'hello'\n" +
                        "  writeFile file: 'child/stuff2', text: 'world'\n" +
                        "  writeFile file: 'child/stuff3', text: env.BUILD_NUMBER\n" +
                        "  writeFile file: 'child/Dockerfile.other', text: '# This is a test.\\n\\nFROM hello-world\\nARG stuff4=4\\nARG stuff5=5\\nCOPY stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'\n" +
                        "  def built = docker.build 'hello-world-stuff-arguments', '-f child/Dockerfile.other --pull --build-arg stuff4=build4 --build-arg stuff5=build5 child'\n" +
                        "  echo \"built ${built.id}\"\n" +
                        "}", true));

                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff-arguments", ".Id");
                j.assertLogContains("built hello-world-stuff-arguments", b);
                j.assertLogContains(" --pull ", b);
                j.assertLogNotContains(" --no-cache ", b);
                j.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                j.assertLogContains(" --build-arg stuff4=build4 ", b);
                j.assertLogContains(" --build-arg stuff5=build5 ", b);
                final Matcher<String> stringMatcher = containsString("WARNING! Unsafe parameter expansion is enabled.");
                if (unsafeParameterExpansion) {
                    assertThat(logger.getMessages(), containsInRelativeOrder(stringMatcher));
                } else {
                    assertThat(logger.getMessages(), not(containsInRelativeOrder(stringMatcher)));
                }
        });
    }

    @Test public void withTool() throws Throwable {
        story.then(j -> {
                assumeDocker();
                assumeTrue(new File("/usr/bin/docker").canExecute()); // TODO generalize to find docker in $PATH
                j.jenkins.getDescriptorByType(DockerTool.DescriptorImpl.class).setInstallations(new DockerTool("default", "/usr", Collections.<ToolProperty<?>>emptyList()));
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                "docker.withTool('default') {\n" +
                "  docker.image('httpd:2.4.12').withRun {}\n" +
                "  sh 'echo PATH=$PATH'\n" +
                "}", true));
                j.assertLogContains("PATH=/usr/bin:", j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        });
    }

    @Test public void tag() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "     try { sh 'docker rmi busybox:test' } catch (Exception e) {}\n" +
                    "     def busybox = docker.image('busybox');\n" +
                    "     busybox.pull();\n" +
                    "     // tag it\n" +
                    "     busybox.tag('test', /* ignored but to test that the argument is accepted */false);\n" +
                    "     // retag it\n" +
                    "     busybox.tag('test');\n" +
                    "}", true));
                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            final Matcher<String> stringMatcher = containsString("WARNING! Unsafe parameter expansion is enabled.");
            if (unsafeParameterExpansion) {
                    assertThat(logger.getMessages(), containsInRelativeOrder(stringMatcher));
                } else {
                    assertThat(logger.getMessages(), not(containsInRelativeOrder(stringMatcher)));
                }
        });
    }

    @Test @Issue("SECURITY-1878") public void tagInjection() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                        "     try { sh 'docker rmi busybox:test' } catch (Exception e) {}\n" +
                        "     def busybox = docker.image('busybox');\n" +
                        "     busybox.pull();\n" +
                        "     // tag it\n" +
                        "     busybox.tag(\"test\\necho haxored\", false);\n" +
                        "}", true));
                WorkflowRun b = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
                final String log = JenkinsRule.getLog(b);
                assertThat(log, not(matchesRegex("^haxored")));
                assertThat(log, containsString("ERROR: Tag must follow the pattern"));
                final Matcher<String> stringMatcher = containsString("WARNING! Unsafe parameter expansion is enabled.");
                if (unsafeParameterExpansion) {
                    assertThat(logger.getMessages(), containsInRelativeOrder(stringMatcher));
                } else {
                    assertThat(logger.getMessages(), not(containsInRelativeOrder(stringMatcher)));
                }
        });
    }

    @Test @Issue("JENKINS-57366") public void imageInjectionOr() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                        "     try { sh 'docker rmi busybox:test' } catch (Exception e) {}\n" +
                        "     def busybox = docker.image('busybox|echo haxored');\n" +
                        "     busybox.pull();\n" +
                        "}", true));
                WorkflowRun b = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
                final String log = JenkinsRule.getLog(b);
                assertThat(log, not(matchesRegex("^haxored")));
                assertThat(log, containsString("ERROR: Name must follow the pattern"));
        });
    }

    @Test public void run() throws Throwable {
        story.then(j -> {
                assumeDocker();
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "     def busybox = docker.image('busybox');\n" +
                                "     busybox.run('--tty', 'echo \"Hello\"').stop();\n" +
                                "}", true));
                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                j.assertLogContains("Hello", b);
        });
    }

    @Test public void port() throws Throwable {
        final int port = randomTcpPort();
        story.then(j -> {
            assumeDocker();
            WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
            p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                    "  def img = docker.image('httpd:2.4.12')\n" +
                    "  def port = img.withRun('-p "+port+":80') { c -> c.port(80) }\n" +
                    "  echo \"container running on ${port}\"" +
                "}", true));
            WorkflowRun r = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            j.assertLogContains("container running on 0.0.0.0:" + port, r);
        });
    }
}
