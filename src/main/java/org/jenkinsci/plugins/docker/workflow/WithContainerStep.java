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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.os.WindowsUtil;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.WorkspaceList;
import hudson.util.VersionNumber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.docker.workflow.client.WindowsDockerClient;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WithContainerStep extends Step {
    
    private static final Logger LOGGER = Logger.getLogger(WithContainerStep.class.getName());
    private final @NonNull String image;
    private String args;
    private String toolName;

    @DataBoundConstructor public WithContainerStep(@NonNull String image) {
        this.image = image;
    }

    @NonNull
    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setArgs(String args) {
        this.args = Util.fixEmpty(args);
    }

    public String getArgs() {
        return args;
    }

    public String getToolName() {
        return toolName;
    }

    @DataBoundSetter public void setToolName(String toolName) {
        this.toolName = Util.fixEmpty(toolName);
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static void destroy(String container, @NonNull Launcher launcher, Node node, EnvVars launcherEnv, String toolName) throws Exception {
        new DockerClient(launcher, node, toolName).stop(launcherEnv, container);
    }

    public static class Execution extends GeneralNonBlockingStepExecution {
        private static final long serialVersionUID = 1;
        private transient final WithContainerStep step;
        private String container;
        private String toolName;

        Execution(WithContainerStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override public boolean start() throws Exception {
            run(this::doStart);
            return false;
        }

        private void doStart() throws Exception {
            StepContext context = getContext();
            Launcher launcher = context.get(Launcher.class);
            TaskListener listener = context.get(TaskListener.class);
            FilePath workspace = context.get(FilePath.class);
            EnvVars env = context.get(EnvVars.class);
            Computer computer = context.get(Computer.class);
            Node node = context.get(Node.class);
            Run<?, ?> run = context.get(Run.class);

            EnvVars envReduced = new EnvVars(env);
            EnvVars envHost = computer.getEnvironment();
            envReduced.entrySet().removeAll(envHost.entrySet());

            // Remove PATH during cat.
            envReduced.remove("PATH");
            envReduced.remove("");

            LOGGER.log(Level.FINE, "reduced environment: {0}", envReduced);
            workspace.mkdirs(); // otherwise it may be owned by root when created for -v
            String ws = getPath(launcher, workspace);
            toolName = step.toolName;
            DockerClient dockerClient = launcher.isUnix()
                ? new DockerClient(launcher, node, toolName)
                : new WindowsDockerClient(launcher, node, toolName);

            VersionNumber dockerVersion = dockerClient.version();
            if (dockerVersion != null) {
                if (dockerVersion.isOlderThan(new VersionNumber("1.7"))) {
                    throw new AbortException("The docker version is less than v1.7. Pipeline functions requiring 'docker exec' (e.g. 'docker.inside') or SELinux labeling will not work.");
                } else if (dockerVersion.isOlderThan(new VersionNumber("1.8"))) {
                    listener.error("The docker version is less than v1.8. Running a 'docker.inside' from inside a container will not work.");
                } else if (dockerVersion.isOlderThan(new VersionNumber("1.13"))) {
                    if (!launcher.isUnix())
                        throw new AbortException("The docker version is less than v1.13. Running a 'docker.inside' from inside a Windows container will not work.");
                }
            } else {
                listener.error("Failed to parse docker version. Please note there is a minimum docker version requirement of v1.7.");
            }

            FilePath tempDir = tempDir(workspace);
            tempDir.mkdirs();
            String tmp = getPath(launcher, tempDir);

            Map<String, String> volumes = new LinkedHashMap<String, String>();
            Collection<String> volumesFromContainers = new LinkedHashSet<String>();
            Optional<String> containerId = dockerClient.getContainerIdIfContainerized();
            if (containerId.isPresent()) {
                listener.getLogger().println(node.getDisplayName() + " seems to be running inside container " + containerId.get());
                final Collection<String> mountedVolumes = dockerClient.getVolumes(env, containerId.get());
                final String[] dirs = {ws, tmp};
                for (String dir : dirs) {
                    // check if there is any volume which contains the directory
                    boolean found = false;
                    for (String vol : mountedVolumes) {
                        boolean dirStartsWithVol = launcher.isUnix()
                            ? dir.startsWith(vol) // Linux
                            : dir.toLowerCase().startsWith(vol.toLowerCase()); // Windows

                        if (dirStartsWithVol) {
                            volumesFromContainers.add(containerId.get());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        listener.getLogger().println("but " + dir + " could not be found among " + mountedVolumes);
                        volumes.put(dir, dir);
                    }
                }
            } else {
                listener.getLogger().println(node.getDisplayName() + " does not seem to be running inside a container");
                volumes.put(ws, ws);
                volumes.put(tmp, tmp);
            }

            String command = launcher.isUnix() ? "cat" : "cmd.exe";
            container = dockerClient.run(env, step.image, step.args, ws, volumes, volumesFromContainers, envReduced, dockerClient.whoAmI(), /* expected to hang until killed */ command);
            final List<String> ps = dockerClient.listProcess(env, container);
            if (!ps.contains(command)) {
                listener.error(
                    "The container started but didn't run the expected command. " +
                        "Please double check your ENTRYPOINT does execute the command passed as docker run argument, " +
                        "as required by official docker images (see https://github.com/docker-library/official-images#consistency for entrypoint consistency requirements).\n" +
                        "Alternatively you can force image entrypoint to be disabled by adding option `--entrypoint=''`.");
            }

            ImageAction.add(step.image, run);
            context.newBodyInvoker().
                    withContext(BodyInvoker.mergeLauncherDecorators(context.get(LauncherDecorator.class), new Decorator(container, envHost, ws, toolName, dockerVersion))).
                    withCallback(new Callback()).
                    start();
        }

        private String getPath(Launcher launcher, FilePath filePath)
            throws IOException, InterruptedException {
            if (launcher.isUnix()) {
                return filePath.getRemote();
            } else {
                return filePath.toURI().getPath().substring(1).replace("\\", "/");
            }
        }

        // TODO use 1.652 use WorkspaceList.tempDir
        private static FilePath tempDir(FilePath ws) {
            return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
        }

        @Override public void stop(@NonNull Throwable cause) throws Exception {
            super.stop(cause);
            destroyContainerAsync(cause);
        }

        private void destroyContainer() throws Exception {
            if (container == null) {
                return;
            }
            LOGGER.log(Level.FINE, "stopping container {0}", container);
            StepContext context = getContext();
            destroy(container, context.get(Launcher.class), context.get(Node.class), context.get(EnvVars.class), toolName);
        }

        private void destroyContainerAsync(Throwable cause) {
            if (container == null) {
                return;
            }
            Authentication auth = Jenkins.getAuthentication2();
            Computer.threadPoolForRemoting.submit(() -> {
                try (ACLContext ignored = ACL.as2(auth)) {
                    destroyContainer();
                } catch (Exception x) {
                    if (cause != null) {
                        cause.addSuppressed(x);
                    }
                    LOGGER.log(Level.WARNING, "failed to stop container " + container, x);
                }
            });
        }

        private class Callback extends BodyExecutionCallback {
            private static final long serialVersionUID = 1;

            @Override public void onSuccess(StepContext context, Object result) {
                run(() -> {
                    try {
                        destroyContainer();
                    } catch (Exception x) {
                        context.onFailure(x);
                        return;
                    }
                    context.onSuccess(result);
                });
            }

            @Override public void onFailure(StepContext context, Throwable t) {
                run(() -> {
                    try {
                        destroyContainer();
                    } catch (Exception x) {
                        t.addSuppressed(x);
                    }
                    context.onFailure(t);
                });
            }
        }

    }

    private static class Decorator extends LauncherDecorator implements Serializable {

        private static final long serialVersionUID = 1;
        private final String container;
        private final String[] envHost;
        private final String ws;
        private final @CheckForNull String toolName;
        private final boolean hasEnv;
        private final boolean hasWorkdir;

        Decorator(String container, EnvVars envHost, String ws, @CheckForNull String toolName, VersionNumber dockerVersion) {
            this.container = container;
            this.envHost = Util.mapToEnv(envHost);
            this.ws = ws;
            this.toolName = toolName;
            this.hasEnv = dockerVersion != null && dockerVersion.compareTo(new VersionNumber("1.13.0")) >= 0;
            this.hasWorkdir = dockerVersion != null && dockerVersion.compareTo(new VersionNumber("17.12")) >= 0;
        }

        @NonNull
        @Override public Launcher decorate(@NonNull final Launcher launcher, @NonNull final Node node) {
            return new Launcher.DecoratedLauncher(launcher) {
                @Override public Proc launch(Launcher.ProcStarter starter) throws IOException {
                    String executable;
                    try {
                        executable = getExecutable();
                    } catch (InterruptedException x) {
                        throw new IOException(x);
                    }
                    List<String> prefix = new ArrayList<>(Arrays.asList(executable, "exec"));
                    List<Boolean> masksPrefixList = new ArrayList<>(Arrays.asList(false, false));
                    if (ws != null) {
                        FilePath cwd = starter.pwd();
                        if (cwd != null) {
                            String path = cwd.getRemote();
                            if (!path.equals(ws)) {
                                if (hasWorkdir) {
                                    prefix.add("--workdir");
                                    masksPrefixList.add(false);
                                    if (super.isUnix()) {
                                        prefix.add(path);
                                    } else {
                                        prefix.add(WindowsUtil.quoteArgument(path));
                                    }
                                    masksPrefixList.add(false);
                                } else {
                                    String safePath = path.replace("'", "'\"'\"'");
                                    starter.cmds().addAll(0, Arrays.asList("sh", "-c", "cd '" + safePath + "'; exec \"$@\"", "--"));
                                }
                            }
                        }
                    } // otherwise we are loading an old serialized Decorator
                    Set<String> envReduced = new TreeSet<String>(Arrays.asList(starter.envs()));
                    envReduced.removeAll(Arrays.asList(envHost));

                    // Remove PATH or invalid variable during `exec` as well.
                    Iterator<String> it = envReduced.iterator();
                    while (it.hasNext()) {
                        final String envVar = it.next();
                        if (envVar.startsWith("PATH=") || "=".equals(envVar.trim())) {
                            it.remove();
                        }
                    }
                    LOGGER.log(Level.FINE, "(exec) reduced environment: {0}", envReduced);
                    if (hasEnv) {
                        for (String e : envReduced) {
                            prefix.add("--env");
                            masksPrefixList.add(false);
                            if (super.isUnix()) {
                                prefix.add(e);
                            } else {
                                prefix.add(WindowsUtil.quoteArgument(e));
                            }
                            masksPrefixList.add(true);
                        }
                        prefix.add(container);
                        masksPrefixList.add(false);
                    } else {
                        prefix.add(container);
                        masksPrefixList.add(false);
                        prefix.add("env");
                        masksPrefixList.add(false);
                        if (super.isUnix()) {
                            prefix.addAll(envReduced);
                        } else {
                            prefix.addAll(envReduced.stream()
                                                    .map(v -> WindowsUtil.quoteArgument(v))
                                                    .collect(Collectors.toList()));
                        }
                        masksPrefixList.addAll(envReduced.stream()
                                                         .map(v -> true)
                                                         .collect(Collectors.toList()));
                    }

                    boolean[] originalMasks = starter.masks();
                    if (originalMasks == null) {
                        originalMasks = new boolean[starter.cmds().size()];
                    }

                    List<String> cmds = new ArrayList<>();
                    cmds.addAll(prefix);

                    if (!super.isUnix() && starter.cmds().size() >= 3 && "cmd".equals(starter.cmds().get(0)) && "/c".equalsIgnoreCase(starter.cmds().get(1))) {
                        // JENKINS-75102 Docker exec on Windows processes character escaping differently.
                        // Modify launch to work with special characters in a way that docker exec can handle.
                        cmds.addAll(starter.cmds().subList(0, 2));
                        cmds.add("call");
                        cmds.addAll(starter.cmds().subList(2, starter.cmds().size()).stream()
                            .map(cmd -> cmd.replaceAll("\"\"(.*)\"\"", "\"$1\"")).collect(Collectors.toList()));
                    } else {
                        cmds.addAll(starter.cmds());
                    }
                    starter.cmds(cmds);

                    boolean[] masks = new boolean[originalMasks.length + prefix.size()];
                    boolean[] masksPrefix = new boolean[masksPrefixList.size()];
                    for (int i = 0; i < masksPrefix.length; i++) {
                        masksPrefix[i] = masksPrefixList.get(i);
                    }
                    System.arraycopy(masksPrefix, 0, masks, 0, masksPrefix.length);
                    System.arraycopy(originalMasks, 0, masks, prefix.size(), originalMasks.length);
                    starter.masks(masks);

                    return super.launch(starter);
                }
                @Override public void kill(Map<String,String> modelEnvVars) throws IOException, InterruptedException {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    String executable = getExecutable();
                    // For this code to be able to successfully kill processes:
                    // 1: The image must contain `ps`, which notably excludes slim variants of Debian.
                    // 2. The version of `ps` being used must support `-o command`. Busybox does not (so no Alpine!).
                    //    `-o args` seems to be a more portable equivalent, but even then, see JENKINS-52881.
                    // 3. The version of `ps` being used must support the `e` output modifier to include environment
                    //    variables in the command's output. Otherwise, the output only includes `jsc=<cookie here>` and
                    //    `JENKINS_SERVER_COOKIE=$jsc`, but the output must include `JENKINS_SERVER_COOKIE=<cookie here>
                    //    for this code to work.
                    // In practice, this means that this code only works with images that include procps.
                    if (getInner().launch().cmds(executable, "exec", container, "ps", "-A", "-o", "pid,command", "e").stdout(baos).quiet(true).start().joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, listener) != 0) {
                        throw new IOException("failed to run ps");
                    }
                    List<String> pids = new ArrayList<String>();
                    LINE: for (String line : baos.toString(Charset.defaultCharset().name()).split("\n")) {
                        for (Map.Entry<String,String> entry : modelEnvVars.entrySet()) {
                            // TODO this is imprecise: false positive when argv happens to match KEY=value even if environment does not. Cf. trick in BourneShellScript.
                            if (!line.contains(entry.getKey() + "=" + entry.getValue())) {
                                continue LINE;
                            }
                        }
                        line = line.trim();
                        int spc = line.indexOf(' ');
                        if (spc == -1) {
                            continue;
                        }
                        pids.add(line.substring(0, spc));
                    }
                    LOGGER.log(Level.FINE, "killing {0}", pids);
                    if (!pids.isEmpty()) {
                        List<String> cmds = new ArrayList<>(Arrays.asList(executable, "exec", container, "kill"));
                        cmds.addAll(pids);
                        if (getInner().launch().cmds(cmds).quiet(true).start().joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, listener) != 0) {
                            throw new IOException("failed to run kill");
                        }
                    }
                }
                private String getExecutable() throws IOException, InterruptedException {
                    EnvVars env = new EnvVars();
                    for (String pair : envHost) {
                        env.addLine(pair);
                    }
                    return DockerTool.getExecutable(toolName, node, getListener(), env);
                }
            };
        }

    }

    @Extension public static class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "withDockerContainer";
        }

        @NonNull
        @Override public String getDisplayName() {
            return "Run build steps inside a Docker container";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public boolean isAdvanced() {
            return true;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, TaskListener.class, FilePath.class, EnvVars.class, Computer.class, Node.class, Run.class);
        }

    }

}
