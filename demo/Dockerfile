##
# The MIT License
#
# Copyright (c) 2015, CloudBees, Inc.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
##

FROM jenkins/jenkins

USER root

# Install Docker client
ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.12.6

RUN set -x \
	&& curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION.tgz" -o docker.tgz \
	&& tar -xzvf docker.tgz \
	&& mv docker/* /usr/local/bin/ \
	&& rmdir docker \
	&& rm docker.tgz \
	&& docker -v

RUN cd /opt && \
  curl -s https://dlcdn.apache.org/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.tar.gz | tar xvfz - && \
  ln -s /opt/apache-maven-3.8.6/bin/mvn /usr/bin && \
  which mvn && \
  mvn -version

ADD repo /tmp/repo
RUN git config --global user.email "demo@jenkins-ci.org" && git config --global user.name "Docker Workflow Demo" && cd /tmp/repo && git init && git add . && git commit -m 'demo'

# Run commands identical to those in Pipeline script to warm up the cache:
RUN echo '<settings><mirrors><mirror><id>central</id><url>http://repo.jenkins-ci.org/public/</url><mirrorOf>*</mirrorOf></mirror></mirrors></settings>' > settings.xml
RUN mvn -s settings.xml -Dmaven.repo.local=/usr/share/jenkins/ref/jobs/docker-workflow/workspace@tmp/m2repo -f /tmp/repo/app -B -DskipTests clean package && \
    mvn -s settings.xml -Dmaven.repo.local=/usr/share/jenkins/ref/jobs/docker-workflow/workspace@tmp/m2repo -f /tmp/repo/test -B -Dmaven.test.failure.ignore clean test
# TODO switch to a persistent volume as in parallel-test-executor-demo, after making sure run-demo.sh starts by deleting snapshots from it

RUN jenkins-plugin-cli --plugins \
  credentials-binding \
  docker-workflow \
  xvnc
COPY docker-workflow.jpi /usr/share/jenkins/ref/plugins

ADD JENKINS_HOME /usr/share/jenkins/ref

COPY run-demo.sh /usr/local/bin/run-demo.sh
ENV JAVA_OPTS -Djenkins.install.state=INITIAL_SECURITY_SETUP

RUN chown -R jenkins.jenkins /usr/share/jenkins/ref /tmp/repo

USER jenkins

CMD /usr/local/bin/run-demo.sh
