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

FROM jenkinsci/workflow-demo:2.2

USER root

RUN apt-get update
RUN apt-get install -y sudo
RUN adduser jenkins sudo
RUN echo 'Defaults !authenticate' >> /etc/sudoers

# Minimum for docker-workflow. Docker server does not accept a client newer than itself (older is OK).
RUN wget -O /usr/bin/docker --no-check-certificate https://get.docker.com/builds/Linux/x86_64/docker-1.3.0
RUN chmod a+x /usr/bin/docker

COPY run-demo.sh /usr/local/bin/run-demo.sh

COPY plugins.txt /tmp/files/
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD JENKINS_HOME /usr/share/jenkins/ref

ADD repo /tmp/repo
RUN git config --global user.email "demo@jenkins-ci.org" && git config --global user.name "Docker Workflow Demo" && cd /tmp/repo && git init && git add . && git commit -m 'demo'

# Run commands identical to those in Pipeline script to warm up the cache:
RUN /usr/local/maven/bin/mvn -Dmaven.repo.local=/usr/share/jenkins/ref/jobs/docker-workflow/workspace@tmp/m2repo -f /tmp/repo/app -B -DskipTests clean package && \
    /usr/local/maven/bin/mvn -Dmaven.repo.local=/usr/share/jenkins/ref/jobs/docker-workflow/workspace@tmp/m2repo -f /tmp/repo/test -B -Dmaven.test.failure.ignore clean test

CMD /usr/local/bin/run-demo.sh
