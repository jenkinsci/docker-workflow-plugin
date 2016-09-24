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

FROM jenkinsci/workflow-demo:2.3

USER root

RUN apt-get update && \
    apt-get install -y sudo
RUN adduser jenkins sudo && \
    echo 'Defaults !authenticate' >> /etc/sudoers

# Minimum for docker-workflow. Docker server does not accept a client newer than itself (older is OK).
RUN wget --no-verbose -O /usr/bin/docker --no-check-certificate https://get.docker.com/builds/Linux/x86_64/docker-1.7.0 && \
    chmod a+x /usr/bin/docker

ADD repo /tmp/repo
RUN git config --global user.email "demo@jenkins-ci.org" && git config --global user.name "Docker Workflow Demo" && cd /tmp/repo && git init && git add . && git commit -m 'demo'

# Run commands identical to those in Pipeline script to warm up the cache:
RUN echo '<settings><mirrors><mirror><id>central</id><url>http://repo.jenkins-ci.org/simple/repo1-cache/</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' > settings.xml
RUN /usr/local/maven/bin/mvn -s settings.xml -Dmaven.repo.local=/usr/share/jenkins/ref/jobs/docker-workflow/workspace@tmp/m2repo -f /tmp/repo/app -B -DskipTests clean package && \
    /usr/local/maven/bin/mvn -s settings.xml -Dmaven.repo.local=/usr/share/jenkins/ref/jobs/docker-workflow/workspace@tmp/m2repo -f /tmp/repo/test -B -Dmaven.test.failure.ignore clean test

COPY plugins.txt /tmp/files/
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD JENKINS_HOME /usr/share/jenkins/ref

COPY run-demo.sh /usr/local/bin/run-demo.sh

CMD /usr/local/bin/run-demo.sh
