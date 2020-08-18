package org.jenkinsci.plugins.docker.workflow.client;

import com.google.common.base.Optional;
import hudson.FilePath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ControlGroup {

    /** hierarchy ID number */
    public final int id;

    /** set of subsystems bound to the hierarchy */
    public final String subsystems;

    /** control group in the hierarchy to which the process belongs */
    public final String group;

    private ControlGroup(String line) throws NumberFormatException, IndexOutOfBoundsException {
        String[] fields = line.split(":");
        id = Integer.parseInt(fields[0]);
        subsystems = fields[1];
        group = fields[2];
    }


    public static Optional<String> getContainerId(FilePath procfile) throws IOException, InterruptedException {
        return getContainerId(new InputStreamReader(procfile.read(), StandardCharsets.UTF_8));
    }

    static Optional<String> getContainerId(Reader reader) throws IOException {
        try (BufferedReader r = new BufferedReader(reader)) {
            String line;
            while ((line = r.readLine()) != null) {
                final ControlGroup cgroup = new ControlGroup(line);
                final String containerId = cgroup.getContainerId();
                if (containerId != null) return Optional.of(containerId);
            }
        }
        return Optional.absent();
    }

    public String getContainerId() throws IOException {
        if (group.contains("/docker/")) {
            // 4:cpuset:/system.slice/docker-3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b.scope
            // 2:cpu:/docker/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
            // 2:cpu:/docker-ce/docker/7cacbc548047c130ae50653548f037285806d49c0c4c1543925cffb8873ed213
            // 10:cpu,cpuacct:/docker/a9f3c3932cd81c4a74cc7e0a18c3300255159512f1d000545c42895adaf68932/docker/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
            // 3:cpu:/docker/4193df6bcf5fce75f3fc77f303b2ac06fb664adeb269b959b7ae17b3f8dcf329/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
            int i = group.lastIndexOf('/');
            if (group.length() < i+1+64) throw new IOException("Unexpected cgroup syntax "+group);
            return group.substring(i+1, i+1+64);
        }
        if (group.startsWith("/ecs/")) {
            // 7:cpu:/ecs/0410eff2-7e59-4111-823e-1e0d98ef7f30/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
            int i = group.lastIndexOf('/');
            if (group.length() < i+1+64) throw new IOException("Unexpected cgroup syntax "+group);
            return group.substring(i+1, i+1+64);
        }
        if (group.contains("/docker-")) {
            // 8:cpuset:/kubepods.slice/kubepods-pod9c26dfb6_b9c9_11e7_bfb9_02c6c1fc4861.slice/docker-3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b.scope
            int i = group.lastIndexOf("/docker-");
            if (group.length() < i+8+64) throw new IOException("Unexpected cgroup syntax "+group);
            return group.substring(i+8, i+8+64);
        }
        if (group.startsWith("/kubepods/")) {
            // 8:cpuset:/kubepods/besteffort/pod60070ae4-c63a-11e7-92b3-0adc1ac11520/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b
            int i = group.lastIndexOf('/');
            if (group.length() < i+1+64) throw new IOException("Unexpected cgroup syntax "+group);
            return group.substring(i+1, i+1+64);
        }
        if (group.startsWith("/actions_job/")) {
            // 12:freezer:/actions_job/ddecc467e1fb3295425e663efb6531282c1c936f25a3eeb7bb64e7b0fc61a216
            int i = group.lastIndexOf('/');
            if (group.length() < i+1+64) throw new IOException("Unexpected cgroup syntax "+group);
            return group.substring(i+1, i+1+64);
        }

        return null;
    }

}
