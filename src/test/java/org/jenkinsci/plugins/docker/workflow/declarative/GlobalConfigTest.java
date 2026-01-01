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
 */

package org.jenkinsci.plugins.docker.workflow.declarative;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

class GlobalConfigTest {

    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();

    @Issue("JENKINS-42027")
    @Test
    void globalConfigPersists() throws Throwable {
        story.then(r -> {
                GlobalConfig.get().setDockerLabel("config_docker");
                GlobalConfig.get().save();
            }
        );

        story.then(r -> assertEquals("config_docker", GlobalConfig.get().getDockerLabel())
        );
    }

    @Issue("https://github.com/jenkinsci/docker-workflow-plugin/pull/202#issuecomment-597156438")
    @LocalData
    @Test
    void oldPackages() throws Throwable {
        story.then(r -> {
            assertEquals("docker", GlobalConfig.get().getDockerLabel(), "GlobalConfig is translated");
            assertEquals("https://myreg/", GlobalConfig.get().getRegistry().getUrl());
            assertEquals("myreg", GlobalConfig.get().getRegistry().getCredentialsId());
            Folder d = r.jenkins.getItemByFullName("d", Folder.class);
            assertNotNull(d);
            FolderConfig c = d.getProperties().get(FolderConfig.class);
            assertNotNull(c, "FolderConfig is translated");
            assertEquals("dokker", c.getDockerLabel());
            assertEquals("https://yourreg/", c.getRegistry().getUrl());
            assertEquals("yourreg", c.getRegistry().getCredentialsId());
            assertEquals(Collections.emptySet(), ExtensionList.lookupSingleton(OldDataMonitor.class).getData().keySet(), "there is no old data");
        });
    }

}
