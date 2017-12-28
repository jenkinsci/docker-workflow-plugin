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

import com.google.common.base.Optional;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.util.ArgumentListBuilder;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Simple docker client for Pipeline.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DockerClient {

    private static final Logger LOGGER = Logger.getLogger(DockerClient.class.getName());

    /**
     * Maximum amount of time (in seconds) to wait for {@code docker} client operations which are supposed to be more or less instantaneous.
     */
    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL", justification="mutable for scripts")
    @Restricted(NoExternalUse.class)
    public static int CLIENT_TIMEOUT = Integer.getInteger(DockerClient.class.getName() + ".CLIENT_TIMEOUT", 180); // TODO 2.4+ SystemProperties

    // e.g. 2015-04-09T13:40:21.981801679Z
    public static final String DOCKER_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    
    /**
     * Known cgroup formats
     * 
     * 4:cpuset:/system.slice/docker-3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b.scope
     * 2:cpu:/docker/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
     * 10:cpu,cpuacct:/docker/a9f3c3932cd81c4a74cc7e0a18c3300255159512f1d000545c42895adaf68932/docker/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
     * 3:cpu:/docker/4193df6bcf5fce75f3fc77f303b2ac06fb664adeb269b959b7ae17b3f8dcf329/14d7240da87b145e4992654c908a8631dbf179abb7f88115ea72743e1192d07d
     */
    public static final String CGROUP_MATCHER_PATTERN = "(?m)^\\d+:[\\w,?]+:(?:/[\\w.]+)?(?:/docker[-/])(/?(?:docker/)?(?<containerId>\\p{XDigit}{12,}))+(?:\\.scope)?$";

    private final Launcher launcher;
    private final @CheckForNull Node node;
    private final @CheckForNull String toolName;

    public DockerClient(@Nonnull Launcher launcher, @CheckForNull Node node, @CheckForNull String toolName) {
        this.launcher = launcher;
        this.node = node;
        this.toolName = toolName;
    }

    /**
     * Run a docker image.
     *
     * @param launchEnv Docker client launch environment.
     * @param image The image name.
     * @param args Any additional arguments for the {@code docker run} command.
     * @param workdir The working directory in the container, or {@code null} for default.
     * @param volumes Volumes to be bound. Supply an empty list if no volumes are to be bound.
     * @param volumesFromContainers Mounts all volumes from the given containers.
     * @param containerEnv Environment variables to set in container.
     * @param user The <strong>uid:gid</strong> to execute the container command as. Use {@link #whoAmI()}.
     * @param entrypoint The command to execute in the image container being run.
     * @return The container ID.
     */
    public String run(@Nonnull EnvVars launchEnv, @Nonnull String image, @CheckForNull String args, @CheckForNull String workdir, @Nonnull Map<String, String> volumes, @Nonnull Collection<String> volumesFromContainers, @Nonnull EnvVars containerEnv, @Nonnull String user, @Nonnull String entrypoint) throws IOException, InterruptedException {
        ArgumentListBuilder argb = new ArgumentListBuilder();

        argb.add("run", "-t", "-d", "-u", user);
        if (args != null) {
            argb.addTokenized(args);
        }
        
        if (workdir != null) {
            argb.add("-w", workdir);
        }
        for (Map.Entry<String, String> volume : volumes.entrySet()) {
            argb.add("-v", volume.getKey() + ":" + volume.getValue() + ":rw,z");
        }
        for (String containerId : volumesFromContainers) {
            argb.add("--volumes-from", containerId);
        }
        for (Map.Entry<String, String> variable : containerEnv.entrySet()) {
            argb.add("-e");
            argb.addMasked(variable.getKey()+"="+variable.getValue());
        }
        argb.add("--entrypoint").add(entrypoint).add(image);

        LaunchResult result = launch(launchEnv, false, null, argb);
        if (result.getStatus() == 0) {
            return result.getOut();
        } else {
            throw new IOException(String.format("Failed to run image '%s'. Error: %s", image, result.getErr()));
        }
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
        LaunchResult result = launch(launchEnv, false, "stop", "--time=1", containerId);
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
     * @return The inspected field value. Null if the command failed
     */
    public @CheckForNull String inspect(@Nonnull EnvVars launchEnv, @Nonnull String objectId, @Nonnull String fieldPath) throws IOException, InterruptedException {
        LaunchResult result = launch(launchEnv, true, "inspect", "-f", String.format("{{%s}}", fieldPath), objectId);
        if (result.getStatus() == 0) {
            return result.getOut();
        } else {
            return null;
        }
    }
    
    /**
     * Inspect a docker image/container.
     * @param launchEnv Docker client launch environment.
     * @param objectId The image/container ID.
     * @param fieldPath The data path of the data required e.g. {@code .NetworkSettings.IPAddress}.
     * @return The inspected field value. May be an empty string
     * @throws IOException Execution error. Also fails if cannot retrieve the requested field from the request
     * @throws InterruptedException Interrupted
     * @since 1.1
     */
    public @Nonnull String inspectRequiredField(@Nonnull EnvVars launchEnv, @Nonnull String objectId, 
            @Nonnull String fieldPath) throws IOException, InterruptedException {
        final String fieldValue = inspect(launchEnv, objectId, fieldPath);
        if (fieldValue == null) {
            throw new IOException("Cannot retrieve " + fieldPath + " from 'docker inspect " + objectId + "'");
        }
        return fieldValue;
    }
    
    private @CheckForNull Date getCreatedDate(@Nonnull EnvVars launchEnv, @Nonnull String objectId) throws IOException, InterruptedException {
        String createdString = inspect(launchEnv, objectId, "json .Created");
        if (createdString == null) {
            return null;
        }
        // TODO Currently truncating. Find out how to specify last part for parsing (TZ etc)
        String s = createdString.substring(1, DOCKER_DATE_TIME_FORMAT.length() - 1);
        try {
            return new SimpleDateFormat(DOCKER_DATE_TIME_FORMAT).parse(s);
        } catch (ParseException e) {
            throw new IOException(String.format("Error parsing created date '%s' for object '%s'.", s, objectId), e);
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
        result.setStatus(procStarter.quiet(quiet).cmds(args).envs(launchEnv).stdout(out).stderr(err).start().joinWithTimeout(CLIENT_TIMEOUT, TimeUnit.SECONDS, launcher.getListener()));
        final String charsetName = Charset.defaultCharset().name();
        result.setOut(out.toString(charsetName));
        result.setErr(err.toString(charsetName));
        return result;
    }

    /**
     * Who is executing this {@link DockerClient} instance.
     *
     * @return a {@link String} containing the <strong>uid:gid</strong>.
     */
    public String whoAmI() throws IOException, InterruptedException {
        ByteArrayOutputStream userId = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-u").quiet(true).stdout(userId).start().joinWithTimeout(CLIENT_TIMEOUT, TimeUnit.SECONDS, launcher.getListener());

        ByteArrayOutputStream groupId = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-g").quiet(true).stdout(groupId).start().joinWithTimeout(CLIENT_TIMEOUT, TimeUnit.SECONDS, launcher.getListener());

        final String charsetName = Charset.defaultCharset().name();
        return String.format("%s:%s", userId.toString(charsetName).trim(), groupId.toString(charsetName).trim());

    }

    /**
     * Checks if this {@link DockerClient} instance is running inside a container and returns the id of the container
     * if so.
     *
     * @return an optional string containing the <strong>container id</strong>, or <strong>absent</strong> if
     * it isn't containerized.
     * @see <a href="http://stackoverflow.com/a/25729598/12916">Discussion</a>
     */
    public Optional<String> getContainerIdIfContainerized() throws IOException, InterruptedException {
        final Pattern pattern = Pattern.compile(CGROUP_MATCHER_PATTERN);
        if (node == null) {
            return Optional.absent();
        }
        FilePath cgroupFile = node.createPath("/proc/self/cgroup");
        if (cgroupFile == null || !cgroupFile.exists()) {
            return Optional.absent();
        }
        String cgroup = cgroupFile.readToString();
        Matcher matcher = pattern.matcher(cgroup);
        return matcher.find() ? Optional.of(matcher.group(matcher.groupCount())) : Optional.<String>absent();
    }

    public ContainerRecord getContainerRecord(@Nonnull EnvVars launchEnv, String containerId) throws IOException, InterruptedException {
        String host = inspectRequiredField(launchEnv, containerId, ".Config.Hostname");
        String containerName = inspectRequiredField(launchEnv, containerId, ".Name");
        Date created = getCreatedDate(launchEnv, containerId);
        String image = inspectRequiredField(launchEnv, containerId, ".Image");

        // TODO get tags and add for ContainerRecord
        return new ContainerRecord(host, containerId, image, containerName,
                (created != null ? created.getTime() : 0L), 
                Collections.<String,String>emptyMap());
    }

    /**
     * Inspect the mounts of a container.
     * These might have been declared {@code VOLUME}s, or mounts defined via {@code --volume}.
     * @param launchEnv Docker client launch environment.
     * @param containerID The container ID.
     * @return a list of filesystem paths inside the container
     * @throws IOException Execution error. Also fails if cannot retrieve the requested field from the request
     * @throws InterruptedException Interrupted
     */
    public List<String> getVolumes(@Nonnull EnvVars launchEnv, String containerID) throws IOException, InterruptedException {
        LaunchResult result = launch(launchEnv, true, "inspect", "-f", "{{range.Mounts}}{{.Destination}}\n{{end}}", containerID);
        if (result.getStatus() != 0) {
            return Collections.emptyList();
        }

        String volumes = result.getOut();
        if (volumes.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(volumes.split("\\n"));
    }
}
