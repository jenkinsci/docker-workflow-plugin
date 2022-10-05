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
package org.jenkinsci.plugins.docker.workflow.client;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Launch result.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class LaunchResult {

    private int status;
    private @NonNull String out;
    private @NonNull String err;

    LaunchResult() {
        this(0, "", "");
    }

    public LaunchResult(int status, String out, String err) {
        this.status = status;
        this.out = out.trim();
        this.err = err.trim();
    }
    
    public int getStatus() {
        return status;
    }

    LaunchResult setStatus(int status) {
        this.status = status;
        return this;
    }

    public @NonNull String getOut() {
        return out;
    }

    LaunchResult setOut(@NonNull String out) {
        this.out = out.trim();
        return this;
    }

    public @NonNull String getErr() {
        return err;
    }

    LaunchResult setErr(@NonNull String err) {
        this.err = err.trim();
        return this;
    }
}
