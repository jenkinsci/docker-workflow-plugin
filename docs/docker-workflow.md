# Docker Pipeline Plugin

## Introduction

Many organizations are using [Docker](https://www.docker.com) to unify their build and test environments across machines and provide an efficient way to deploy applications into production. This plugin offers a convenient domain-specific language (DSL) for performing some of the most commonly needed Docker operations in a continuous-deployment pipeline from a Pipeline script.

The entry point for all of this plugin's functionality is a `docker` global variable, available without import to any flow script when the plugin is enabled. To get detailed information on available methods, open the _Pipeline Syntax_ available on any Pipeline Job page:

**Global variable reference**
![Global variable reference](dsl-help.png)

## Run Build Steps Inside Containers

It is commonplace for Jenkins projects to require a specific toolset or libraries to be available during a build. If many projects in a Jenkins installation have the same requirements, and there are few agents, it is not hard to just preconfigure those agents accordingly. In other cases it is feasible to keep such files in project source control. Finally, for some tools--especially those with a self-contained, platform-independent download, like Maven--it is possible to use the Jenkins tool installer system with the Pipeline `tool` step to retrieve tools on demand. However, many cases remain where these techniques are not practical.

For builds which can run on Linux, Docker provides an ideal solution to this problem. Each project need merely select an image containing all the tools and libraries it would need. (This might be a publicly available image like [maven](https://registry.hub.docker.com/_/maven/), or it might have been built by this or another Jenkins project.) Developers can also run build steps locally using an environment identical to that used by the Jenkins project.

There are two ways to run Jenkins build steps in such an image. One is to include a Java runtime and Jenkins agent JAR file inside the image, and add a _Docker_ cloud using the Docker plugin. Then the entire agent runs wholly inside the image, so any builds tied to that cloud label can assume a particular environment.

For cases where you do not want to "pollute" the image with Java and the Jenkins agent, or just want a simpler and more flexible setup, this plugin provides a way to run build steps inside an arbitrary image. In the simplest case, just select an `image` and run your whole build `inside` it:

```groovy
docker.image('maven:3.3.3-jdk-8').inside {
  git '…your-sources…'
  sh 'mvn -B clean install'
}
```

The above is a complete Pipeline script. `inside` will:

1. Automatically grab an agent and a workspace (no extra `node` block is required).
2. Pull the requested image to the Docker server (if not already cached).
3. Start a container running that image.
4. Mount the Jenkins workspace as a "volume" inside the container, using the same file path.
5. Run your build steps. External processes like `sh` will be wrapped in `docker exec` so they are run inside the container. Other steps (such as test reporting) run unmodified: they can still access workspace files created by build steps.
6. At the end of the block, stop the container and discard any storage it consumed.
7. Record the fact that the build used the specified image. This unlocks features in other Jenkins plugins: you can track all projects using an image, or configure this project to be triggered automatically when an updated image is pushed to the Docker registry. If you use the Docker Traceability plugin, you will be also able to see a history of the image deployments on Docker servers.

> **TIP:**
> If you are running a tool like Maven which has a large download cache, running each build inside its own image will mean that a large quantity of data is downloaded from the network on every build, which is usually undesirable. The easiest way to avoid this is to redirect the cache to the agent workspace, so that if you run another build on the same agent, it will run much more quickly. In the case of Maven:
>
> ```groovy
> docker.image('maven:3.3.3-jdk-8').inside {
>   git '…your-sources…'
>   writeFile file: 'settings.xml', text: "<settings><localRepository>${pwd()}/.m2repo</localRepository></settings>"
>   sh 'mvn -B -s settings.xml clean install'
> }
> ```
>
> (If you wanted to use a cache location elsewhere on the agent, you would need to pass an extra `--volume` option to `inside` so that the container could see that path.)
>
> Another solution is to pass an argument to `inside` to mount a sharable volume, such as `-v m2repo:/m2repo`, and use that path as the `localRepository`. Just beware that the default local repository management in Maven is not thread-safe for concurrent builds, and `install:install` could pollute the local repository across builds or even across jobs. The safest solution is to use a nearby repository mirror as a cache.

> **NOTE:**
> For `inside` to work, the Docker server and the Jenkins agent must use the same filesystem, so that the workspace can be mounted. The easiest way to ensure this is for the Docker server to be running on localhost (the same computer as the agent). Currently, neither the Jenkins plugin or the Docker CLI automatically detect that the server is running remotely; typical symptoms are negative exit codes or errors from nested `sh` commands, such as:
>
> ```bash
> cannot create /…@tmp/durable-…/pid: Directory nonexistent
> ```
>
> When Jenkins can detect that the agent is itself running inside a Docker container, it automatically passes the `--volumes-from` argument to the `inside` container, ensuring that it can share a workspace with the agent.

## Customize Agent Allocation

All DSL functions which run some Docker command automatically acquire an agent (executor) and a workspace if necessary. For more complex scripts which perform several commands using the DSL, you will typically want to run a block, or the whole script, on the _same_ agent and workspace. In that case just wrap the block in `node`, selecting a label if desired:

```groovy
node('linux') {
  def maven = docker.image('maven:latest')
  maven.pull() // make sure we have the latest available from Docker Hub
  maven.inside {
    // …as above
  }
}
```

Here we ensure that the same agent runs both `pull` and `inside`, so the local image cache update by the first step is seen by the second.

## Build and Publish Images

If your build needs to create a Docker image, use the `build` method, which takes an image name with an optional tag and creates it from a `Dockerfile`. This also returns a handle to the result image, so you can work with it further:

```groovy
node {
  git '…' // checks out Dockerfile & Makefile
  def myEnv = docker.build 'my-environment:snapshot'
  myEnv.inside {
    sh 'make test'
  }
}
```

Here the `build` method takes a `Dockerfile` in your source tree specifying a build environment (for example `RUN apt-get install -y libapr1-dev`). Then a `Makefile` in the same source tree describes how to build your actual project in that environment.

If you want to publish a newly created image to Docker Hub (or your own registry—discussed below), use `push`:

```groovy
node {
  git '…' // checks out Dockerfile and some project sources
  def newApp = docker.build "mycorp/myapp:${env.BUILD_TAG}"
  newApp.push()
}
```

Here we are giving the image a tag which identifies the Jenkins project and build number that created it. (See the documentation for the `env` global variable.) The image is pushed under this tag name to the registry.

To push an image into a staging or production environment, a common style is to update a predefined tag such as `latest` in the registry. In this case just specify the tag name:

```groovy
node {
  stage 'Building image'
  git '…'
  def newApp = docker.build "mycorp/myapp:${env.BUILD_TAG}"
  newApp.push() // record this snapshot (optional)
  stage 'Test image'
  // run some tests on it (see below), then if everything looks good:
  stage 'Approve image'
  newApp.push 'latest'
}
```

The `build` method records information in Jenkins tied to this project build: what image was built, and what image that was derived from (the `FROM` instruction at the top of your `Dockerfile`). Other plugins can then identify the build which created an image known to have been used by a downstream build, or deployed to a particular environment. You can also have this project be triggered when an update is pushed to the ancestor image (`FROM`) in a registry.

## Run and Test Containers

To run an image you built, or pulled from a registry, you can use the `run` method. This returns a handle to the running container. More safely, you can use the `withRun` method, which automatically stops the container at the end of a block:

```groovy
node {
  git '…'
  docker.image('mysql').withRun {c ->
    sh './test-with-local-db'
  }
}
```

The above simply starts a container running a test MySQL database and runs a regular build while that container is running. Unlike `inside`, shell steps inside the block are **not** run inside the container, but they could connect to it using a local TCP port for example.

You can also access the `id` of the running container, which is passed as an argument to the block, in case you need to do anything further with it:

```groovy
// …as above, but also dump logs before we finish:
sh "docker logs ${c.id}"
```

Like `inside`, `run` and `withRun` record the fact that the build used the specified image.

## Specify a Custom Registry and Server

So far we have assumed that you are using the public Docker Hub as the image registry, and connecting to a Docker server in the default location (typically a daemon running locally on a Linux agent). Either or both of these settings can be easily customized.

To select a custom registry, wrap build steps which need it in the `withRegistry` method on `docker` (inside `node` if you want to specify an agent explicitly). You should pass in a registry URL. If the registry requires authentication, you can add the ID of username/password credentials. (_Credentials_ link in the Jenkins index page or in a folder; when creating the credentials, use the _Advanced_ section to specify a memorable ID for use in your pipelines.)

```groovy
docker.withRegistry('https://docker.mycorp.com/', 'docker-login') {
  git '…'
  docker.build('myapp').push('latest')
}
```

The above builds an image from a `Dockerfile`, and then publishes it (under the `latest` tag) to a password-protected registry. There is no need to preconfigure authentication on the agent.

To select a non-default Docker server, such as for Docker Swarm, use the `withServer` method. You pass in a URI, and optionally the ID of _Docker Server Certificate Authentication_ credentials (which encode a client key and client/server certificates to support TLS).

```groovy
docker.withServer('tcp://swarm.mycorp.com:2376', 'swarm-certs') {
  docker.image('httpd').withRun('-p 8080:80') {c ->
    sh "curl -i http://${hostIp(c)}:8080/"
  }
}
def hostIp(container) {
  sh "docker inspect -f {{.Node.Ip}} ${container.id} > hostIp"
  readFile('hostIp').trim()
}
```

Note that you cannot use `inside` or `build` with a Swarm server, and some versions of Swarm do not support interacting with a custom registry either.

## Advanced Usage

If your script needs to run other Docker client commands or options not covered by the DSL, use a `sh` step. You can still take advantage of some DSL methods like `imageName` to prepend a registry ID:

```groovy
docker.withRegistry('https://docker.mycorp.com/') {
  def myImg = docker.image('myImg')
  // or docker.build, etc.
  sh "docker pull --all-tags ${myImg.imageName()}"
  // runs: docker pull --all-tags docker.mycorp.com/myImg
}
```

and you can be assured that the environment variables and files needed to connect to any custom registry and/or server will be prepared.

## Demonstrations

A Docker image is available for you to run which demonstrates a complete flow script which builds an application as an image, tests it from another container, and publishes the result to a private registry. Refer to the [Instructions on running this demonstration](https://github.com/jenkinsci/docker-workflow-plugin/tree/docker-workflow-1.12/demo) for details.
