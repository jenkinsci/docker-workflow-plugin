/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
 *
 */

package org.jenkinsci.plugins.docker.workflow.declarative;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Run;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;

/**
 * The system config.
 *
 * For example the system level {@link DockerLabelProvider}.
 */
@Extension @Symbol({"pipeline-model-docker", "pipeline-model"})
public class GlobalConfig extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(GlobalConfig.class.getName());

    private String dockerLabel;
    private DockerRegistryEndpoint registry;

    public GlobalConfig() {
        String oldId = "org.jenkinsci.plugins.pipeline.modeldefinition.config.GlobalConfig";
        File oldConfigFile = new File(Jenkins.get().getRootDir(), oldId + ".xml");
        File newConfigFile = new File(Jenkins.get().getRootDir(), getId() + ".xml");
        if (oldConfigFile.exists() && !newConfigFile.exists()) {
            try {
                FileUtils.moveFile(oldConfigFile, newConfigFile);
                LOGGER.info("migrated " + oldConfigFile + " to " + newConfigFile);
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
        ((XStream2) getConfigFile().getXStream()).addCompatibilityAlias(oldId, GlobalConfig.class);
        load();
    }

    public String getDockerLabel() {
        return Util.fixEmpty(dockerLabel);
    }

    @DataBoundSetter
    public void setDockerLabel(String dockerLabel) {
        this.dockerLabel = dockerLabel;
    }

    public DockerRegistryEndpoint getRegistry() {
        return registry;
    }

    @DataBoundSetter
    public void setRegistry(DockerRegistryEndpoint registry) {
        this.registry = registry;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static GlobalConfig get() {
        return ExtensionList.lookup(GlobalConfiguration.class).get(GlobalConfig.class);
    }

    @Extension(ordinal = -10000) //Last one to be asked
    public static final class GlobalConfigDockerPropertiesProvider extends DockerPropertiesProvider {
        @Inject
        GlobalConfig config;

        @Override
        public String getLabel(@Nullable Run run) {
            return config.getDockerLabel();
        }

        @Override
        public String getRegistryUrl(@Nullable Run run) {
            if (config.getRegistry() != null && !StringUtils.isBlank(config.getRegistry().getUrl())) {
                return config.getRegistry().getUrl();
            } else {
                return null;
            }
        }

        @Override
        public String getRegistryCredentialsId(@Nullable Run run) {
            if (config.getRegistry() != null && !StringUtils.isBlank(config.getRegistry().getCredentialsId())) {
                return config.getRegistry().getCredentialsId();
            } else {
                return null;
            }
        }
    }
}
