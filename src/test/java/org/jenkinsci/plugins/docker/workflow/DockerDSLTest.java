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
import org.jenkinsci.plugins.workflow.BuildWatcher;
import org.jenkinsci.plugins.workflow.JenkinsRuleExt;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class DockerDSLTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void firstDoNoHarm() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals(Collections.<String>emptySet(), grep(b.getRootDir(), "org.jenkinsci.plugins.docker.workflow.Docker"));
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b));
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
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
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
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertLogContains("Require method GET POST OPTIONS", story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b)));
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

    @Test public void endpoints() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    // do withRegistry before withServer until JENKINS-28317 fix in Workflow 1.7:
                    "docker.withRegistry('https://docker.my.com/') {\n" +
                    "  docker.withServer('tcp://host:1234') {\n" +
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
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(p.getLastBuild()));
                story.j.assertLogContains("would be connecting to tcp://host:1234", b);
                story.j.assertLogContains("image name is docker.my.com/whatever", b);
            }
        });
    }

    @Test public void runArgs() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
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
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
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
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertLogContains("Require method GET POST OPTIONS", story.j.assertBuildStatusSuccess(JenkinsRuleExt.waitForCompletion(b)));
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

    @Test public void build() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                String ancestorImageId = "91c95931e552b11604fea91c2f537284149ec32fff0f700a4769cfd31d7696ae";
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  writeFile file: 'stuff1', text: 'hello'\n" +
                    "  writeFile file: 'stuff2', text: 'world'\n" +
                    "  writeFile file: 'stuff3', text: env.BUILD_NUMBER\n" +
                    "  sh 'touch -t 201501010000 stuff*'\n" + // image hash includes timestamps!
                    "  def helloworld = docker.image('hello-world');\n" +
                    "  helloworld.pull();\n" +
                    "  writeFile file: 'Dockerfile', text: '# This is a test.\\n\\nFROM hello-world@" + ancestorImageId + "\\nCOPY stuff1 /\\nCOPY stuff2 /\\nCOPY stuff3 /\\n'\n" +
                    "  def built = docker.build 'hello-world-stuff'\n" +
                    "  echo \"built ${built.id}\"\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                String descendantImageId1 = "c04cd8a9a37440562c655c8076dc47a41ae7855096e73c6e8aa5f01f2ed52b85";
                story.j.assertLogContains("built hello-world-stuff", b);
                story.j.assertLogContains(descendantImageId1.substring(0, 12), b);
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
                String descendantImageId2 = "0703ebc9f6a713f56191ce4db96338f4572de53479bc32efd60717f789d91089";
                story.j.assertLogContains("built hello-world-stuff", b);
                story.j.assertLogContains(descendantImageId2.substring(0, 12), b);
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

    @Test public void withTool() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                assumeDocker();
                assumeTrue(new File("/usr/bin/docker").canExecute()); // TODO generalize to find docker in $PATH
                story.j.jenkins.getDescriptorByType(DockerTool.DescriptorImpl.class).setInstallations(new DockerTool("default", "/usr", Collections.<ToolProperty<?>>emptyList()));
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
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
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "     try { sh 'docker rmi busybox:test' } catch (Exception e) {}\n" +
                    "     def busybox = docker.image('busybox');\n" +
                    "     busybox.pull();\n" +
                    "     // tag it\n" +
                    "     busybox.tag('test', false);\n" +
                    "     // tag it again - should fail because the tag already exists and the --force flag is false\n" +
                    "     try {\n" +
                    "         busybox.tag('test', false);\n" +
                    "     } catch (Exception e) {\n" +
                    "        sh \"echo 'TAG without force failed as expected'\"\n" +
                    "     }\n" +
                    "     // tag it again - should work because the --force flag is true\n" +
                    "     busybox.tag('test', true);\n" +
                    "     sh \"echo 'TAG with force succeeded as expected'\"\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("TAG without force failed as expected", b);
                story.j.assertLogContains("TAG with force succeeded as expected", b);
            }
        });
    }
}
