Docker image for Docker Pipeline demo
=====================================
This image contains a "Docker Pipeline" Job that demonstrates Jenkins Pipeline integration
with Docker via [Docker Pipeline](https://wiki.jenkins-ci.org/display/JENKINS/Docker+Pipeline+Plugin) plugin.

```
docker run --rm -p 127.0.0.1:8080:8080 -v $(which docker):/usr/bin/docker -v /var/run/docker.sock:/var/run/docker.sock --group-add=$(stat -c %g /var/run/docker.sock) jenkinsci/docker-workflow-demo
```

The "Docker Pipeline" Job simply does the following:

1. Gets the Spring Pet Clinic demonstration application code from GitHub.
1. Builds the Pet Clinic application in a Docker container.
1. Builds a runnable Pet Clinic application Docker image.
1. Runs a Pet Clinic app container (from the Pet Clinic application Docker image) + a second maven3 container that runs automated tests against the Pet Clinic app container.
  * The 2 containers are linked, allowing the test container to fire requests at the Pet Clinic app container.

The "Docker Pipeline" Job demonstrates how to use the `docker` DSL:

1. Use `docker.image` to define a DSL `Image` object (not to be confused with `build`) that can then be used to perform operations on a Docker image:
  * use `Image.inside` to run a Docker container and execute commands in it. The build workspace is mounted as the working directory in the container.
  * use `Image.run` to run a Docker container in detached mode, returning a DSL `Container` object that can be later used to stop the container (via `Container.stop`).
1. Use `docker.build` to build a Docker image from a `Dockerfile`, returning a DSL `Image` object that can then be used to perform operations on that image (as above). 
  
The `docker` DSL supports some additional capabilities not shown in the "Docker Pipeline" Job:
  
1. Use the `docker.withRegistry` and `docker.withServer` to register endpoints for the Docker registry and host to be used when executing docker commands.
  * `docker.withRegistry(<registryUrl>, <registryCredentialsId>)`
  * `docker.withServer(<serverUri>, <serverCredentialsId>)` 
1. Use the `Image.pull` to pull Docker image layers into the Docker host cache.
1. Use the `Image.push` to push a Docker image to the associated Docker Registry. See `docker.withRegistry` above. 

The image needs to run Docker commands, so it assumes that your Docker daemon is listening to `/var/run/docker.sock` ([discussion](https://github.com/docker/docker/issues/1143)). This is not “Docker-in-Docker”; the container only runs the CLI and connects back to the host to start sister containers. The `run` target also makes reference to file paths on the Docker host, assuming they are where you are running that command, so this target *cannot work* on boot2docker. There may be some way to run this demo using boot2docker; if so, please contribute it.
