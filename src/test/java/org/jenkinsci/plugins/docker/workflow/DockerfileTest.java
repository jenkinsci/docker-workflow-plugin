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
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class DockerfileTest {

    private FilePath dockerfilePath;

    @Before public void setUp() {
        dockerfilePath = new FilePath(new File("src/test/resources/Dockerfile-defaultArgs"));
    }

    @Test public void parseDockerfile() throws IOException, InterruptedException {
        Dockerfile dockerfile = new Dockerfile(dockerfilePath);
        Assert.assertThat(dockerfile.getFroms(), IsCollectionWithSize.hasSize(1));
        Assert.assertThat(dockerfile.getFroms().getLast(), IsEqual.equalTo("${REGISTRY_URL}hello-world:${TAG}"));
        Assert.assertThat(dockerfile.getArgs().keySet(), IsCollectionWithSize.hasSize(2));
        Assert.assertThat(dockerfile.getArgs().keySet(), IsCollectionContaining.hasItems("REGISTRY_URL", "TAG"));
    }
    
    @Test public void parseDockerfileIgnoreCase() throws IOException, InterruptedException {
    	FilePath dockerFilePath = new FilePath(new File("src/test/resources/Dockerfile-ignoreCase")); 
    	Dockerfile dockerfile = new Dockerfile(dockerFilePath);
    	Assert.assertThat(dockerfile.getFroms().getLast(), IsEqual.equalTo("${REGISTRY_URL}hello-world:${TAG}"));
    	Assert.assertThat(dockerfile.getArgs().keySet(), IsCollectionWithSize.hasSize(2));
    }
}


