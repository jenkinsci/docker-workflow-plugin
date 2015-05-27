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

import hudson.BulkChange;
import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.jenkinsci.plugins.docker.commons.DockerImageExtractor;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprintAction;

/**
 * Represents that a build used a particular Docker image.
 * Unlike {@link DockerFingerprintAction} this records the actual name, not an image ID.
 */
final class ImageAction extends InvisibleAction /* implements RunAction2 */ {

    private final Set<String> names = new TreeSet<String>();

    ImageAction() {}

    /**
     * Records an image name as per {@code Config.Image} in {@code docker inspect $container} or a {@code FROM} instruction.
     * Typically in {@code repository} or {@code user/repository} format, but may include tags {@code repo:latest} or hashes {@code repo@123abc}.
     * @see <a href="https://docs.docker.com/reference/api/docker_remote_api_v1.18/#inspect-a-container">this specification which does not really specify anything</a>
     */
    static void add(String image, Run<?,?> run) throws IOException {
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                ImageAction action = run.getAction(ImageAction.class);
                if (action == null) {
                    action = new ImageAction();
                    run.addAction(action);
                }
                action.names.add(image);
                bc.commit();
            } finally {
                bc.abort();
            }
        }
    }

    @Extension public static final class ExtractorImpl extends DockerImageExtractor {

        @Override public Collection<String> getDockerImagesUsedByJob(Job<?,?> job) {
            Run<?,?> build = job.getLastCompletedBuild();
            if (build != null) {
                ImageAction action = build.getAction(ImageAction.class);
                if (action != null) {
                    Set<String> bareNames = new TreeSet<String>();
                    for (String name : action.names) {
                        bareNames.add(name./* strip any tag or hash */replaceFirst("[:@].+", ""));
                    }
                    return bareNames;
                }
            }
            return Collections.emptySet();
        }

    }

}
