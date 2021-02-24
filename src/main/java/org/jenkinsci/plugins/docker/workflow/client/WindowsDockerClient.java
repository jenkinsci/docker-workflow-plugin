package org.jenkinsci.plugins.docker.workflow.client;

import com.google.common.base.Optional;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.os.WindowsUtil;
import hudson.util.ArgumentListBuilder;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The type Windows docker client.
 */
public class WindowsDockerClient extends DockerClient {
    private static final Logger LOGGER = Logger.getLogger(WindowsDockerClient.class.getName());

    private final Launcher launcher;
    private final Node node;
    private boolean needToContainerizePath = false;
    private boolean isContainerUnix = false;

    /**
     * Instantiates a new Windows docker client.
     *
     * @param launcher the launcher
     * @param node     the node
     * @param toolName the tool name
     */
    public WindowsDockerClient(@Nonnull Launcher launcher, @CheckForNull Node node, @CheckForNull String toolName) {
        super(launcher, node, toolName);
        this.launcher = launcher;
        this.node = node;
    }

    @Override
    public String run(@Nonnull EnvVars launchEnv, @Nonnull String image, @CheckForNull String args, @CheckForNull String workdir, @Nonnull Map<String, String> volumes, @Nonnull Collection<String> volumesFromContainers, @Nonnull EnvVars containerEnv, @Nonnull String user, @Nonnull String... command) throws IOException, InterruptedException {
        ArgumentListBuilder argb = new ArgumentListBuilder("docker", "run", "-d", "-t");
        if (args != null) {
            argb.addTokenized(args);
        }

        if (workdir != null) {
            argb.add("-w", containerizePathIfNeeded(workdir));
        }
        for (Map.Entry<String, String> volume : volumes.entrySet()) {
            argb.add("-v", volume.getKey() + ":" + containerizePathIfNeeded(volume.getValue()));
        }
        for (String containerId : volumesFromContainers) {
            argb.add("--volumes-from", containerId);
        }
        for (Map.Entry<String, String> variable : containerEnv.entrySet()) {
            argb.add("-e");
            argb.addMasked(WindowsUtil.quoteArgument(variable.getKey() + "=" + variable.getValue()));
        }
        argb.add(image).add(command);

        LaunchResult result = launch(launchEnv, false, null, argb);
        if (result.getStatus() == 0) {
            return result.getOut();
        } else {
            throw new IOException(String.format("Failed to run image '%s'. Error: %s", image, result.getErr()));
        }
    }

