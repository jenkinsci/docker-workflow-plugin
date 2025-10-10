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

import java.io.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.docker.commons.tools.DockerTool;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DockerTestUtil {

    public static final String DEFAULT_MINIMUM_VERSION = "1.3";

    // Major Windows kernel versions. See https://hub.docker.com/r/microsoft/windows-nanoserver
    private static final List<String> MAJOR_WINDOWS_KERNEL_VERSIONS = Arrays.asList(
        "10.0.17763.6659", // 1809
        "10.0.18363.1556", // 1909
        "10.0.19041.1415", // 2004
        "10.0.19042.1889", // 20H2
        "10.0.20348.2966", // 2022
        "10.0.26100.2605"  // 2025
    );

    public enum DockerOsMode {
        LINUX,
        WINDOWS
    }

    public static void assumeDocker() throws Exception {
        assumeDocker(DockerOsMode.LINUX, new VersionNumber(DEFAULT_MINIMUM_VERSION));
    }

    public static void assumeDocker(DockerOsMode osMode) throws Exception {
        assumeDocker(osMode, new VersionNumber(DEFAULT_MINIMUM_VERSION));
    }

    public static void assumeDocker(DockerOsMode osMode, VersionNumber minimumVersion) throws Exception {
        Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);
        try {
            int status = localLauncher
                .launch()
                .cmds(DockerTool.getExecutable(null, null, null, null), "ps")
                .start()
                .joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, localLauncher.getListener());
            assumeTrue(status == 0, "Docker working");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            status = localLauncher
                .launch()
                .cmds(DockerTool.getExecutable(null, null, null, null), "version", "-f", "{{.Server.Os}}")
                .stdout(out)
                .start()
                .joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, localLauncher.getListener());
            DockerOsMode cmdOsMode = DockerOsMode.valueOf(out.toString().trim().toUpperCase());
            assumeTrue(status == 0, "Docker working");
            assumeTrue(osMode == cmdOsMode, "Docker os mode " + osMode);
        } catch (IOException x) {
            assumeTrue(false, "have Docker installed: " + x);
        }
        DockerClient dockerClient = new DockerClient(localLauncher, null, null);
        assumeFalse(dockerClient.version().isOlderThan(minimumVersion), "Docker version not < " + minimumVersion.toString());
    }

    public static void assumeWindows() {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("windows"));
    }

    public static void assumeNotWindows() {
        assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));
    }

    public static void assumeDrive(char drive) {
        assumeTrue(new File(drive + ":/").exists());
    }

    public static String getWindowsKernelVersion() throws Exception {
        Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int status = localLauncher
            .launch()
            .cmds("cmd", "/c", "ver")
            .stdout(out)
            .stderr(err)
            .start()
            .joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, localLauncher.getListener());

        if (status != 0) {
            throw new RuntimeException(String.format("Failed to obtain Windows kernel version with exit code: %d stdout: %s stderr: %s", status, out, err));
        }

        Matcher matcher = Pattern.compile("Microsoft Windows \\[Version ([^\\]]+)\\]").matcher(out.toString().trim());

        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("Unable to obtain Windows kernel version from output: " + out);
        }
    }

    /**
     * @return The image tag of an image with a kernel version corresponding to the closest compatible Windows release
     * @throws Exception
     */
    public static String getWindowsImageTag() throws Exception {
        // Kernel must match when running Windows containers on docker on Windows if < Windows 11 with Server 2022
        String kernelVersion = DockerTestUtil.getWindowsKernelVersion();

        // Select the highest well known kernel version <= ours since sometimes an image may not exist for our version
        Optional<String> wellKnownKernelVersion = MAJOR_WINDOWS_KERNEL_VERSIONS.stream()
            .filter(k -> k.compareTo(kernelVersion) <= 0).max(java.util.Comparator.naturalOrder());

        // Fall back to trying our kernel version
        return wellKnownKernelVersion.orElse(kernelVersion);
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
