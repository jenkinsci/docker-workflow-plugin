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
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public final class Dockerfile {

    private static final String ARG = "ARG";
    private static final String FROM = "FROM ";

    private FilePath dockerfilePath;
    private LinkedList<String> froms;
    private Map<String,String> args;

    @DataBoundConstructor public Dockerfile(FilePath dockerfilePath) throws IOException, InterruptedException {
        this.dockerfilePath = dockerfilePath;
        this.froms = new LinkedList<>();
        this.args = new HashMap<>();
        parse();
    }

    public LinkedList<String> getFroms() {
        return froms;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    private void parse() throws IOException, InterruptedException {
        // encoding probably irrelevant since image/tag names must be ASCII
        try (InputStream is = dockerfilePath.read();
             BufferedReader r = new BufferedReader(new InputStreamReader(is, "ISO-8859-1"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith(ARG)) {
                    String[] keyVal = parseDockerfileArg(line.substring(4));
                    args.put(keyVal[0], keyVal[1]);
                    continue;
                }

                if (line.startsWith(FROM)) {
                    froms.add(line.substring(5));
                    continue;
                }
            }
        }
    }

    protected String[] parseDockerfileArg(String argLine) {
        String[] keyValue = argLine.split("=", 2);
        String key = keyValue[0];
        String value = "";
        if (keyValue.length > 1) {
            value = keyValue[1];
        }
        return new String[]{key, value};
    }
}
