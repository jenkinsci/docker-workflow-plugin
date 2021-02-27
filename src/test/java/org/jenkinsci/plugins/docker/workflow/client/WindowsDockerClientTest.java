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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class WindowsDockerClientTest {

    private DockerClient dockerClient;

    @Before
    public void setup() throws Exception {
        DockerTestUtil.assumeDocker();

        TaskListener taskListener = StreamTaskListener.fromStderr();
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        dockerClient = new WindowsDockerClient(launcher, null, null);
    }

    @Test
    public void test_run() throws IOException, InterruptedException {
        EnvVars launchEnv = DockerTestUtil.newDockerLaunchEnv();
        String containerId = dockerClient.run(
            launchEnv,
            "learn/tutorial",
            null,
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            new EnvVars(),
            dockerClient.whoAmI(),
            "cat");

        Assert.assertEquals(64, containerId.length());
        ContainerRecord containerRecord = dockerClient.getContainerRecord(launchEnv, containerId);
        Assert.assertEquals(dockerClient.inspect(launchEnv, "learn/tutorial", ".Id"), containerRecord.getImageId());
        Assert.assertTrue(containerRecord.getContainerName().length() > 0);
        Assert.assertTrue(containerRecord.getHost().length() > 0);
        Assert.assertTrue(containerRecord.getCreated() > 1000000000000L);
        Assert.assertEquals(Collections.<String>emptyList(), dockerClient.getVolumes(launchEnv, containerId));

        // Also test that the stop works and cleans up after itself
        Assert.assertNotNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        dockerClient.stop(launchEnv, containerId);
        Assert.assertNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
    }

    @Test
    public void test_run_with_env_vars() throws IOException, InterruptedException {
        EnvVars launchEnv = DockerTestUtil.newDockerLaunchEnv();
        EnvVars containerEnv = new EnvVars(
            "VAR", "A",
            "VAR_WITH_SPACE", "A B",
            "VAR_WITH_EQUALS", "A=B",
            "VAR_WITH_QUOTE", "A\"B",
            "VAR_WITH_SPACE_AND_EQUALS", "A=B C",
            "VAR_WITH_SPACE_EQUALS_AND_QUOTE", "A=\"B C\""
        );
        String containerId = dockerClient.run(
            launchEnv,
            "learn/tutorial",
            null,
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            containerEnv,
            dockerClient.whoAmI(),
            "cat");
        Assert.assertEquals(64, containerId.length());
        ContainerRecord containerRecord = dockerClient.getContainerRecord(launchEnv, containerId);
        Assert.assertEquals(dockerClient.inspect(launchEnv, "learn/tutorial", ".Id"), containerRecord.getImageId());
        Assert.assertTrue(containerRecord.getContainerName().length() > 0);
        Assert.assertTrue(containerRecord.getHost().length() > 0);
        Assert.assertTrue(containerRecord.getCreated() > 1000000000000L);
        Assert.assertEquals(Collections.<String>emptyList(), dockerClient.getVolumes(launchEnv, containerId));

        String envVarsString = dockerClient.inspect(launchEnv, containerId, ".Config.Env");
        Assert.assertNotNull(envVarsString);

        for (Map.Entry<String, String> variable : containerEnv.entrySet()) {
            Assert.assertTrue(envVarsString.contains(variable.getKey() + "=" + variable.getValue()));
        }

        // Also test that the stop works and cleans up after itself
        Assert.assertNotNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        dockerClient.stop(launchEnv, containerId);
        Assert.assertNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
    }
}
