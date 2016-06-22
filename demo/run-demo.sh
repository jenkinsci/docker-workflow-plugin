#!/bin/bash

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

sudo chmod a+rw /var/run/docker.sock

#
# Install a private registry that can be used by the demo to push images to.
#

echo '*************** Installing a local Docker Registry Service for the demo ***************'
echo '***************            Please sit tight for a minute                ***************'

REG_SETUP_PATH=/tmp/files/regup

# TODO we need to kill these containers when run.sh exits
# TODO these are not properly linked
docker run -d --name registry --restart=always registry:0.9.1
docker run -d -p 443:443 --name wf-registry-proxy -v $REG_SETUP_PATH:/etc/nginx/conf.d/ -v $REG_SETUP_PATH/sec:/var/registry/certs --link registry:registry nginx:1.9.0

echo '***************         Docker Registry Service running now             ***************'

# In case some tagged images were left over from a previous run using a cache:
(docker images -q examplecorp/spring-petclinic; docker images -q docker.example.com/examplecorp/spring-petclinic) | xargs docker rmi --no-prune=true --force

#
# Remove the base workflow-demo "cd" job
#
rm -rf /usr/share/jenkins/ref/jobs/cd /var/jenkins_home/jobs/cd

#
# Now run Jenkins.
#
#
/usr/local/bin/run.sh
