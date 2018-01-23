package org.jenkinsci.plugins.docker.workflow.client;

import com.google.common.base.Optional;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ControlGroupTest {

    @Test
    public void test_cgroup_string_matching() throws IOException {

        final String[] possibleCgroupStrings = new String[] {
            "2:cpu:/docker/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b",
            "4:cpuset:/system.slice/docker-3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b.scope",
            "10:cpu,cpuacct:/docker/a9f3c3932cd81c4a74cc7e0a18c3300255159512f1d000545c42895adaf68932/docker/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b",
            "3:cpu:/docker/4193df6bcf5fce75f3fc77f303b2ac06fb664adeb269b959b7ae17b3f8dcf329/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b",
            "8:cpuset:/kubepods.slice/kubepods-pod9c26dfb6_b9c9_11e7_bfb9_02c6c1fc4861.slice/docker-3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b.scope",
            "8:cpuset:/kubepods/besteffort/pod60070ae4-c63a-11e7-92b3-0adc1ac11520/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b",
            "7:cpu:/ecs/0410eff2-7e59-4111-823e-1e0d98ef7f30/3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b"
        };

        for (final String possibleCgroupString : possibleCgroupStrings) {
            final Optional<String> containerId = ControlGroup.getContainerId(new StringReader(possibleCgroupString));

            Assert.assertTrue("pattern didn't match containerId " + possibleCgroupString, containerId.isPresent());
            Assert.assertEquals("3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b", containerId.get());
        }

    }
}
