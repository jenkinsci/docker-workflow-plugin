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

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.Serializable;

/**
 * Image name.
 * 
 * <p>
 * Not including the registry host name.
 * 
 * TODO: Should this be in docker-commons?
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ImageNameTokens implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public final String userAndRepo;
    public final String tag;

    public ImageNameTokens(@NonNull String name) {
        int tagIdx = name.lastIndexOf(':');
        if (tagIdx != -1) {
            this.userAndRepo = name.substring(0, tagIdx);
            if (tagIdx < name.length() - 1) {
                this.tag = name.substring(tagIdx + 1);
            } else {
                this.tag = "latest";
            }
        } else {
            this.userAndRepo = name;
            this.tag = "latest";
        }
    }
}
