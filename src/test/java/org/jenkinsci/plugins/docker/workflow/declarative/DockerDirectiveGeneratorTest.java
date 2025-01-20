/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package org.jenkinsci.plugins.docker.workflow.declarative;

import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Describable;
import jakarta.servlet.ServletRequest;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.OptionalJobProperty;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.AbstractDirective;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.AgentDirective;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.DirectiveGenerator;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;

/**
 * Adapted from {@link org.jenkinsci.plugins.pipeline.modeldefinition.generator.DirectiveGeneratorTest}.
 */
public class DockerDirectiveGeneratorTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @BeforeClass
    public static void setUp() throws Exception {
        ToolInstallations.configureMaven3();
    }

    @Test
    public void simpleAgentDocker() throws Exception {
        AgentDirective agent = new AgentDirective(new DockerPipeline("some-image"));
        assertGenerateDirective(agent, "agent {\n" +
                "  docker 'some-image'\n" +
                "}");
    }

    @Test
    public void fullAgentDocker() throws Exception {
        DockerPipeline dockerPipeline = new DockerPipeline("some-image");
        dockerPipeline.setAlwaysPull(true);
        dockerPipeline.setArgs("--some-arg");
        dockerPipeline.setCustomWorkspace("some/path");
        dockerPipeline.setLabel("some-label");
        dockerPipeline.setRegistryCredentialsId("some-cred-id");
        dockerPipeline.setReuseNode(true);
        dockerPipeline.setRegistryUrl("http://some.where");
        AgentDirective agent = new AgentDirective(dockerPipeline);

        assertGenerateDirective(agent, "agent {\n" +
                "  docker {\n" +
                "    alwaysPull true\n" +
                "    args '--some-arg'\n" +
                "    customWorkspace 'some/path'\n" +
                "    image 'some-image'\n" +
                "    label 'some-label'\n" +
                "    registryCredentialsId 'some-cred-id'\n" +
                "    registryUrl 'http://some.where'\n" +
                "    reuseNode true\n" +
                "  }\n" +
                "}");
    }

    @Test
    public void simpleAgentDockerfile() throws Exception {
        AgentDirective agent = new AgentDirective(new DockerPipelineFromDockerfile());

        assertGenerateDirective(agent, "agent {\n" +
                "  dockerfile true\n" +
                "}");
    }

    @Test
    public void fullAgentDockerfile() throws Exception {
        DockerPipelineFromDockerfile dp = new DockerPipelineFromDockerfile();
        dp.setAdditionalBuildArgs("--additional-arg");
        dp.setDir("some-sub/dir");
        dp.setFilename("NotDockerfile");
        dp.setArgs("--some-arg");
        dp.setCustomWorkspace("/custom/workspace");
        dp.setLabel("some-label");
        AgentDirective agent = new AgentDirective(dp);

        assertGenerateDirective(agent, "agent {\n" +
                "  dockerfile {\n" +
                "    additionalBuildArgs '--additional-arg'\n" +
                "    args '--some-arg'\n" +
                "    customWorkspace '/custom/workspace'\n" +
                "    dir 'some-sub/dir'\n" +
                "    filename 'NotDockerfile'\n" +
                "    label 'some-label'\n" +
                "  }\n" +
                "}");
    }

    private void assertGenerateDirective(@NonNull AbstractDirective desc, @NonNull String responseText) throws Exception {
        // First, make sure the expected response text actually matches the toGroovy for the directive.
        assertEquals(desc.toGroovy(true), responseText);

        // Then submit the form with the appropriate JSON (we generate it from the directive, but it matches the form JSON exactly)
        JenkinsRule.WebClient wc = r.createWebClient();
        WebRequest wrs = new WebRequest(new URL(r.getURL(), DirectiveGenerator.GENERATE_URL), HttpMethod.POST);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("json", staplerJsonForDescr(desc).toString()));
        // WebClient.addCrumb *replaces* rather than *adds*:
        params.add(new NameValuePair(r.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(), r.jenkins.getCrumbIssuer().getCrumb((ServletRequest) null)));
        wrs.setRequestParameters(params);
        WebResponse response = wc.getPage(wrs).getWebResponse();
        assertEquals("text/plain", response.getContentType());
        assertEquals(responseText, response.getContentAsString().trim());
    }

    private Object getValue(DescribableParameter p, Object o) {
        Class<?> ownerClass = o.getClass();
        try {
            try {
                return ownerClass.getField(p.getName()).get(o);
            } catch (NoSuchFieldException x) {
                // OK, check for getter instead
            }
            try {
                return ownerClass.getMethod("get" + p.getCapitalizedName()).invoke(o);
            } catch (NoSuchMethodException x) {
                // one more check
            }
            try {
                return ownerClass.getMethod("is" + p.getCapitalizedName()).invoke(o);
            } catch (NoSuchMethodException x) {
                throw new UnsupportedOperationException("no public field ‘" + p.getName() + "’ (or getter method) found in " + ownerClass);
            }
        } catch (UnsupportedOperationException x) {
            throw x;
        } catch (Exception x) {
            throw new UnsupportedOperationException(x);
        }
    }

    private JSONObject staplerJsonForDescr(Describable d) {
        DescribableModel<?> m = DescribableModel.of(d.getClass());

        JSONObject o = new JSONObject();
        o.accumulate("stapler-class", d.getClass().getName());
        o.accumulate("$class", d.getClass().getName());
        if (d instanceof OptionalJobProperty) {
            o.accumulate("specified", true);
        }
        for (DescribableParameter param : m.getParameters()) {
            Object v = getValue(param, d);
            if (v != null) {
                if (v instanceof Describable) {
                    o.accumulate(param.getName(), staplerJsonForDescr((Describable)v));
                } else if (v instanceof List && !((List) v).isEmpty()) {
                    JSONArray a = new JSONArray();
                    for (Object obj : (List) v) {
                        if (obj instanceof Describable) {
                            a.add(staplerJsonForDescr((Describable) obj));
                        } else if (obj instanceof Number) {
                            a.add(obj.toString());
                        } else {
                            a.add(obj);
                        }
                    }
                    o.accumulate(param.getName(), a);
                } else if (v instanceof Number) {
                    o.accumulate(param.getName(), v.toString());
                } else {
                    o.accumulate(param.getName(), v);
                }
            }
        }
        return o;
    }

}
