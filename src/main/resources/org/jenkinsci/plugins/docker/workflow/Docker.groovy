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

/**
 * Do not copy this idiom unless you fully understand the consequences.
 */
class Docker implements Serializable {

    private org.jenkinsci.plugins.workflow.cps.CpsScript script

    public Docker(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
    }

    public <V> V withRegistry(String url, String credentialsId = null, Closure<V> body) {
        node {
            script.withEnv(["DOCKER_REGISTRY_URL=${url}"]) {
                script.withDockerRegistry(url: url, credentialsId: credentialsId, toolName: script.env.DOCKER_TOOL_NAME) {
                    body()
                }
            }
        }
    }

    public <V> V withServer(String uri, String credentialsId = null, Closure<V> body) {
        node {
            script.withDockerServer([uri: uri, credentialsId: credentialsId]) {
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

    String shell(boolean isUnix) {
        isUnix ? "sh" : "bat"
    }

    String asEnv(boolean isUnix, String var) {
        isUnix ? "\$${var}" : "%${var}%"
    }

    public Image build(String image, String args = '.') {
        check(image)
        node {
            def isUnix = script.isUnix()
            def commandLine = 'docker build -t "' + asEnv(isUnix, 'JD_IMAGE') + '" ' + args
            script.withEnv(["JD_IMAGE=${image}"]) {
                script."${shell(isUnix)}" commandLine
            }
            this.image(image)
        }
    }

    @com.cloudbees.groovy.cps.NonCPS
    private static void check(String id) {
        org.jenkinsci.plugins.docker.commons.credentials.ImageNameValidator.checkUserAndRepo(id)
    }

    public static class Image implements Serializable {

        private final Docker docker;
        public final String id;
        private ImageNameTokens parsedId;

        private Image(Docker docker, String id) {
            check(id)
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
                def toRun = imageName()
                def isUnix = docker.script.isUnix()
                docker.script.withEnv(["JD_ID=${id}", "JD_TO_RUN=${toRun}"]) {
                    if (toRun != id && docker.script."${docker.shell(isUnix)}"(script: 'docker inspect -f . "' + docker.asEnv(isUnix, 'JD_ID') + '"', returnStatus: true) == 0) {
                        // Can run it without registry prefix, because it was locally built.
                        toRun = id
                    } else {
                        if (docker.script."${docker.shell(isUnix)}"(script: 'docker inspect -f . "' + docker.asEnv(isUnix, 'JD_TO_RUN') + '"', returnStatus: true) != 0) {
                            // Not yet present locally.
                            // withDockerContainer requires the image to be available locally, since its start phase is not a durable task.
                            pull()
                        }
                    }
                }
                docker.script.withDockerContainer(image: toRun, args: args, toolName: docker.script.env.DOCKER_TOOL_NAME) {
                    body()
                }
            }
        }

        public void pull() {
            docker.node {
                def toPull = imageName()
                def isUnix = docker.script.isUnix()
                docker.script.withEnv(["JD_TO_PULL=${toPull}"]) {
                    docker.script."${docker.shell(isUnix)}" 'docker pull "' + docker.asEnv(isUnix, 'JD_TO_PULL') + '"'
                }
            }
        }

        public Container run(String args = '', String command = "") {
            docker.node {
                def isUnix = docker.script.isUnix()
                def container = docker.script."${docker.shell(isUnix)}"(script: "docker run -d${args != '' ? ' ' + args : ''} ${id}${command != '' ? ' ' + command : ''}", returnStdout: true).trim()
                new Container(docker, container, isUnix)
            }
        }

        public <V> V withRun(String args = '', String command = "", Closure<V> body) {
            docker.node {
                Container c = run(args, command)
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
                def isUnix = docker.script.isUnix()
                docker.script.withEnv(["JD_ID=${id}", "JD_TAGGED_IMAGE_NAME=${taggedImageName}"]) {
                    docker.script."${docker.shell(isUnix)}" 'docker tag "' + docker.asEnv(isUnix, 'JD_ID') + '" "' + docker.asEnv(isUnix, 'JD_TAGGED_IMAGE_NAME') + '"'
                }
                return taggedImageName;
            }
        }

        public void push(String tagName = parsedId.tag, boolean force = true) {
            docker.node {
                // The image may have already been tagged, so the tagging may be a no-op.
                // That's ok since tagging is cheap.
                def taggedImageName = tag(tagName, force)
                def isUnix = docker.script.isUnix()
                docker.script.withEnv(["JD_TAGGED_IMAGE_NAME=${taggedImageName}"]) {
                    docker.script."${docker.shell(isUnix)}" 'docker push "' + docker.asEnv(isUnix, 'JD_TAGGED_IMAGE_NAME') + '"'
                }
            }
        }

    }

    public static class Container implements Serializable {

        private final Docker docker;
        private final boolean isUnix;
        public final String id;

        private Container(Docker docker, String id, boolean isUnix) {
            this.docker = docker
            this.id = id
            this.isUnix = isUnix;
        }

        public void stop() {
            docker.script.withEnv(["JD_ID=${id}"]) {
                docker.script."${docker.shell(isUnix)}" 'docker stop "' + docker.asEnv(isUnix,'JD_ID') + '" && docker rm -f "' + docker.asEnv(isUnix, 'JD_ID') + '"'
            }
        }

        public String port(int port) {
            docker.script.withEnv(["JD_ID=${id}", "JD_PORT=${port}"]) {
                docker.script."${docker.shell(isUnix)}"(script: 'docker port "' + docker.asEnv(isUnix, 'JD_ID') + '" "' + docker.asEnv(isUnix, 'JD_PORT') + '"', returnStdout: true).trim()
            }
        }
    }

}
