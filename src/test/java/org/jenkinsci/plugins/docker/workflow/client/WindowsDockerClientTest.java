package org.jenkinsci.plugins.docker.workflow.client;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;
import org.jenkinsci.plugins.docker.workflow.DockerTestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WindowsDockerClientTest {

    private DockerClient dockerClient;

    @Before
    public void setup() {
        TaskListener taskListener = StreamTaskListener.fromStderr();
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        dockerClient = new WindowsDockerClient(launcher, null, null);
    }

    @Test
    public void test_run() throws Exception {
        DockerTestUtil.assumeDocker();
        EnvVars launchEnv = DockerTestUtil.newDockerLaunchEnv();
        String containerId = dockerClient.run(
            launchEnv,
            "busybox",
            null,
            null,
            Collections.singletonMap("D:\\Jenkins\\workspace", "D:\\Jenkins\\workspace"),
            Collections.emptyList(),
            new EnvVars(),
            dockerClient.whoAmI(),
            "cat");

        Assert.assertEquals(64, containerId.length());
        ContainerRecord containerRecord = dockerClient.getContainerRecord(launchEnv, containerId);
        Assert.assertEquals(dockerClient.inspect(launchEnv, "busybox", ".Id"), containerRecord.getImageId());
        Assert.assertTrue(containerRecord.getContainerName().length() > 0);
        Assert.assertTrue(containerRecord.getHost().length() > 0);
        Assert.assertTrue(containerRecord.getCreated() > 1000000000000L);
        Assert.assertEquals(Collections.singletonList("d:"), dockerClient.getVolumes(launchEnv, containerId));

        // Also test that the stop works and cleans up after itself
        Assert.assertNotNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        dockerClient.stop(launchEnv, containerId);
        Assert.assertNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
    }
}
