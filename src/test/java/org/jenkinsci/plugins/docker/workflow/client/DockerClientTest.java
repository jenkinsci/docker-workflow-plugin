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
package org.jenkinsci.plugins.docker.workflow.client;

import org.jenkinsci.plugins.docker.workflow.DockerTestUtil;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DockerClientTest {

    private DockerClient dockerClient;
    
    @Before
    public void setup() throws Exception {
        DockerTestUtil.assumeDocker();

        // Set stuff up for the test
        TaskListener taskListener = StreamTaskListener.fromStderr();
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        dockerClient = new DockerClient(launcher, null, null);
    }

    @Test
    public void test_run() throws IOException, InterruptedException {
        EnvVars launchEnv = newLaunchEnv();
        String containerId =
                dockerClient.run(launchEnv, "learn/tutorial", null, null, Collections.<String, String>emptyMap(), new EnvVars(),
                        dockerClient.whoAmI(), "echo", "hello world");
        Assert.assertEquals(64, containerId.length());
        ContainerRecord containerRecord = dockerClient.getContainerRecord(launchEnv, containerId);
        Assert.assertEquals("8dbd9e392a964056420e5d58ca5cc376ef18e2de93b5cc90e868a1bbc8318c1c", containerRecord.getImageId());
        Assert.assertTrue(containerRecord.getContainerName().length() > 0);
        Assert.assertTrue(containerRecord.getHost().length() > 0);
        Assert.assertTrue(containerRecord.getCreated() > 1000000000000L);

        // Also test that the stop works and cleans up after itself
        Assert.assertNotNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        dockerClient.stop(launchEnv, containerId);
        Assert.assertNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
    }

    @Test
    public void test_valid_version() {
        VersionNumber dockerVersion = DockerClient.parseVersionNumber("Docker version 1.5.0, build a8a31ef");
        Assert.assertFalse(dockerVersion.isOlderThan(new VersionNumber("1.1")));
        Assert.assertFalse(dockerVersion.isOlderThan(new VersionNumber("1.5")));
        Assert.assertTrue(dockerVersion.isOlderThan(new VersionNumber("1.10")));
    }
    
    @Test
    public void test_invalid_version() {
        Assert.assertNull(DockerClient.parseVersionNumber("xxx"));
    }
    
    private EnvVars newLaunchEnv() {
        // Create the KeyMaterial for connecting to the docker host/server.
        // E.g. currently need to add something like the following to your env
        //  -DDOCKER_HOST_FOR_TEST="tcp://192.168.x.y:2376"
        //  -DDOCKER_HOST_KEY_DIR_FOR_TEST="/Users/tfennelly/.boot2docker/certs/boot2docker-vm"
        final String docker_host_for_test = System.getProperty("DOCKER_HOST_FOR_TEST");
        final String docker_host_key_dir_for_test = System.getProperty("DOCKER_HOST_KEY_DIR_FOR_TEST");
        
        EnvVars env = new EnvVars();
        if (docker_host_for_test != null) {
            env.put("DOCKER_HOST", docker_host_for_test);
        }
        if (docker_host_key_dir_for_test != null) {
            env.put("DOCKER_TLS_VERIFY", "1");
            env.put("DOCKER_CERT_PATH", docker_host_key_dir_for_test);
        }
        return env;
    }
}
