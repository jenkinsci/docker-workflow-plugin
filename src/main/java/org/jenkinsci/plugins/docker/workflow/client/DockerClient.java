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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.util.ArgumentListBuilder;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;

/**
 * Simple docker client for workflow.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DockerClient {

    private static final Logger LOGGER = Logger.getLogger(DockerClient.class.getName());

    // e.g. 2015-04-09T13:40:21.981801679Z
    public static final String DOCKER_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private Launcher launcher;
    private final @CheckForNull Node node;
    private final @CheckForNull String toolName;

    public DockerClient(@Nonnull Launcher launcher, @CheckForNull Node node, @CheckForNull String toolName) {
        this.launcher = launcher;
        this.node = node;
        this.toolName = toolName;
    }

    /**
     * Run a docker image.
     * Runs the image in detached (-d) mode with a pseudo-tty (-t). Use
     * {@link #run(hudson.EnvVars, java.lang.String, java.util.Collection, java.lang.String, java.util.Map, hudson.EnvVars, java.lang.String, java.lang.String...)}
     * to get another behavior.
     * @param launchEnv Docker client launch environment.
     * @param image The image name.
     * @param args Any additional arguments for the {@code docker run} command.
     * @param workdir The working directory in the container, or {@code null} for default.
     * @param volumes Volumes to be bound. Supply an empty list if no volumes are to be bound.
     * @param containerEnv Environment variables to set in container.
     * @param user The <strong>uid:gid</strong> to execute the container command as. Use {@link #whoAmI()}.
     * @param command The command to execute in the image container being run.
     * @return The container ID.
     */
    public String run(@Nonnull EnvVars launchEnv, @Nonnull String image, @CheckForNull String args, @CheckForNull String workdir, @Nonnull Map<String, String> volumes, @Nonnull EnvVars containerEnv, @Nonnull String user, @CheckForNull String... command) throws IOException, InterruptedException {
        final List<String> argb = new LinkedList<String>();
        if (args != null) {
            argb.addAll(Arrays.asList(Util.tokenize(args)));
        }
        argb.add("-t");
        argb.add("-d");
        LaunchResult result = runImage(launchEnv, image, argb, workdir, volumes, containerEnv, user, command);
        if (result.getStatus() == 0) {
            return result.getOut();
        } else {
            throw new IOException(String.format("Failed to run image '%s'. Error: %s", image, result.getErr()));
        }
    }
    
    /**
     * Run a docker image.
     * @param launchEnv Docker client launch environment.
     * @param image The image name.
     * @param args Any additional arguments for the {@code docker run} command.
     * @param workdir The working directory in the container, or {@code null} for default.
     * @param volumes Volumes to be bound. Supply an empty list if no volumes are to be bound.
     * @param containerEnv Environment variables to set in container.
     * @param user The <strong>uid:gid</strong> to execute the container command as. Use {@link #whoAmI()}.
     * @param command The command to execute in the image container being run.
     * @return Execution result
     * @since TODO
     */
    public @Nonnull LaunchResult runImage(@Nonnull EnvVars launchEnv, @Nonnull String image, 
            @CheckForNull Collection<String> args, @CheckForNull String workdir, 
            @Nonnull Map<String, String> volumes, @Nonnull EnvVars containerEnv, 
            @Nonnull String user, @CheckForNull String... command) throws IOException, InterruptedException {    
        ArgumentListBuilder argb = new ArgumentListBuilder();

        argb.add("run", "-u", user);
        if (args != null) {
            argb.add(args.toArray(new String[args.size()]));
        }

        if (workdir != null) {
            argb.add("-w", workdir);
        }
        for (Map.Entry<String, String> volume : volumes.entrySet()) {
            argb.add("-v", volume.getKey() + ":" + volume.getValue() + ":rw");
        }
        for (Map.Entry<String, String> variable : containerEnv.entrySet()) {
            argb.add("-e");
            argb.addMasked(variable.getKey()+"="+variable.getValue());
        }
        if (command != null) {
            argb.add(image).add(command);
        }

        return launch(launchEnv, false, null, argb);
    }

    /**
     * Stop a container.
     * 
     * <p>                              
     * Also removes ({@link #rm(EnvVars, String)}) the container.
     * 
     * @param launchEnv Docker client launch environment.
     * @param containerId The container ID.
     */
    public void stop(@Nonnull EnvVars launchEnv, @Nonnull String containerId) throws IOException, InterruptedException {
        LaunchResult result = launch(launchEnv, false, "stop", containerId);
        if (result.getStatus() != 0) {
            throw new IOException(String.format("Failed to kill container '%s'.", containerId));
        }
        rm(launchEnv, containerId);
    }

    /**
     * Remove a container.
     * 
     * @param launchEnv Docker client launch environment.
     * @param containerId The container ID.
     */
    public void rm(@Nonnull EnvVars launchEnv, @Nonnull String containerId) throws IOException, InterruptedException {
        LaunchResult result;
        result = launch(launchEnv, false, "rm", "-f", containerId);
        if (result.getStatus() != 0) {
            throw new IOException(String.format("Failed to rm container '%s'.", containerId));
        }
    }

    /**
     * Inspect a docker image/container.
     * @param launchEnv Docker client launch environment.
     * @param objectId The image/container ID.
     * @param fieldPath The data path of the data required e.g. {@code .NetworkSettings.IPAddress}.
     * @return The inspected field value.
     */
    public @CheckForNull String inspect(@Nonnull EnvVars launchEnv, @Nonnull String objectId, @Nonnull String fieldPath) throws IOException, InterruptedException {
        LaunchResult result = launch(launchEnv, true, "inspect", "-f", String.format("{{%s}}", fieldPath), objectId);
        if (result.getStatus() == 0) {
            return result.getOut();
        } else {
            return null;
        }
    }

    private Date getCreatedDate(@Nonnull EnvVars launchEnv, @Nonnull String objectId) throws IOException, InterruptedException {
        String createdString = inspect(launchEnv, objectId, ".Created");        
        if (createdString == null) {
            return null;
        }
        try {
            // TODO Currently truncating. Find out how to specify last part for parsing (TZ etc)
            return new SimpleDateFormat(DOCKER_DATE_TIME_FORMAT).parse(createdString.substring(0, DOCKER_DATE_TIME_FORMAT.length() - 2));
        } catch (ParseException e) {
            throw new IOException(String.format("Error parsing created date '%s' for object '%s'.", createdString, objectId), e);
        }
    }

    /**
     * Get the docker version.
     *
     * @return The {@link VersionNumber} instance if the version string matches the expected format,
     * otherwise {@code null}.
     */
    public @CheckForNull VersionNumber version() throws IOException, InterruptedException {
        LaunchResult result = launch(new EnvVars(), true, "-v");
        if (result.getStatus() == 0) {
            return parseVersionNumber(result.getOut());
        } else {
            return null;
        }
    }
    
    private static final Pattern pattern = Pattern.compile("^(\\D+)(\\d+)\\.(\\d+)\\.(\\d+)(.*)");
    /**
     * Parse a Docker version string (e.g. "Docker version 1.5.0, build a8a31ef").
     * @param versionString The version string to parse.
     * @return The {@link VersionNumber} instance if the version string matched the
     * expected format, otherwise {@code null}.
     */
    protected static VersionNumber parseVersionNumber(@Nonnull String versionString) {
        Matcher matcher = pattern.matcher(versionString.trim());
        if (matcher.matches()) {
            String major = matcher.group(2);
            String minor = matcher.group(3);
            String maint = matcher.group(4);
            return new VersionNumber(String.format("%s.%s.%s", major, minor, maint));
        } else {
            return null;
        }        
    }

    private LaunchResult launch(@Nonnull EnvVars launchEnv, boolean quiet, @Nonnull String... args) throws IOException, InterruptedException {
        return launch(launchEnv, quiet, null, args);
    }
    private LaunchResult launch(@Nonnull EnvVars launchEnv, boolean quiet, FilePath pwd, @Nonnull String... args) throws IOException, InterruptedException {
        return launch(launchEnv, quiet, pwd, new ArgumentListBuilder(args));
    }
    private LaunchResult launch(@CheckForNull @Nonnull EnvVars launchEnv, boolean quiet, FilePath pwd, @Nonnull ArgumentListBuilder args) throws IOException, InterruptedException {
        // Prepend the docker command
        args.prepend(DockerTool.getExecutable(toolName, node, launcher.getListener(), launchEnv));

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Executing docker command {0}", args.toString());
        }

        Launcher.ProcStarter procStarter = launcher.launch();

        if (pwd != null) {
            procStarter.pwd(pwd);
        }

        LaunchResult result = new LaunchResult();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            result.setStatus(procStarter.quiet(quiet).cmds(args).envs(launchEnv).stdout(out).stderr(err).join());
            return result;
        } finally {
            try {
                result.setOut(out.toString());
                out.close();
            } finally {
                result.setErr(err.toString());
                err.close();
            }
        }
    }

    /**
     * Who is executing this {@link DockerClient} instance.
     *
     * @return a {@link String} containing the <strong>uid:gid</strong>.
     */
    public String whoAmI() throws IOException, InterruptedException {
        ByteArrayOutputStream userId = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-u").quiet(true).stdout(userId).join();

        ByteArrayOutputStream groupId = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-g").quiet(true).stdout(groupId).join();

        return String.format("%s:%s", userId.toString().trim(), groupId.toString().trim());

    }

    public ContainerRecord getContainerRecord(@Nonnull EnvVars launchEnv, String containerId) throws IOException, InterruptedException {
        String host = inspect(launchEnv, containerId, ".Config.Hostname");
        String containerName = inspect(launchEnv, containerId, ".Name");
        Date created = getCreatedDate(launchEnv, containerId);
        String image = inspect(launchEnv, containerId, ".Image");

        // TODO get tags and add for ContainerRecord
        return new ContainerRecord(host, containerId, image, containerName,
                (created != null ? created.getTime() : 0L), 
                Collections.<String,String>emptyMap());
    }
}
