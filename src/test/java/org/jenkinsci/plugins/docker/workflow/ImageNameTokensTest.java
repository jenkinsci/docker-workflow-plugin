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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
class ImageNameTokensTest {

    @Test
    void test_no_tag() {
        ImageNameTokens name = new ImageNameTokens("busybox");

        assertEquals("busybox", name.userAndRepo);
        assertEquals("latest", name.tag);
    }

    @Test
    void test_empty_tag() {
        ImageNameTokens name = new ImageNameTokens("busybox:");

        assertEquals("busybox", name.userAndRepo);
        assertEquals("latest", name.tag);
    }

    @Test
    void test_with_tag() {
        ImageNameTokens name = new ImageNameTokens("busybox:staging");

        assertEquals("busybox", name.userAndRepo);
        assertEquals("staging", name.tag);

        name = new ImageNameTokens("spring-petclinic:1");

        assertEquals("spring-petclinic", name.userAndRepo);
        assertEquals("1", name.tag);

        name = new ImageNameTokens("examplecorp/spring-petclinic:1");

        assertEquals("examplecorp/spring-petclinic", name.userAndRepo);
        assertEquals("1", name.tag);
    }
}
