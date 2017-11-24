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

import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeDocker;

import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Fingerprint;
import hudson.tools.ToolProperty;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.docker.commons.DockerImageExtractor;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerAncestorFingerprintFacet;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerDescendantFingerprintFacet;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerRunFingerprintFacet;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;

import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeNotWindows;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class DockerDSLTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void firstDoNoHarm() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("prj", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), "org.jenkinsci.plugins.docker.workflow.Docker"));
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
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


    @Test public void inside() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "def r = docker.image('httpd:2.4.12').inside {\n" +
                    "  semaphore 'wait'\n" +
                    "  sh 'cat /usr/local/apache2/conf/extra/httpd-userdir.conf'\n" +
                    "  42\n" +
                    "}; echo \"the answer is ${r}\"", true));
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
                story.j.assertLogContains("the answer is 42", b);
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String httpdIID = client.inspect(new EnvVars(), "httpd:2.4.12", ".Id");
                Fingerprint f = DockerFingerprints.of(httpdIID);
                assertNotNull(f);
                DockerRunFingerprintFacet facet = f.getFacet(DockerRunFingerprintFacet.class);
                assertNotNull(facet);
                assertEquals(1, facet.records.size());
                assertNotNull(facet.records.get(0).getContainerName());
                assertEquals(Fingerprint.RangeSet.fromString("1", false), facet.getRangeSet(p));
                assertEquals(Collections.singleton("httpd"), DockerImageExtractor.getDockerImagesUsedByJobFromAll(p));
            }
        });
    }

    @Issue("JENKINS-37987")
    @Test public void entrypoint() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "docker.image('maven:latest').inside {\n" +
                               "  sh 'mvn -version'\n" +
                               "}", true));

                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        });
    }

    @Test public void endpoints() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeNotWindows();

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "docker.withServer('tcp://host:1234') {\n" +
                    "  docker.withRegistry('https://docker.my.com/') {\n" +
                    "    semaphore 'wait'\n" +
                    "    sh 'echo would be connecting to $DOCKER_HOST'\n" +
                    "    echo \"image name is ${docker.image('whatever').imageName()}\"\n" +
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
                WorkflowRun b = story.j.assertBuildStatusSuccess(story.j.waitForCompletion(p.getLastBuild()));
                story.j.assertLogContains("would be connecting to tcp://host:1234", b);
                story.j.assertLogContains("image name is docker.my.com/whatever", b);
            }
        });
    }

    @Test public void runArgs() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
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
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        });
    }

    @Test public void withRun() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "def r = docker.image('httpd:2.4.12').withRun {c ->\n" +
                    "  semaphore 'wait'\n" +
                    "  sh \"docker exec ${c.id} cat /usr/local/apache2/conf/extra/httpd-userdir.conf\"\n" +
                    "  42\n" +
                    "}; echo \"the answer is ${r}\"", true));
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
                story.j.assertLogContains("the answer is 42", b);
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String httpdIID = client.inspect(new EnvVars(), "httpd:2.4.12", ".Id");
                Fingerprint f = DockerFingerprints.of(httpdIID);
                assertNotNull(f);
                DockerRunFingerprintFacet facet = f.getFacet(DockerRunFingerprintFacet.class);
                assertNotNull(facet);
                assertEquals(1, facet.records.size());
                assertNotNull(facet.records.get(0).getContainerName());
                assertEquals(Fingerprint.RangeSet.fromString("1", false), facet.getRangeSet(p));
                assertEquals(Collections.singleton("httpd"), DockerImageExtractor.getDockerImagesUsedByJobFromAll(p));
            }
        });
    }

    @Test public void withRunCommand() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        " docker.image('maven:3.3.9-jdk-8').withRun(\"--entrypoint mvn\", \"-version\") {c ->\n" +
                                "  sh \"docker logs ${c.id}\"" +
                                "}", true));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String mavenIID = client.inspect(new EnvVars(), "maven:3.3.9-jdk-8", ".Id");
                Fingerprint f = DockerFingerprints.of(mavenIID);
                assertNotNull(f);
                DockerRunFingerprintFacet facet = f.getFacet(DockerRunFingerprintFacet.class);
                assertNotNull(facet);
            }
        });
    }

    @Test public void build() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  writeFile file: 'stuff1', text: 'hello'\n" +
                    "  writeFile file: 'stuff2', text: 'world'\n" +
                    "  writeFile file: 'stuff3', text: env.BUILD_NUMBER\n" +
                    "  writeFile file: 'Dockerfile', text: '# This is a test.\\n\\nFROM hello-world\\nCOPY stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'\n" +
                    "  def built = docker.build 'hello-world-stuff'\n" +
                    "  echo \"built ${built.id}\"\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String ancestorImageId = client.inspect(new EnvVars(), "hello-world", ".Id");
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff", ".Id");
                story.j.assertLogContains("built hello-world-stuff", b);
                story.j.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                Fingerprint f = DockerFingerprints.of(ancestorImageId);
                assertNotNull(f);
                DockerDescendantFingerprintFacet descendantFacet = f.getFacet(DockerDescendantFingerprintFacet.class);
                assertNotNull(descendantFacet);
                assertEquals(Fingerprint.RangeSet.fromString("1", false), descendantFacet.getRangeSet(p));
                assertEquals(ancestorImageId, descendantFacet.getImageId());
                assertEquals(Collections.singleton(descendantImageId1), descendantFacet.getDescendantImageIds());
                f = DockerFingerprints.of(descendantImageId1);
                assertNotNull(f);
                DockerAncestorFingerprintFacet ancestorFacet = f.getFacet(DockerAncestorFingerprintFacet.class);
                assertNotNull(ancestorFacet);
                assertEquals(Fingerprint.RangeSet.fromString("1", false), ancestorFacet.getRangeSet(p));
                assertEquals(Collections.singleton(ancestorImageId), ancestorFacet.getAncestorImageIds());
                assertEquals(descendantImageId1, ancestorFacet.getImageId());
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                String descendantImageId2 = client.inspect(new EnvVars(), "hello-world-stuff", ".Id");
                story.j.assertLogContains("built hello-world-stuff", b);
                story.j.assertLogContains(descendantImageId2.replaceFirst("^sha256:", "").substring(0, 12), b);
                f = DockerFingerprints.of(ancestorImageId);
                assertNotNull(f);
                descendantFacet = f.getFacet(DockerDescendantFingerprintFacet.class);
                assertNotNull(descendantFacet);
                assertEquals(Fingerprint.RangeSet.fromString("1-2", false), descendantFacet.getRangeSet(p));
                assertEquals(ancestorImageId, descendantFacet.getImageId());
                assertEquals(new HashSet<String>(Arrays.asList(descendantImageId1, descendantImageId2)), descendantFacet.getDescendantImageIds());
                f = DockerFingerprints.of(descendantImageId1);
                assertNotNull(f);
                ancestorFacet = f.getFacet(DockerAncestorFingerprintFacet.class);
                assertNotNull(ancestorFacet);
                assertEquals(Fingerprint.RangeSet.fromString("1", false), ancestorFacet.getRangeSet(p));
                assertEquals(Collections.singleton(ancestorImageId), ancestorFacet.getAncestorImageIds());
                assertEquals(descendantImageId1, ancestorFacet.getImageId());
                f = DockerFingerprints.of(descendantImageId2);
                assertNotNull(f);
                ancestorFacet = f.getFacet(DockerAncestorFingerprintFacet.class);
                assertNotNull(ancestorFacet);
                assertEquals(Fingerprint.RangeSet.fromString("2", false), ancestorFacet.getRangeSet(p));
                assertEquals(Collections.singleton(ancestorImageId), ancestorFacet.getAncestorImageIds());
                assertEquals(descendantImageId2, ancestorFacet.getImageId());
                assertEquals(Collections.singleton("hello-world"), DockerImageExtractor.getDockerImagesUsedByJobFromAll(p));
            }
        });
    }

    @Test public void buildWithMultiStage() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker(new VersionNumber("17.05"));
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
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

                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff-arguments", ".Id");
                story.j.assertLogContains("built hello-world-stuff-arguments", b);
                story.j.assertLogNotContains(" --no-cache ", b);
                story.j.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                story.j.assertLogContains(" --build-arg stuff4=build4 ", b);
                story.j.assertLogContains(" --build-arg stuff5=build5 ", b);
            }
        });
    }

    @Test public void buildArguments() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
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

                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new Launcher.LocalLauncher(StreamTaskListener.NULL), null, null);
                String descendantImageId1 = client.inspect(new EnvVars(), "hello-world-stuff-arguments", ".Id");
                story.j.assertLogContains("built hello-world-stuff-arguments", b);
                story.j.assertLogContains(" --pull ", b);
                story.j.assertLogNotContains(" --no-cache ", b);
                story.j.assertLogContains(descendantImageId1.replaceFirst("^sha256:", "").substring(0, 12), b);
                story.j.assertLogContains(" --build-arg stuff4=build4 ", b);
                story.j.assertLogContains(" --build-arg stuff5=build5 ", b);
            }
        });
    }

    @Test public void withTool() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                assumeTrue(new File("/usr/bin/docker").canExecute()); // TODO generalize to find docker in $PATH
                story.j.jenkins.getDescriptorByType(DockerTool.DescriptorImpl.class).setInstallations(new DockerTool("default", "/usr", Collections.<ToolProperty<?>>emptyList()));
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                "docker.withTool('default') {\n" +
                "  docker.image('httpd:2.4.12').withRun {}\n" +
                "  sh 'echo PATH=$PATH'\n" +
                "}", true));
                story.j.assertLogContains("PATH=/usr/bin:", story.j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
            }
        });
    }

    @Test public void tag() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
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
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        });
    }

    @Test public void run() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "     def busybox = docker.image('busybox');\n" +
                                "     busybox.run('--tty', 'echo \"Hello\"').stop();\n" +
                                "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("Hello", b);
            }
        });
    }

    @Test public void port(){
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "prj");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                        "  def img = docker.image('httpd:2.4.12')\n" +
                        "  def port = img.withRun('-p 12345:80') { c -> c.port(80) }\n" +
                        "  echo \"container running on ${port}\"" +
                    "}", true));
                WorkflowRun r = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("container running on 0.0.0.0:12345", r);
            }
        });
    }
}
