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

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.types.Commandline;

public final class DockerUtils {
    private DockerUtils() {
        // utility class
    }

    public static Map<String, String> parseBuildArgs(String commandLine) {
        // this also accounts for quote, escapes, ...
        Commandline parsed = new Commandline(commandLine);
        Map<String, String> result = new HashMap<>();

        String[] arguments = parsed.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (arg.equals("--build-arg")) {
                if (arguments.length < i + 1) {
                    throw new IllegalArgumentException("Missing parameter for --build-arg: " + commandLine);
                }
                String keyVal = arguments[i+1];

                String parts[] = keyVal.split("=", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Illegal syntax for --build-arg " + keyVal + ", need KEY=VALUE");
                }
                String key = parts[0];
                String value = parts[1];

                result.put(key, value);
            }
        }
        return result;
    }

    public static SimpleEntry<String, String> splitArgs(String argString) {
        //TODO: support complex single/double quotation marks
        Pattern p = Pattern.compile("^['\"]?(\\w+)=(.*?)['\"]?$");

        Matcher matcher = p.matcher(argString.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Illegal --build-arg parameter syntax: " + argString);
        }

        String key = matcher.group(1);
        String value = matcher.group(2);

        return new SimpleEntry<>(key, value);
    }

}
