/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package org.jenkinsci.plugins.docker.workflow.declarative;

import hudson.Functions;
import hudson.model.Result;
import hudson.model.Slave;
import org.jenkinsci.plugins.docker.workflow.DockerTestUtil;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Adapted from {@link org.jenkinsci.plugins.pipeline.modeldefinition.BasicModelDefTest}.
 */
public class DockerBasicModelDefTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setNumExecutors(10);
        s.setLabelString("some-label docker");
    }

    @Test
    public void dockerGlobalVariable() throws Exception {
        DockerTestUtil.assumeDocker();

        expect("org/jenkinsci/plugins/docker/workflow/declarative/dockerGlobalVariable")
                .logContains("[Pipeline] { (foo)", "image: ubuntu")
                .go();
    }

    @Issue("JENKINS-40226")
    @Test
    public void failureBeforeStages() throws Exception {
        Assume.assumeFalse("Fails using the version of Git installed on the Windows ACI agents on ci.jenkins.io", Functions.isWindows());
        // This should fail whether we've got Docker available or not. Hopefully.
        expect(Result.FAILURE, "org/jenkinsci/plugins/docker/workflow/declarative/failureBeforeStages")
                .logContains("Dockerfile failed")
                .logNotContains("This should never happen")
                .go();
    }

}
