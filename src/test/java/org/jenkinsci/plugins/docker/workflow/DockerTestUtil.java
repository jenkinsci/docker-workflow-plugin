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
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import hudson.Launcher;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import org.junit.Assume;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.docker.commons.tools.DockerTool;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DockerTestUtil {
    public static String DEFAULT_MINIMUM_VERSION = "1.3";

    public static void assumeDocker() throws Exception {
        assumeDocker(new VersionNumber(DEFAULT_MINIMUM_VERSION));
    }
    
    public static void assumeDocker(VersionNumber minimumVersion) throws Exception {
        Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);
        try {
            int status = localLauncher
                .launch()
                .cmds(DockerTool.getExecutable(null, null, null, null), "ps")
                .start()
                .joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, localLauncher.getListener());
            Assume.assumeTrue("Docker working", status == 0);
        } catch (IOException x) {
            Assume.assumeNoException("have Docker installed", x);
        }
        DockerClient dockerClient = new DockerClient(localLauncher, null, null);
        Assume.assumeFalse("Docker version not < " + minimumVersion.toString(), dockerClient.version().isOlderThan(minimumVersion));
    }

    public static void assumeNotWindows() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));
    }

    public static EnvVars newDockerLaunchEnv() {
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

    private DockerTestUtil() {}
    
}
