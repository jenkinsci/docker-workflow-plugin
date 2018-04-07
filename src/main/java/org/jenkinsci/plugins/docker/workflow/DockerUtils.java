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

import java.text.ParseException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.tools.ant.types.Commandline;

public final class DockerUtils {

    // marks the parameter format: --build-arg FOO=BAR
    private static final String BUILD_ARG_SEPARATE = "--build-arg";
    // marks the parameter format: --build-arg=FOO=BAR
    private static final String BUILD_ARG_CONCATENATED = "--build-arg=";

    private DockerUtils() {
        // utility class
    }

    public static Map<String, String> parseBuildArgs(final Dockerfile dockerfile, final String commandLineString) {
        // this also accounts for quote, escapes, ...
        Commandline commandLine = new Commandline(commandLineString);
        Map<String, String> result = new HashMap<>();
        if (dockerfile != null) {
            result.putAll(dockerfile.getArgs());
        }

        String[] arguments = commandLine.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            try {
                if (arg.equals(BUILD_ARG_SEPARATE)) {
                    String[] buildArgs = Arrays.copyOfRange(arguments, i, arguments.length);
                    SimpleEntry<String, String> parsed = parseBuildArgsArray(buildArgs);
                    result.put(parsed.getKey(), parsed.getValue());
                } else if (arg.startsWith(BUILD_ARG_CONCATENATED)) {
                    SimpleEntry<String, String> parsed = parseBuildArgsConcatenated(arg);
                    result.put(parsed.getKey(), parsed.getValue());
                }
            } catch (ParseException pe) {
                throw new IllegalArgumentException("Error parsing --build-arg: " + pe.getMessage() + ", cmdline: " + commandLineString);
            }
        }
        return result;
    }

    /**
     * Requires input: --build-arg=FOO=BAR
     */
    private static SimpleEntry<String, String> parseBuildArgsConcatenated(String concatenated) throws ParseException {
        String args = concatenated.substring(BUILD_ARG_CONCATENATED.length());

        SimpleEntry<String, String> result = splitArgNameValue(args);
        return result;
    }

    /**
     * Requires input of the format: ["--build-args", "FOO=BAR", ...]
     */
    private static SimpleEntry<String, String> parseBuildArgsArray(String arguments[]) throws ParseException {
        if (arguments.length < 2) {
			throw new ParseException("Missing parameter for --build-arg", 0);
		}
        String keyVal = arguments[1];

        SimpleEntry<String, String> result = splitArgNameValue(keyVal);
        return result;
    }

    private static SimpleEntry<String, String> splitArgNameValue(String nameValue) throws ParseException {
        String parts[] = nameValue.split("=", 2);
        if (parts.length != 2) {
			throw new ParseException("Illegal syntax for --build-arg " + nameValue +
                ": need further arguments with KEY=VALUE, e.g.: --build-arg FOO=Bar or --build-arg=FOO=Bar", 0);
		}
        String key = parts[0];
        String value = parts[1];

        return new SimpleEntry<>(key, value);
    }
}
