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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static com.google.common.collect.ImmutableMultimap.of;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class DockerUtilsTest {

    // parameters trigger the possible valid(!) combinations of:
    //
    // Param0: should --build-arg and Param-Name be separated by '=' or by ' '?
    // examples:
    // --build-arg FOO=BAR
    // --build-arg=FOO=BAR
    //
    // Param1: should an empty parameter (i.e.: key without value) have the '=' suffix?
    // examples
    // --build-arg FOO
    // --build-arg FOO=
    @Parameters(name = "{0}/{1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { '=', true },
            { '=', false },
            { ' ', true },
            { ' ', false },
        });
    }

    @Parameter()
    public char buildArgsSeparator;

    @Parameter(1)
    public boolean argValuePrefix;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    // given
    private Dockerfile dockerfile = null;
    private Multimap<String, String> buildArgs = null;
    private String commandLine = null;

    // when
    private Map<String, String> parsedArgs = null;

    @Test
    public void parseBuildArgs() {
        givenDockerfile("src/test/resources/Dockerfile-withArgs");
        givenBuildArgs(of("IMAGE_TO_UPDATE", "hello-world:latest"));
        givenCommandLine(withBuildArgsBySeparator());

        whenCommandLineIsParsed();

        thenParsedArgsContainExactly(buildArgs);
    }

    @Test
    public void parseBuildArgsWithDefaults() {
        givenDockerfileDefaultArgs();
        givenBuildArgs(of());
        givenCommandLine(emptyList());

        whenCommandLineIsParsed();

        // these are the defaults defined in the Dockerfile
        thenParsedArgsContainExactly
            (of("REGISTRY_URL", "",
                "TAG", "latest"));
    }

    @Test
    public void parseBuildArgsOverridingDefaults() {
        givenDockerfileDefaultArgs();
        givenBuildArgs(
            of("TAG", "1.2.3",
                "REGISTRY_URL", "http://private.registry:5000/"));
        givenCommandLine(withBuildArgsBySeparator());

        whenCommandLineIsParsed();

        thenParsedArgsContainExactly(buildArgs);
    }

    @Test
    public void parseBuildArgWithKeyAndEqual() {
        givenNoDockerfile();
        givenBuildArgs(of("key", ""));
        givenCommandLine(withBuildArgsBySeparator());

        whenCommandLineIsParsed();

        thenParsedArgsContainExactly(of("key", ""));
    }

    @Test
    public void parseInvalidBuildArg() {
        final String commandLine = "docker build -t hello-world --build-arg";

        exception.expect(IllegalArgumentException.class);
        DockerUtils.parseBuildArgs(null, commandLine);
    }

    private void givenNoDockerfile() {
        dockerfile = null;
    }

    private void givenDockerfile(String dockerFilePath) {
        FilePath dockerfilePath = new FilePath(new File(dockerFilePath));
        try {
            dockerfile = new Dockerfile(dockerfilePath);
        } catch (Exception e) {
            throw new IllegalStateException("Dockerfile not found: " + dockerFilePath);
        }
    }

    private void givenDockerfileDefaultArgs() {
        givenDockerfile("src/test/resources/Dockerfile-defaultArgs");
    }

    private void givenBuildArgs(Multimap<String, String> args) {
        buildArgs = ImmutableMultimap.copyOf(args);
    }

    private List<String> withBuildArgsBySeparator() {
        return buildArgs.entries().stream()
            .map(param -> createBuildArgParam(buildArgsSeparator, argValuePrefix, param))
            .collect(Collectors.toList());
    }

    private void givenCommandLine(List<String> buildArgs) {
        StringBuilder cmd = new StringBuilder("docker build -t hello-world");
        for (String arg : buildArgs) {
            cmd.append(" ").append(arg);
        }
        this.commandLine = cmd.toString();
    }

    private void whenCommandLineIsParsed() {
        parsedArgs = DockerUtils.parseBuildArgs(dockerfile, commandLine);
    }

    private void thenParsedArgsContainExactly(Multimap<String, String> expected) {
        assertThat(parsedArgs.entrySet(), everyItem(isIn(expected.entries())));
        assertThat(expected.entries(), everyItem(isIn(parsedArgs.entrySet())));
    }

    private String createBuildArgParam(char buildArgSeparator, boolean emptyValuePrefixEquals,
        Entry<String, String> param) {
        String result = " --build-arg" + buildArgSeparator + joinValue(emptyValuePrefixEquals, param);
        return result;
    }

    private String joinValue(boolean emptyValuePrefixEquals, Entry<String, String> param) {
        if (isNotBlank(param.getValue())) {
            return param.getKey() + "=" + param.getValue();
        }
        return param.getKey() + (emptyValuePrefixEquals ? "=" : "");
    }
}


