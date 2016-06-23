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

htpasswd -bmc docker-registry.htpasswd workflowuser 123123123

# Create the CA Key and Certificate for signing Certs
openssl genrsa -des3 -passout pass:x -out ca.key 4096
openssl rsa -passin pass:x -in ca.key -out ca.key # remove password!
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/C=US/ST=California/L=San Jose/O=Jenkins CI/OU=Workflow Dept/CN=localhost"
 
# Create the Server Key, CSR, and Certificate
openssl genrsa -des3 -passout pass:x -out key.pem 1024
openssl rsa -passin pass:x -in key.pem -out key.pem # remove password!
openssl req -new -key key.pem -out server.csr -subj "/C=US/ST=California/L=San Jose/O=Jenkins CI/OU=Workflow Dept/CN=localhost"
 
# Self sign the server cert.
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key -set_serial 01 -out cert.pem

# cat the ca cert onto the server cert
cat ca.crt >> cert.pem

# White-list the CA cert (because it is self-signed), otherwise docker client will not be able to authenticate
cp ca.crt /usr/local/share/ca-certificates
update-ca-certificates

popd
