package org.jenkinsci.plugins.docker.workflow.client;

import com.google.common.base.Optional;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.util.ArgumentListBuilder;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WindowsLinuxDockerClient extends WindowsDockerClient {
    private static final Logger LOGGER = Logger.getLogger(WindowsLinuxDockerClient.class.getName());

    private final Launcher launcher;
    private final Node node;

    public WindowsLinuxDockerClient(@Nonnull Launcher launcher, @CheckForNull Node node, @CheckForNull String toolName) {
        super(launcher, node, toolName);
        this.launcher = launcher;
        this.node = node;
    }

    @Override
    public List<String> listProcess(@Nonnull EnvVars launchEnv, @Nonnull String containerId) throws IOException, InterruptedException {
        LaunchResult result = launch(launchEnv, false, null, "docker", "top", containerId);
        if (result.getStatus() != 0) {
            throw new IOException(String.format("Failed to run top '%s'. Error: %s", containerId, result.getErr()));
        }
        List<String> processes = new ArrayList<>();
        try (Reader r = new StringReader(result.getOut());
             BufferedReader in = new BufferedReader(r)) {
            String line;
            in.readLine(); // ps header
            while ((line = in.readLine()) != null) {
                final StringTokenizer stringTokenizer = new StringTokenizer(line, " ");
                if (stringTokenizer.countTokens() < 4) {
                    throw new IOException("Unexpected `docker top` output : "+line);
                }

                stringTokenizer.nextToken(); // PID
                stringTokenizer.nextToken(); // USER
                stringTokenizer.nextToken(); // TIME
                processes.add(stringTokenizer.nextToken()); // COMMAND
            }
        }
        return processes;
    }

    @Override
    public String runCommand()
    {
        return "cat";
    }

    @Override
    public boolean needToContainerizePath() {
        return true;
    }
}
