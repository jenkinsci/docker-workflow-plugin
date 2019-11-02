package org.jenkinsci.plugins.docker.workflow;

import hudson.*;
import hudson.model.Node;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;

import javax.annotation.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Decorator extends LauncherDecorator implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Decorator.class.getName());

    private static final long serialVersionUID = 1;
    private final String container;
    private final String[] envHost;
    private final String ws;
    private final @CheckForNull
    String toolName;
    private final boolean hasEnv;
    private final boolean hasWorkdir;

    Decorator(String container, EnvVars envHost, String ws, String toolName, VersionNumber dockerVersion) {
        this.container = container;
        this.envHost = Util.mapToEnv(envHost);
        this.ws = ws;
        this.toolName = toolName;
        this.hasEnv = dockerVersion != null && dockerVersion.compareTo(new VersionNumber("1.13.0")) >= 0;
        this.hasWorkdir = dockerVersion != null && dockerVersion.compareTo(new VersionNumber("17.12")) >= 0;
    }

    @Override public Launcher decorate(final Launcher launcher, final Node node) {
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
                                prefix.add(path);
                                masksPrefixList.add(false);
                            } else {
                                String safePath = path.replace("'", "'\"'\"'");
                                starter.cmds().addAll(0, Arrays.asList("sh", "-c", "cd '" + safePath + "'; exec \"$@\"", "--"));
                            }
                        }
                    }
                } // otherwise we are loading an old serialized Decorator
                Set<String> envReduced = new TreeSet<>(Arrays.asList(starter.envs()));
                envReduced.removeAll(Arrays.asList(envHost));

                // Remove PATH during `exec` as well.
                Iterator<String> it = envReduced.iterator();
                while (it.hasNext()) {
                    if (it.next().startsWith("PATH=")) {
                        it.remove();
                    }
                }
                LOGGER.log(Level.FINE, "(exec) reduced environment: {0}", envReduced);
                if (hasEnv) {
                    for (String e : envReduced) {
                        prefix.add("--env");
                        masksPrefixList.add(false);
                        prefix.add(e);
                        masksPrefixList.add(true);
                    }
                    prefix.add(container);
                    masksPrefixList.add(false);
                } else {
                    prefix.add(container);
                    masksPrefixList.add(false);
                    prefix.add("env");
                    masksPrefixList.add(false);
                    prefix.addAll(envReduced);
                    masksPrefixList.addAll(envReduced.stream()
                        .map(v -> true)
                        .collect(Collectors.toList()));
                }

                boolean[] originalMasks = starter.masks();
                if (originalMasks == null) {
                    originalMasks = new boolean[starter.cmds().size()];
                }

                // Adapted from decorateByPrefix:
                starter.cmds().addAll(0, prefix);

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
                if (getInner().launch().cmds(executable, "exec", container, "ps", "-A", "-o", "pid,command", "e").stdout(baos).quiet(true).start().joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, listener) != 0) {
                    throw new IOException("failed to run ps");
                }
                List<String> pids = new ArrayList<>();
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