    @Override
    public List<String> listProcess(@Nonnull EnvVars launchEnv, @Nonnull String containerId) throws IOException, InterruptedException {
        if (isContainerUnix) {
            return listProcessUnixContainer(launchEnv, containerId);
        }
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
                if (stringTokenizer.countTokens() < 1) {
                    throw new IOException("Unexpected `docker top` output : "+line);
                }

                processes.add(stringTokenizer.nextToken()); // COMMAND
            }
        }
        return processes;
    }

    @Override
    public Optional<String> getContainerIdIfContainerized() throws IOException, InterruptedException {
        if (node == null ||
            launch(new EnvVars(), true, null, "sc.exe", "query", "cexecsvc").getStatus() != 0) {
            return Optional.absent();
        }

        LaunchResult getComputerName = launch(new EnvVars(), true, null, "hostname");
        if(getComputerName.getStatus() != 0) {
            throw new IOException("Failed to get hostname.");
        }

        String shortID = getComputerName.getOut().toLowerCase();
        LaunchResult getLongIdResult = launch(new EnvVars(), true, null, "docker", "inspect", shortID, "--format={{.Id}}");
        if(getLongIdResult.getStatus() != 0) {
            LOGGER.log(Level.INFO, "Running inside of a container but cannot determine container ID from current environment.");
            return Optional.absent();
        }

        return Optional.of(getLongIdResult.getOut());
    }

    @Override
    public String whoAmI() throws IOException, InterruptedException {
        try (ByteArrayOutputStream userId = new ByteArrayOutputStream()) {
            launcher.launch().cmds("whoami").quiet(true).stdout(userId).start().joinWithTimeout(CLIENT_TIMEOUT, TimeUnit.SECONDS, launcher.getListener());
            return userId.toString(Charset.defaultCharset().name()).trim();
        }
    }

    private LaunchResult launch(EnvVars env, boolean quiet, FilePath workDir, String... args) throws IOException, InterruptedException {
        return launch(env, quiet, workDir, new ArgumentListBuilder(args));
    }
    private LaunchResult launch(EnvVars env, boolean quiet, FilePath workDir, ArgumentListBuilder argb) throws IOException, InterruptedException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Executing command \"{0}\"", argb);
        }

        Launcher.ProcStarter procStarter = launcher.launch();
        if (workDir != null) {
            procStarter.pwd(workDir);
        }

        LaunchResult result = new LaunchResult();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        result.setStatus(procStarter.quiet(quiet).cmds(argb).envs(env).stdout(out).stderr(err).start().joinWithTimeout(CLIENT_TIMEOUT, TimeUnit.SECONDS, launcher.getListener()));
        final String charsetName = Charset.defaultCharset().name();
        result.setOut(out.toString(charsetName));
        result.setErr(err.toString(charsetName));

        return result;
    }

    /**
     * List process unix container list.
     *
     * @param launchEnv   the launch env
     * @param containerId the container id
     * @return the list
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    public List<String> listProcessUnixContainer(@Nonnull EnvVars launchEnv, @Nonnull String containerId) throws IOException, InterruptedException {
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
                    throw new IOException("Unexpected `docker top` output : " + line);
                }
                stringTokenizer.nextToken(); // PID
                stringTokenizer.nextToken(); // USER
                stringTokenizer.nextToken(); // TIME
                processes.add(stringTokenizer.nextToken()); // COMMAND
            }
        }
        return processes;
    }

    /**
     * @return command to run as entry-point
     */
    @Override
    public String runCommand() {
        if (isContainerUnix) {
            return "cat";
        }
        return "cmd";
    }

    /**
     * @return boolean path need to be containerize
     */
    public boolean isNeedToContainerizePath() {
        return needToContainerizePath;
    }

    /**
     * @param needToContainerizePath the need to containerize path
     */
    public void setNeedToContainerizePath(boolean needToContainerizePath) {
        this.needToContainerizePath = needToContainerizePath;
    }


    /**
     * @return boolean container type (unix or windows)
     */
    public boolean isContainerUnix() {
        return isContainerUnix;
    }

    /**
     * @param containerUnix the container unix
     */
    public void setContainerUnix(boolean containerUnix) {
        isContainerUnix = containerUnix;
    }

    /**
     * Containerize path if needed string.
     *
     * @param path the path
     * @return the string
     */
    public String containerizePathIfNeeded(String path) {
        return containerizePathIfNeeded(path, null);
    }

    /**
     * Containerize path if needed string.
     *
     * @param path   the path
     * @param prefix the prefix
     * @return the string
     */
    public String containerizePathIfNeeded(String path, String prefix) {
        if (this.needToContainerizePath)
            return WindowsDockerClient.containerizePath(path, prefix);
        return path;
    }

    /**
     * Containerize path string.
     *
     * @param path   the path
     * @param prefix the prefix
     * @return the string
     */
    public static String containerizePath(String path, String prefix) {
        StringBuffer result = new StringBuffer();
        char[] pathChars = path.toCharArray();
        char[] prefixChars = (prefix == null) ? null : prefix.toCharArray();

        for (int i = 0; i < pathChars.length; i++) {
            char currentChar = pathChars[i];
            if (currentChar == ':' && i > 0 && i < pathChars.length - 1) {
                char previousChar = pathChars[i - 1];
                if ((previousChar >= 'a' && previousChar <= 'z') || (previousChar >= 'A' && previousChar <= 'Z')) {
                    char nextChar = pathChars[i + 1];
                    if (nextChar == '/' || nextChar == '\\') {
                        char nextNextChar = (i < pathChars.length - 2) ? pathChars[i + 2] : ' ';
                        if (nextNextChar != '/') {
                            if (prefix == null || checkPrefix(pathChars, i - 1, prefixChars)) {
                                result.setCharAt(i - 1, '/');
                                result.append(Character.toLowerCase(previousChar));
                                result.append('/');
                                i++;
                                i++;

                                boolean done = false;
                                for (; i < pathChars.length; i++) {
                                    currentChar = pathChars[i];
                                    switch (currentChar) {
                                        case '\\':
                                            result.append('/');
                                            break;

                                        case '?':
                                        case '<':
                                        case '>':
                                        case ':':
                                        case '*':
                                        case '|':
                                        case '"':
                                        case '\'':
                                            result.append(currentChar);
                                            done = true;
                                            break;

                                        default:
                                            result.append(currentChar);
                                            break;
                                    }

                                    if (done)
                                        break;
                                }

                                continue;
                            }
                        }
                    }
                }
            }

            result.append(currentChar);

        }
        return result.toString();

    }

    /**
     * @param pathChars
     * @param index
     * @param prefixChars
     * @return
     */
    private static boolean checkPrefix(char[] pathChars, int index, char[] prefixChars) {
        if (index + prefixChars.length > pathChars.length)
            return false;

        for (int i = 0; i < prefixChars.length; i++) {
            char pathChar = pathChars[index + i];
            if (pathChar == '\\')
                pathChar = '/';

            char prefixChar = prefixChars[i];
            if (prefixChar == '\\')
                prefixChar = '/';

            if (pathChar != prefixChar)
                return false;
        }

        return true;
    }
}
