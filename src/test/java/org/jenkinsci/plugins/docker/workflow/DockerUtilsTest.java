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
package org.jenkinsci.plugins.docker.workflow;

import hudson.FilePath;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerUtilsTest {

    @Test
    void parseBuildArgs() throws Exception {

        FilePath dockerfilePath = new FilePath(new File("src/test/resources/Dockerfile-withArgs"));
        Dockerfile dockerfile = new Dockerfile(dockerfilePath);

        final String imageToUpdate = "hello-world:latest";
        final String key = "IMAGE_TO_UPDATE";
        final String commandLine = "docker build -t hello-world --build-arg "+key+"="+imageToUpdate;
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(dockerfile, commandLine);

        assertThat(buildArgs.keySet(), hasSize(1));
        assertThat(buildArgs.keySet(), hasItems(key));
        assertThat(buildArgs.get(key), equalTo(imageToUpdate));
    }

    @Test
    void parseBuildArgsWithDefaults() throws Exception {

        Dockerfile dockerfile = getDockerfileDefaultArgs();

        final String registry = "";
        final String key_registry = "REGISTRY_URL";
        final String key_tag = "TAG";
        final String commandLine = "docker build -t hello-world";
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(dockerfile, commandLine);

        assertThat(buildArgs.keySet(), hasSize(2));
        assertThat(buildArgs.keySet(), hasItems(key_registry, key_tag));
        assertThat(buildArgs.get(key_registry), equalTo(registry));
        assertThat(buildArgs.get(key_tag), equalTo("latest"));
    }

    @Test
    void parseBuildArgsOverridingDefaults() throws Exception {

        Dockerfile dockerfile = getDockerfileDefaultArgs();

        final String registry = "http://private.registry:5000/";
        final String key_registry = "REGISTRY_URL";
        final String key_tag = "TAG";
        final String tag = "1.2.3";
        final String commandLine = "docker build -t hello-world --build-arg "+key_tag+"="+tag+
            " --build-arg "+key_registry+"="+registry;
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(dockerfile, commandLine);

        assertThat(buildArgs.keySet(), hasSize(2));
        assertThat(buildArgs.keySet(), hasItems(key_registry, key_tag));
        assertThat(buildArgs.get(key_registry), equalTo(registry));
        assertThat(buildArgs.get(key_tag), IsEqual.equalTo(tag));
    }

    @Test
    void parseBuildArgWithKeyAndEqual() {
        final String commandLine = "docker build -t hello-world --build-arg key=";

        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(null, commandLine);

        assertThat(buildArgs.keySet(), hasSize(1));
        assertThat(buildArgs.keySet(), hasItems("key"));
        assertThat(buildArgs.get("key"), equalTo(""));
    }

    @Test
    void parseInvalidBuildArg() {
        final String commandLine = "docker build -t hello-world --build-arg";

        assertThrows(IllegalArgumentException.class, () ->
            DockerUtils.parseBuildArgs(null, commandLine));
    }

    @Test
    void parseInvalidBuildArgWithKeyOnly() {
        final String commandLine = "docker build -t hello-world --build-arg key";

        assertThrows(IllegalArgumentException.class, () ->
            DockerUtils.parseBuildArgs(null, commandLine));
    }

    private Dockerfile getDockerfileDefaultArgs() throws Exception {
        FilePath dockerfilePath = new FilePath(new File("src/test/resources/Dockerfile-defaultArgs"));
        return new Dockerfile(dockerfilePath);
    }
}


