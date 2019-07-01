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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class DockerUtilsTest {

    @Rule public final ExpectedException exception = ExpectedException.none();

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void parseBuildArgs() throws IOException, InterruptedException {

        FilePath dockerfilePath = new FilePath(new File("src/test/resources/Dockerfile-withArgs"));
        Dockerfile dockerfile = new Dockerfile(dockerfilePath);

        final String imageToUpdate = "hello-world:latest";
        final String key = "IMAGE_TO_UPDATE";
        final String commangLine = "docker build -t hello-world --build-arg "+key+"="+imageToUpdate;
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(dockerfile, commangLine);

        Assert.assertThat(buildArgs.keySet(), IsCollectionWithSize.hasSize(1));
        Assert.assertThat(buildArgs.keySet(), IsCollectionContaining.hasItems(key));
        Assert.assertThat(buildArgs.get(key), IsEqual.equalTo(imageToUpdate));
    }

    @Test public void parseBuildArgsWithDefaults() throws IOException, InterruptedException {

        Dockerfile dockerfile = getDockerfileDefaultArgs();

        final String registry = "";
        final String key_registry = "REGISTRY_URL";
        final String key_tag = "TAG";
        final String commangLine = "docker build -t hello-world";
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(dockerfile, commangLine);

        Assert.assertThat(buildArgs.keySet(), IsCollectionWithSize.hasSize(2));
        Assert.assertThat(buildArgs.keySet(), IsCollectionContaining.hasItems(key_registry, key_tag));
        Assert.assertThat(buildArgs.get(key_registry), IsEqual.equalTo(registry));
        Assert.assertThat(buildArgs.get(key_tag), IsEqual.equalTo("latest"));
    }

    @Test public void parseBuildArgsOverridingDefaults() throws IOException, InterruptedException {

        Dockerfile dockerfile = getDockerfileDefaultArgs();

        final String registry = "http://private.registry:5000/";
        final String key_registry = "REGISTRY_URL";
        final String key_tag = "TAG";
        final String tag = "1.2.3";
        final String commangLine = "docker build -t hello-world --build-arg "+key_tag+"="+tag+
            " --build-arg "+key_registry+"="+registry;
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(dockerfile, commangLine);

        Assert.assertThat(buildArgs.keySet(), IsCollectionWithSize.hasSize(2));
        Assert.assertThat(buildArgs.keySet(), IsCollectionContaining.hasItems(key_registry, key_tag));
        Assert.assertThat(buildArgs.get(key_registry), IsEqual.equalTo(registry));
        Assert.assertThat(buildArgs.get(key_tag), IsEqual.equalTo(tag));
    }

    @Test public void parseBuildArgWithKeyAndEqual() throws IOException, InterruptedException {
        final String commangLine = "docker build -t hello-world --build-arg key=";

        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(null, commangLine);

        Assert.assertThat(buildArgs.keySet(), IsCollectionWithSize.hasSize(1));
        Assert.assertThat(buildArgs.keySet(), IsCollectionContaining.hasItems("key"));
        Assert.assertThat(buildArgs.get("key"), IsEqual.equalTo(""));
    }

    @Test public void parseInvalidBuildArg() throws IOException, InterruptedException {
        final String commangLine = "docker build -t hello-world --build-arg";

        exception.expect(IllegalArgumentException.class);
        DockerUtils.parseBuildArgs(null, commangLine);
    }

    @Test public void parseBuildArgWithKeyOnlyAndEnvVariable() throws IOException, InterruptedException {

        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();

        final String json_arg = "EXAMPLE_JSON";
        final String json_value = "{\"http-basic\":{\"repo.example.org\":{\"username\":\"some-api-user\",\"password\":\"generatedapipasswordorsomeothersecret\"}}}";

        env.put("EXAMPLE_JSON", json_value);
        j.jenkins.getGlobalNodeProperties().add(prop);

        final String commangLine = "docker build -t hello-world --build-arg "+json_arg;
        Map<String, String> buildArgs = DockerUtils.parseBuildArgs(null, commangLine, env);

        Assert.assertThat(buildArgs.keySet(), IsCollectionWithSize.hasSize(1));
        Assert.assertThat(buildArgs.keySet(), IsCollectionContaining.hasItems(json_arg));
    }

    @Test public void parseInvalidBuildArgWithKeyOnly() throws IOException, InterruptedException {
        final String commangLine = "docker build -t hello-world --build-arg key";

        exception.expect(IllegalArgumentException.class);
        DockerUtils.parseBuildArgs(null, commangLine);
    }

    private Dockerfile getDockerfileDefaultArgs() throws IOException, InterruptedException {
        FilePath dockerfilePath = new FilePath(new File("src/test/resources/Dockerfile-defaultArgs"));
        return new Dockerfile(dockerfilePath);
    }
}


