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

#
# Install a private registry that can be used by the demo to push images to.
#

echo '*************** Installing a local Docker Registry Service for the demo ***************'
echo '***************            Please sit tight for a minute                ***************'

cont1=$(docker run -d -p 443:5000 --name registry --restart=always registry:docker-workflow-demo)
# TODO would be natural to switch to Compose
trap "docker rm -f $cont1" EXIT

# Note that this https://github.com/docker/docker/issues/23177 workaround is useless since the Docker CLI does not do the hostname resolution, the server does:
# echo $(docker inspect -f '{{.NetworkSettings.Gateway}}' $HOSTNAME) docker.example.com >> /etc/hosts

echo '***************         Docker Registry Service running now             ***************'

# In case some tagged images were left over from a previous run using a cache:
(docker images -q examplecorp/spring-petclinic; docker images -q localhost/examplecorp/spring-petclinic) | xargs docker rmi --no-prune=true --force

# TODO delete any snapshots from m2repo volume
# find … \( -type d -name '*-SNAPSHOT' -prune -o -type f -name maven-metadata-local.xml \) -exec rm -rfv {} \;

#
# Now run Jenkins.
#
#
/usr/local/bin/run.sh
