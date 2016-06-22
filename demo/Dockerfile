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

###############################################################
# Docker in Docker https://github.com/jpetazzo/dind 
    
# Install Docker from Docker Inc. repositories.
RUN curl -sSL https://get.docker.com/ | sh

# Define additional metadata for our image.
VOLUME /var/lib/docker

#
###############################################################

RUN apt-get update && apt-get install -y apparmor apache2-utils

# Install the magic wrapper, tweaked to run our demo.
ADD https://raw.githubusercontent.com/jpetazzo/dind/b6c7f1ffdac20de54cb03033418d4708a9cb44ce/wrapdocker /usr/local/bin/
RUN perl -p -i -e 's!\texec bash --login!\texec bash --login -c /usr/local/bin/run-demo.sh!' /usr/local/bin/wrapdocker
RUN chmod +x /usr/local/bin/wrapdocker

COPY workflow-reg-proxy.conf /tmp/files/regup/workflow-reg-proxy.conf
COPY gen-security-data.sh /usr/local/bin/gen-security-data.sh
RUN /usr/local/bin/gen-security-data.sh /tmp/files/regup/sec
COPY run-demo.sh /usr/local/bin/run-demo.sh

COPY plugins.txt /tmp/files/
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD JENKINS_HOME /usr/share/jenkins/ref

ADD repo /tmp/repo
RUN git config --global user.email "demo@jenkins-ci.org" && git config --global user.name "Docker Workflow Demo" && cd /tmp/repo && git init && git add . && git commit -m 'demo'

# wrapdocker has been modified to launch Jenkins via the installed run.sh script
CMD ["wrapdocker"]
