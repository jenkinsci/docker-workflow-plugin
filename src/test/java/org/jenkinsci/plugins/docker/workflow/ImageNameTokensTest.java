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

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ImageNameTokensTest {
    
    @Test
    public void test_no_tag() {
        ImageNameTokens name = new ImageNameTokens("busybox");

        Assert.assertEquals("busybox", name.userAndRepo);
        Assert.assertEquals("latest", name.tag);
    }
    
    @Test
    public void test_empty_tag() {
        ImageNameTokens name = new ImageNameTokens("busybox:");

        Assert.assertEquals("busybox", name.userAndRepo);
        Assert.assertEquals("latest", name.tag);
    }
    
    @Test
    public void test_with_tag() {
        ImageNameTokens name = new ImageNameTokens("busybox:staging");

        Assert.assertEquals("busybox", name.userAndRepo);
        Assert.assertEquals("staging", name.tag);

        name = new ImageNameTokens("spring-petclinic:1");

        Assert.assertEquals("spring-petclinic", name.userAndRepo);
        Assert.assertEquals("1", name.tag);

        name = new ImageNameTokens("examplecorp/spring-petclinic:1");

        Assert.assertEquals("examplecorp/spring-petclinic", name.userAndRepo);
        Assert.assertEquals("1", name.tag);
    }

    @Test
    public void test_with_registry_and_tag() {
        ImageNameTokens name = new ImageNameTokens("docker.io/busybox:staging");

        Assert.assertEquals("docker.io", name.registry);
        Assert.assertEquals("busybox", name.userAndRepo);
        Assert.assertEquals("staging", name.tag);

        name = new ImageNameTokens("privateregistry:123/spring-petclinic:1");

        Assert.assertEquals("privateregistry:123", name.registry);
        Assert.assertEquals("spring-petclinic", name.userAndRepo);
        Assert.assertEquals("1", name.tag);

        name = new ImageNameTokens("another.registry-corp.net/example.corp/spring-petclinic:1");

        Assert.assertEquals("another.registry-corp.net", name.registry);
        Assert.assertEquals("example.corp/spring-petclinic", name.userAndRepo);
        Assert.assertEquals("1", name.tag);

        name = new ImageNameTokens("localhost/spring-petclinic:1");

        Assert.assertEquals("localhost", name.registry);
        Assert.assertEquals("spring-petclinic", name.userAndRepo);
        Assert.assertEquals("1", name.tag);
    }
}
