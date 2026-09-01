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

set -e

rm -rfv $1
mkdir -p $1

pushd $1

docker run --entrypoint htpasswd registry:2.5.1 -Bbn pipelineuser 123123123 > docker-registry.htpasswd 

# Create the CA Key and Certificate for signing Certs
openssl genrsa -des3 -passout pass:x -out ca.key 4096
openssl rsa -passin pass:x -in ca.key -out ca.key # remove password!
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/C=US/ST=California/L=San Jose/O=Jenkins CI/OU=Pipeline Dept/CN=localhost"
