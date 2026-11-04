package org.jenkinsci.plugins.docker.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * The {@code docker exec} command line {@link WithContainerStep}'s launcher builds, without a container engine.
 */
public class WithContainerStepDecoratorTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    private static final String WS = "/ws";
    private static final String DIR = "/only-in-container";

    @Test public void podmanUsesWorkdir() throws Exception {
        Launched l = launch(new VersionNumber("4.9.3"), true, DIR);
        assertEquals(List.of("docker", "exec", "--workdir", DIR, "c1", "pwd"), withoutEnv(l.cmds));
        assertEquals(WS, l.pwd);
    }

    @Test public void podmanUsesEnv() throws Exception {
        assertThat(launch(new VersionNumber("4.9.3"), true, DIR).cmds, hasItem("--env"));
    }

    @Test public void podmanBefore1UsesCd() throws Exception {
        Launched l = launch(new VersionNumber("0.12.1"), true, DIR);
        assertThat(l.cmds, not(hasItem("--workdir")));
        assertEquals(List.of("sh", "-c", "cd '" + DIR + "'; exec \"$@\"", "--", "pwd"), l.cmds.subList(l.cmds.indexOf("c1") + 1, l.cmds.size()));
        assertEquals(WS, l.pwd);
    }

    @Test public void dockerUsesWorkdirFrom17_12() throws Exception {
        Launched l = launch(new VersionNumber("17.12.0"), false, DIR);
        assertEquals(List.of("docker", "exec", "--workdir", DIR, "c1", "pwd"), withoutEnv(l.cmds));
        assertEquals(WS, l.pwd);
    }

    @Test public void dockerBefore17_12UsesCd() throws Exception {
        Launched l = launch(new VersionNumber("17.11.0"), false, DIR);
        assertThat(l.cmds, not(hasItem("--workdir")));
        assertThat(l.cmds.subList(l.cmds.indexOf("c1") + 1, l.cmds.size()), contains("sh", "-c", "cd '" + DIR + "'; exec \"$@\"", "--", "pwd"));
        assertEquals(WS, l.pwd);
    }

    @Test public void workspaceItselfNeedsNothing() throws Exception {
        Launched l = launch(new VersionNumber("4.9.3"), true, WS);
        assertEquals(List.of("docker", "exec", "c1", "pwd"), withoutEnv(l.cmds));
        assertEquals(WS, l.pwd);
    }

    private static final class Launched {
        List<String> cmds;
        String pwd;
    }

    private Launched launch(VersionNumber version, boolean podman, String dir) throws Exception {
        Launched launched = new Launched();
        Launcher recorder = new Launcher.DummyLauncher(StreamTaskListener.fromStdout()) {
            @Override public Proc launch(ProcStarter starter) {
                launched.cmds = new ArrayList<>(starter.cmds());
                launched.pwd = starter.pwd() == null ? null : starter.pwd().getRemote();
                return new Proc() { // DummyLauncher's own launch throws
                    @Override public boolean isAlive() { return false; }
                    @Override public void kill() {}
                    @Override public int join() { return 0; }
                    @Override public InputStream getStdout() { return null; }
                    @Override public InputStream getStderr() { return null; }
                    @Override public OutputStream getStdin() { return null; }
                };
            }
            @Override public boolean isUnix() {
                return true;
            }
        };
        WithContainerStep.Decorator decorator = new WithContainerStep.Decorator("c1", new EnvVars(), WS, null, version, podman);
        decorator.decorate(recorder, r.jenkins).launch().cmds("pwd").pwd(new FilePath(new File(dir))).envs("FOO=bar").join();
        return launched;
    }

    // --env pairs depend on the agent's environment; the rest of the command line does not
    private static List<String> withoutEnv(List<String> cmds) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).equals("--env")) {
                i++;
            } else {
                out.add(cmds.get(i));
            }
        }
        return out;
    }
}
