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
package org.jenkinsci.plugins.docker.workflow

class Docker implements Serializable {

    private org.jenkinsci.plugins.workflow.cps.CpsScript script

    public Docker(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
    }

    public <V> V withRegistry(String url, String credentialsId = null, Closure<V> body) {
        node {
            script.withEnv(["DOCKER_REGISTRY_URL=${url}"]) {
                script.withDockerRegistry(registry: [url: url, credentialsId: credentialsId]) {
                    body()
                }
            }
        }
    }

    public <V> V withServer(String uri, String credentialsId = null, Closure<V> body) {
        node {
            script.withDockerServer(server: [uri: uri, credentialsId: credentialsId]) {
                body()
            }
        }
    }

    public <V> V withTool(String toolName, Closure<V> body) {
        node {
            script.withEnv(["PATH=${script.tool name: toolName, type: 'org.jenkinsci.plugins.docker.commons.tools.DockerTool'}/bin:${script.env.PATH}", "DOCKER_TOOL_NAME=${toolName}"]) {
                body()
            }
        }
    }

    private <V> V node(Closure<V> body) {
        if (script.env.NODE_NAME != null) {
            // Already inside a node block.
            body()
        } else {
            script.node {
                body()
            }
        }
    }

    public Image image(String id) {
        new Image(this, id)
    }

    public Image build(String image, String args = '.') {
        node {
            def parsedArgs = args.split(/ (?=([^"']*["'][^"']*["'])*[^"']*$)/)
            def dir = parsedArgs[-1] ?: '.'

            // Detect custom Dockerfile:
            def dockerfile = "${dir}/Dockerfile"
            for (int i=0; i<parsedArgs.length; i++) {
                if (parsedArgs[i] == '-f' && i < (parsedArgs.length - 1)) {
                    dockerfile = parsedArgs[i+1]
                    break
                }
            }

            script.sh "docker build -t ${image} ${args}"
            script.dockerFingerprintFrom dockerfile: dockerfile, image: image, toolName: script.env.DOCKER_TOOL_NAME
            this.image(image)
        }
    }

    public static class Image implements Serializable {

        private final Docker docker;
        public final String id;
        private ImageNameTokens parsedId;

        private Image(Docker docker, String id) {
            this.docker = docker
            this.id = id
            this.parsedId = new ImageNameTokens(id)
        }

        private String toQualifiedImageName(String imageName) {
            return new org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint(docker.script.env.DOCKER_REGISTRY_URL, null).imageName(imageName)
        }

        public String imageName() {
            return toQualifiedImageName(id)
        }
        
        public <V> V inside(String args = '', Closure<V> body) {
            docker.node {
                if (docker.script.sh(script: "docker inspect -f . ${id}", returnStatus: true) != 0) {
                    // Not yet present locally.
                    // withDockerContainer requires the image to be available locally, since its start phase is not a durable task.
                    pull()
                }
                docker.script.withDockerContainer(image: id, args: args, toolName: docker.script.env.DOCKER_TOOL_NAME) {
                    body()
                }
            }
        }

        public void pull() {
            docker.node {
                docker.script.sh "docker pull ${imageName()}"
            }
        }

        public Container run(String args = '', String command = "") {
            docker.node {
                def container = docker.script.sh(script: "docker run -d${args != '' ? ' ' + args : ''} ${id}${command != '' ? ' ' + command : ''}", returnStdout: true).trim()
                docker.script.dockerFingerprintRun containerId: container, toolName: docker.script.env.DOCKER_TOOL_NAME
                new Container(docker, container)
            }
        }

        public <V> V withRun(String args = '', Closure<V> body) {
            docker.node {
                Container c = run(args)
                try {
                    body.call(c)
                } finally {
                    c.stop()
                }
            }
        }

        public void tag(String tagName = parsedId.tag, boolean force = true) {
            docker.node {
                def taggedImageName = toQualifiedImageName(parsedId.userAndRepo + ':' + tagName)
                // TODO as of 1.10.0 --force is deprecated; for 1.12+ do not try it even once
                docker.script.sh "docker tag --force=${force} ${id} ${taggedImageName} || docker tag ${id} ${taggedImageName}"
                return taggedImageName;
            }
        }

        public void push(String tagName = parsedId.tag, boolean force = true) {
            docker.node {
                // The image may have already been tagged, so the tagging may be a no-op.
                // That's ok since tagging is cheap.
                def taggedImageName = tag(tagName, force)
                docker.script.sh "docker push ${taggedImageName}"
            }
        }

    }

    public static class Container implements Serializable {

        private final Docker docker;
        public final String id;

        private Container(Docker docker, String id) {
            this.docker = docker
            this.id = id
        }

        public void stop() {
            docker.script.sh "docker stop ${id} && docker rm -f ${id}"
        }

        public String port(int port) {
            docker.script.sh(script: "docker port ${id} ${port}", returnStdout: true).trim()
        }
    }

}
