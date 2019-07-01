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

import java.util.HashMap;
import java.util.Map;

import org.apache.tools.ant.types.Commandline;

public final class DockerUtils {
    private DockerUtils() {
        // utility class
    }

    public static Map<String, String> parseBuildArgs(final Dockerfile dockerfile, final String commandLine) {
        EnvVars emptyEnvVars = new EnvVars();
        Map<String, String> result = parseBuildArgs(dockerfile, commandLine, emptyEnvVars);
        return result;
    }

    public static Map<String, String> parseBuildArgs(final Dockerfile dockerfile, final String commandLine, final EnvVars env) {
        // this also accounts for quote, escapes, ...
        Commandline parsed = new Commandline(commandLine);
        Map<String, String> result = new HashMap<>();
        if (dockerfile != null) {
            result.putAll(dockerfile.getArgs());
        }

        String[] arguments = parsed.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (arg.equals("--build-arg")) {
                if (arguments.length <= i + 1) {
                    throw new IllegalArgumentException("Missing parameter for --build-arg: " + commandLine);
                }
                String keyVal = arguments[i+1];

                String parts[] = keyVal.split("=", 2);
                if (parts.length != 2) {
                    // check if we have an environment variable with the very same name
                    if (env.get(parts[0]) != null) {
                        String key = parts[0];
                        String value = "";
                        result.put(key, value);
                    } else {
                        throw new IllegalArgumentException("Illegal syntax for --build-arg " + keyVal + ", either need KEY=VALUE or a corrsponding variable KEY on environment.");
                    }
                } else {
                    String key = parts[0];
                    String value = parts[1];
                    result.put(key, value);
                }
            }
        }
        return result;
    }
}
