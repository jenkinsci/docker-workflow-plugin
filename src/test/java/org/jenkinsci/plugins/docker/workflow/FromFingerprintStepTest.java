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
import hudson.Launcher.LocalLauncher;
import hudson.model.Fingerprint;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerDescendantFingerprintFacet;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.jenkinsci.plugins.docker.workflow.DockerTestUtil.assumeDocker;
import static org.junit.Assert.assertNotNull;

public class FromFingerprintStepTest {
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    private static final String HELLO_WORLD_IMAGE = "hello-world";
    private static final String BUSYBOX_IMAGE = "busybox";

    /*
        @Test public void buildWithFROMArgs() throws Exception {
        assertBuild("prj-simple",
            script("--build-arg IMAGE_TO_UPDATE=hello-world:latest"));

        assertBuild("prj-singlequotes-in-build-arg---aroundValue",
            script("--build-arg IMAGE_TO_UPDATE=\\'hello-world:latest\\'"));

        assertBuild("prj-dobulequotes-in-build-arg---aroundValue",
            script("--build-arg IMAGE_TO_UPDATE=\"hello-world:latest\""));

        assertBuild("prj-singlequotes-in-build-arg---aroundAllArgs",
            script("--build-arg \\'IMAGE_TO_UPDATE=hello-world:latest\\'"));

        assertBuild("prj-doublequotes-in-build-arg---aroundAllArgs",
            script("--build-arg \"IMAGE_TO_UPDATE=hello-world:latest\""));

        // and again with equals after --build-arg
        assertBuild("prj-equals-simple",
            script("--build-arg=IMAGE_TO_UPDATE=hello-world:latest"));

        assertBuild("prj-equals-singlequotes-in-build-arg---aroundValue",
            script("--build-arg=IMAGE_TO_UPDATE=\\'hello-world:latest\\'"));

        assertBuild("prj-equals-dobulequotes-in-build-arg---aroundValue",
            script("--build-arg=IMAGE_TO_UPDATE=\"hello-world:latest\""));

        assertBuild("prj-equals-singlequotes-in-build-arg---aroundAllArgs",
            script("--build-arg=\\'IMAGE_TO_UPDATE=hello-world:latest\\'"));

        assertBuild("prj-equals-doublequotes-in-build-arg---aroundAllArgs",
            script("--build-arg=\"IMAGE_TO_UPDATE=hello-world:latest\""));

        // quotes around all of the --build-arg= stuff
        assertBuild("prj-equals-singlequotes-in-build-arg---aroundAll",
            script("\\'--build-arg=IMAGE_TO_UPDATE=hello-world:latest\\'"));

        assertBuild("prj-equals-doublequotes-in-build-arg---aroundAll",
            script("\"--build-arg=IMAGE_TO_UPDATE=hello-world:latest\""));
    }
     */
    /**
     * Test quotation marks in --build-arg parameters
     */
    @Test public void buildWithFROMArgs() throws Exception {
        String dockerfile = "" +
            "ARG IMAGE_TO_UPDATE\\n" +
            "FROM ${IMAGE_TO_UPDATE}\\n";

        assertBuild("prj-simple",
            script(dockerfile, "--build-arg IMAGE_TO_UPDATE=hello-world:latest"), HELLO_WORLD_IMAGE);

        assertBuild("prj-singlequotes-in-build-arg---aroundValue",
            script(dockerfile, "--build-arg IMAGE_TO_UPDATE=\\'hello-world:latest\\'"), HELLO_WORLD_IMAGE);

        assertBuild("prj-dobulequotes-in-build-arg---aroundValue",
            script(dockerfile, "--build-arg IMAGE_TO_UPDATE=\"hello-world:latest\""), HELLO_WORLD_IMAGE);

        assertBuild("prj-singlequotes-in-build-arg---aroundAll",
            script(dockerfile, "--build-arg \\'IMAGE_TO_UPDATE=hello-world:latest\\'"), HELLO_WORLD_IMAGE);

        assertBuild("prj-doublequotes-in-build-arg---aroundAll",
            script(dockerfile, "--build-arg \"IMAGE_TO_UPDATE=hello-world:latest\""), HELLO_WORLD_IMAGE);
    }

    @Test public void buildWithDefaultArgsFromDockerfile() throws Exception {
        String dockerfile = ""+
            "ARG REGISTRY_URL\\n" +
            "ARG TAG=latest\\n" +
            "FROM ${REGISTRY_URL}busybox:${TAG}\\n";

        assertBuild("prj-with-default-arg-from-dockerfile",
            script(dockerfile, ""), BUSYBOX_IMAGE);

        assertBuild("prj-override-empty-value",
            script(dockerfile, "--build-arg REGISTRY_URL="), BUSYBOX_IMAGE);

        assertBuild("prj-with-override-value",
            script(dockerfile, "--build-arg TAG=1.27.1"), BUSYBOX_IMAGE+":1.27.1");

        assertBuild("prj-with-override-value2",
            script(dockerfile, "--build-arg TAG=1.26"), BUSYBOX_IMAGE+":1.26");
    }

    @Test public void buildOverridingRegistry() throws Exception {
        String quayDockerfile = ""+
            "ARG REGISTRY_URL\\n" +
            "ARG TAG=latest\\n" +
            "FROM ${REGISTRY_URL}prometheus/busybox:${TAG}\\n";

        assertBuild("prj-override-registry",
            script(quayDockerfile, "--build-arg REGISTRY_URL=\\'quay.io/\\'"), "quay.io/prometheus/busybox");
    }

    private static String script(String dockerfile, String buildArg) {
        String fullBuildArgs = buildArg + " buildWithFROMArgs";

        String script = "node {\n" +
            "  sh 'mkdir buildWithFROMArgs'\n" +
            "  writeFile file: 'buildWithFROMArgs/Dockerfile', text: '" + dockerfile + "'\n" +
            "  def built = docker.build 'from-with-arg', '" + fullBuildArgs + "'\n" +
            "  echo \"built ${built.id}\"\n" +
            "}";

        return script;
    }

    private void assertBuild(final String projectName, final String pipelineCode, final String fromImage) throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumeDocker();

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, projectName);
                p.setDefinition(new CpsFlowDefinition(pipelineCode, true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                DockerClient client = new DockerClient(new LocalLauncher(StreamTaskListener.NULL), null, null);
                String ancestorImageId = client.inspect(new EnvVars(), fromImage, ".Id");
                story.j.assertLogContains("built from-with-arg", b);
                story.j.assertLogContains(ancestorImageId.replaceFirst("^sha256:", "").substring(0, 12), b);
                Fingerprint f = DockerFingerprints.of(ancestorImageId);
                assertNotNull(f);
                DockerDescendantFingerprintFacet descendantFacet = f.getFacet(DockerDescendantFingerprintFacet.class);
                assertNotNull(descendantFacet);
            }
        });
    }

}
