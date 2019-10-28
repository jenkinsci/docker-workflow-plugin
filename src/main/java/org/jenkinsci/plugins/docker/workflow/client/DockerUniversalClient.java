package org.jenkinsci.plugins.docker.workflow.client;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DockerUniversalClient {

    private static final Logger LOGGER = Logger.getLogger(DockerUniversalClient.class.getName());

    private DockerClientConfig clientConfig;
    private DockerClient client;

    public DockerUniversalClient(String dockerHost) {
        clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .build();
        client = DockerClientBuilder.getInstance(clientConfig).build();
    }

    public DockerClient getClient() {
        return client;
    }

    public CreateContainerResponse runContainer(String imageName, Map<String, String> volumes, String workingDirectory,
                                                List<String> volumesFromContainer, List<Container> volumesFrom) {

        CreateContainerResponse container = null;

        try {
            container = getClient().createContainerCmd(imageName)
                .withVolumes(convertVolumeMap(volumes))
                .withWorkingDir(workingDirectory)
                .withTty(true)
                .exec();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return container;

    }

    public void stopContainer(Container container) {
        getClient().stopContainerCmd(container.getId())
            .withTimeout(1)
            .exec();
    }

    public Container findContainer(String name) {
        Collection<String> containerNames = new ArrayList<>();
        containerNames.add(name);
        List<Container> containers = getClient().listContainersCmd().withNameFilter(containerNames).exec();
        return containers.get(0);
    }

    private List<Volume> convertVolumeMap(Map<String, String> volumeMap) {

        List<Volume> volumes = new ArrayList<>();

        for (Map.Entry<String,String> volume : volumeMap.entrySet()) {
            volumes.add(new Volume(String.format("%s:%s:%s", volume.getKey(), volume.getValue(), "rw,z")));
        }

        return volumes;

    }

    public String getVersion() {
        return getClient().versionCmd().exec().getVersion();
    }

}
