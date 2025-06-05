package org.jenkinsci.plugins.docker.workflow.client;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Proc;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class DockerClientUnitTest {
    private DockerClient dockerClient;
    private Launcher launcher;

    @Before
    public void setup() throws Exception {
        launcher = Mockito.mock(Launcher.class);
        dockerClient = new DockerClient(launcher, null, null);
    }


    @Test
    public void test_run() throws IOException, InterruptedException {
        var output = "ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c";
        Launcher.ProcStarter procStarter = launcher.new ProcStarter();
        Mockito.when(launcher.launch()).thenReturn(procStarter);
        Proc proc = Mockito.mock(Proc.class);
        Mockito.when(launcher.launch(procStarter)).thenAnswer(input -> {
            procStarter.stdout().write(output.getBytes(StandardCharsets.UTF_8));
            return proc;
        });
        Mockito.when(proc.joinWithTimeout(Mockito.anyLong(), Mockito.any(), Mockito.any())).thenReturn(0);

        String containerId =
            dockerClient.run(new EnvVars(), "test-image", null, null, Collections.<String, String>emptyMap(), Collections.<String>emptyList(), new EnvVars(),
                dockerClient.whoAmI(), "cat");

        Assert.assertEquals("ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c", containerId);
    }

    @Test
    public void test_leaked_descriptors() throws IOException, InterruptedException {
        // See https://issues.jenkins.io/browse/JENKINS-63628
        var output = "ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c\nProcess leaked file descriptors. See https://jenkins.io/redirect/troubleshooting/process-leaked-file-descriptors for more information";
        Launcher.ProcStarter procStarter = launcher.new ProcStarter();
        Mockito.when(launcher.launch()).thenReturn(procStarter);
        Proc proc = Mockito.mock(Proc.class);
        Mockito.when(launcher.launch(procStarter)).thenAnswer(input -> {
            procStarter.stdout().write(output.getBytes(StandardCharsets.UTF_8));
            return proc;
        });
        Mockito.when(proc.joinWithTimeout(Mockito.anyLong(), Mockito.any(), Mockito.any())).thenReturn(0);

        String containerId =
            dockerClient.run(new EnvVars(), "test-image", null, null, Collections.<String, String>emptyMap(), Collections.<String>emptyList(), new EnvVars(),
                dockerClient.whoAmI(), "cat");

        Assert.assertEquals("ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c", containerId);
    }
}
