package org.jenkinsci.plugins.docker.workflow.client;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;
import org.jenkinsci.plugins.docker.workflow.DockerTestUtil;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Collections;

class WindowsDockerClientTest {

    private DockerClient dockerClient;

    @BeforeEach
    void setup() {
        TaskListener taskListener = StreamTaskListener.fromStderr();
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        dockerClient = new WindowsDockerClient(launcher, null, null);
    }

    @Test
    void test_run() throws Exception {
        DockerTestUtil.assumeDocker();
        EnvVars launchEnv = DockerTestUtil.newDockerLaunchEnv();
        String containerId = dockerClient.run(
            launchEnv,
            "busybox",
            null,
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            new EnvVars(),
            dockerClient.whoAmI(),
            "cat");

        assertEquals(64, containerId.length());
        ContainerRecord containerRecord = dockerClient.getContainerRecord(launchEnv, containerId);
        assertEquals(dockerClient.inspect(launchEnv, "busybox", ".Id"), containerRecord.getImageId());
        assertFalse(containerRecord.getContainerName().isEmpty());
        assertFalse(containerRecord.getHost().isEmpty());
        assertTrue(containerRecord.getCreated() > 1000000000000L);
        assertEquals(Collections.<String>emptyList(), dockerClient.getVolumes(launchEnv, containerId));

        // Also test that the stop works and cleans up after itself
        assertNotNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        dockerClient.stop(launchEnv, containerId);
        assertNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
    }
}
