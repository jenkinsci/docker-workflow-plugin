package org.jenkinsci.plugins.docker.workflow.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.base.Optional;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

public class ContainerizedCGroupV2Test {
    private FilePath     mountInfo;
    private DockerClient dockerClient;
    
    ContainerizedCGroupV2Test() {
        
        mountInfo = mock(FilePath.class);
        
        Node node = mock(Node.class);
        when(node.createPath("/proc/1/mountinfo")).thenReturn(mountInfo);
        
        TaskListener taskListener = StreamTaskListener.fromStderr();
        Launcher     launcher     = new Launcher.LocalLauncher(taskListener);
        
        dockerClient = new DockerClient(launcher, node, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"org/jenkinsci/plugins/docker/workflow/client/mountinfo_containerized_docker",
                            "org/jenkinsci/plugins/docker/workflow/client/mountinfo_containerized_podman"})
    public void test_is_containerized_cgroupV2(String mountInfoPath) throws IOException, InterruptedException {
        
        when(mountInfo.exists()).thenReturn(true);
        
        when(mountInfo.read()).thenReturn(
            getClass().getClassLoader().getResourceAsStream(mountInfoPath));        
        Optional<String> result = dockerClient.getContainerIdIfContainerized();
        
        assertEquals("32199af8e73b51d2b9f7cc0cf2c2bb4ef1792c54a656f1c4da53858698396fd6", result.get());
    }
    
    @Test
    public void test_is_not_containerized_cgroupV2() throws IOException, InterruptedException {
        
        when(mountInfo.exists()).thenReturn(true);
        
        when(mountInfo.read()).thenReturn(
            getClass().getClassLoader().getResourceAsStream("org/jenkinsci/plugins/docker/workflow/client/mountinfo_not_containerized"));        
        Optional<String> result = dockerClient.getContainerIdIfContainerized();
        
        assertEquals(Optional.absent(), result);
    }
    
    @Test
    public void test_is_not_cgroupV2() throws IOException, InterruptedException {
        
        when(mountInfo.exists()).thenReturn(false);
             
        Optional<String> result = dockerClient.getContainerIdIfContainerized();
        
        assertEquals(Optional.absent(), result);
    }
}




























