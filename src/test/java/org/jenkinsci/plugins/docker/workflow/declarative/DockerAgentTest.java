/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package org.jenkinsci.plugins.docker.workflow.declarative;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jenkinsci.plugins.docker.workflow.DockerTestUtil;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import static org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest.j;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Adapted from {@link org.jenkinsci.plugins.pipeline.modeldefinition.AgentTest}.
 */
public class DockerAgentTest extends AbstractModelDefTest {

    private static Slave s;
    private static Slave s2;

    private static String password;
    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));
        s.setNumExecutors(2);

        s2 = j.createOnlineSlave();
        s2.setLabelString("other-docker");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second")));
        //setup credentials for docker registry
        CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();

        password = System.getProperty("docker.password");

        if(password != null) {
            UsernamePasswordCredentialsImpl globalCred =
                    new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                            "dockerhub", "real", "jtaboada", password);

            store.addCredentials(Domain.global(), globalCred);

        }
    }

    @Test
    public void agentDocker() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDocker", "-v /tmp:/tmp");
    }

    @Test
    public void agentDockerWithCreds() throws Exception {
        //If there is no password, the test is ignored
        if(password != null)
            agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerWithCreds", "-v /tmp:/tmp");
    }

    @Test
    public void agentDockerWithRegistryNoCreds() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerWithRegistryNoCreds",
                "-v /tmp:/tmp",
                "Registry is https://index.docker.io/v2/");
    }

    @Test
    public void agentDockerReuseNode() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerReuseNode");
    }

    @Issue("JENKINS-49558")
    @Test
    public void agentDockerContainerPerStage() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerContainerPerStage");
    }

    @Issue("JENKINS-49558")
    @Test
    public void agentDockerWithoutContainerPerStage() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerWithoutContainerPerStage");
    }

    @Test
    public void agentDockerDontReuseNode() throws Exception {
        DockerTestUtil.assumeDocker();

        expect(Result.FAILURE, "org/jenkinsci/plugins/docker/workflow/declarative/agentDockerDontReuseNode")
                .logContains("The answer is 42")
                .go();

    }

    // Also covers agentAnyInStage, perStageConfigAgent
    @Issue("JENKINS-41605")
    @Test
    public void agentInStageAutoCheckout() throws Exception {
        DockerTestUtil.assumeDocker();

        expect("org/jenkinsci/plugins/docker/workflow/declarative/agentInStageAutoCheckout")
                .logContains("The answer is 42",
                        "found tmp.txt in bar",
                        "did not find tmp.txt in new docker node",
                        "did not find tmp.txt in new label node")
                .go();

    }

    @Test
    public void agentDockerWithNullDockerArgs() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerWithNullDockerArgs");
    }

    @Test
    public void agentDockerWithEmptyDockerArgs() throws Exception {
        agentDocker("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerWithEmptyDockerArgs");
    }

    @Issue("JENKINS-41950")
    @Test
    public void nonExistentDockerImage() throws Exception {
        DockerTestUtil.assumeDocker();

        expect(Result.FAILURE, "org/jenkinsci/plugins/docker/workflow/declarative/nonExistentDockerImage")
                .logContains("ERROR: script returned exit code 1",
                        "There is no image")
                .go();
    }


    @Test
    public void fromDockerfile() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/fromDockerfile")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Issue("https://github.com/jenkinsci/docker-workflow-plugin/pull/57#issuecomment-1507755385")
    @Test
    public void userHandbookDockerfile() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("Dockerfile", "FROM node:16.13.1-alpine\nRUN apk add -U subversion\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/userHandbookDockerfile")
                .logContains("[Pipeline] { (Test)",
                        "svn, version ")
                .go();
    }

    @Test
    public void additionalDockerBuildArgs() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nARG someArg=thisArgHere\n\nRUN echo \"hi there, $someArg\" > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/additionalDockerBuildArgs")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "hi there, thisOtherArg")
                .logNotContains("hi there, thisArgHere")
                .go();
    }

    @Issue("JENKINS-57162")
    @Test
    public void additionalDockerBuildArgsImageHash() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("Dockerfile",  "FROM ubuntu:14.04\n\nARG someArg=thisArgHere\n\nRUN echo \"hi there, $someArg\" > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        Folder folder = j.jenkins.createProject(Folder.class, "testFolder");
        expect("org/jenkinsci/plugins/docker/workflow/declarative/additionalDockerBuildArgsParallel")
                .inFolder(folder)
                .withProjectName("parallelImageHashTest")
                .logContains("[Pipeline] { (foo)",
                        "-v /tmp:/tmp",
                        "docker build -t 8343c0815beb7a50c3676f09d7175d903a57f11b --build-arg someArg=thisOtherArg",
                        "The answer is 42",
                        "hi there, thisOtherArg",
                        "[Pipeline] { (bar)",
                        "docker build -t 4f8d74de557925eb9aacdfdf671b5e9de11b6086 --build-arg someArg=thisDifferentArg",
                        "The answer is 43",
                        "hi there, thisDifferentArg")
                .logNotContains("hi there, thisArgHere")
                .go();
    }

    @Issue("JENKINS-41668")
    @Test
    public void fromDockerfileInOtherDir() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("subdir/Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "subdir/Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/fromDockerfileInOtherDir")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Issue("JENKINS-42286")
    @Test
    public void dirSepInDockerfileName() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("subdir/Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "subdir/Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/fromDockerfileInOtherDir")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Test
    public void fromDockerfileNoArgs() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/fromDockerfileNoArgs")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "HI THERE")
                .go();
    }

    @Test
    public void fromAlternateDockerfile() throws Exception {
        DockerTestUtil.assumeDocker();
        sampleRepo.write("Dockerfile.alternate", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile.alternate");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/fromAlternateDockerfile")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Ignore("Until JENKINS-46831 is addressed")
    @Issue("JENKINS-46831")
    @Test
    public void agentDockerGlobalThenLabel() throws Exception {
        expect("org/jenkinsci/plugins/docker/workflow/declarative/agentDockerGlobalThenLabel")
            .logContains(
                "first agent = first",
                "second agent = second"
            )
            .go();
    }

    @Issue("JENKINS-47106")
    @Test
    public void dockerPullLocalImage() throws Exception {
        DockerTestUtil.assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("org/jenkinsci/plugins/docker/workflow/declarative/dockerPullLocalImage")
                .logContains("[Pipeline] { (in built image)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE",
                        "Maven home: /usr/share/maven")
                .go();
    }

    private void agentDocker(final String jenkinsfile, String... additionalLogContains) throws Exception {
        DockerTestUtil.assumeDocker();

        List<String> logContains = new ArrayList<>();
        logContains.add("[Pipeline] { (foo)");
        logContains.add("The answer is 42");
        logContains.addAll(Arrays.asList(additionalLogContains));

        expect(jenkinsfile)
                .logContains(logContains.toArray(new String[logContains.size()]))
                .go();
    }

}
