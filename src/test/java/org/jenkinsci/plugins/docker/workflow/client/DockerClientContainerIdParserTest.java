/*
 * The MIT License
 *
 * Copyright (c) 2026, contributors.
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
package org.jenkinsci.plugins.docker.workflow.client;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class DockerClientContainerIdParserTest {

    @Test
    public void parsesSingleLineContainerId() throws Exception {
        String output = "ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c\n";
        Assert.assertEquals(
            "ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c",
            DockerClient.normalizeContainerIdOutput(output));
    }

    @Test
    public void parsesContainerIdWhenWarningIsAppendedOnFollowingLine() throws Exception {
        String output =
            "ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c\n"
                + "Process leaked file descriptors. See https://www.jenkins.io/redirect/troubleshooting/process-leaked-file-descriptors for more information\n";
        Assert.assertEquals(
            "ee632b4c84ed5aaea15608d5180169dfc7eedeaf7021a6724232e61c1f4d5d4c",
            DockerClient.normalizeContainerIdOutput(output));
    }

    @Test
    public void rejectsOutputWithoutContainerId() {
        IOException thrown = Assert.assertThrows(
            IOException.class,
            () -> DockerClient.normalizeContainerIdOutput("Process leaked file descriptors\n"));
        Assert.assertTrue(thrown.getMessage().contains("Unable to parse container ID"));
    }
}
