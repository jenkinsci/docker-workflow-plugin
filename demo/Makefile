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

TAG=$(shell date -I -u)
IMAGE=jenkinsci/docker-workflow-demo

build-registry:
	./gen-security-data.sh certs
	docker build -t registry:docker-workflow-demo -f Dockerfile-registry .

copy-plugins:
	set -e; \
	rm -rf plugins; \
	mkdir plugins; \
	for gav in `cat plugins.txt`; do \
	  g=`echo $$gav | cut -f1 -d: | perl -pe 's{[.]}{/}g'`; \
	  a=`echo $$gav | cut -f2 -d:`; \
	  v=`echo $$gav | cut -f3 -d:`; \
	  hpi=$$HOME/.m2/repository/$$g/$$a/$$v/$$a-$$v.hpi; \
	  if [ \! -f $$hpi ]; then \
	    mvn -U org.apache.maven.plugins:maven-dependency-plugin:2.5.1:get -Dartifact=$$gav:hpi -Dtransitive=false ||\
	      (locate $$a-$$v.hpi | fgrep .m2/repository/; false); \
	  fi; \
	  cp -v $$hpi plugins/$$a.jpi; \
	done

build:	copy-plugins build-registry
	docker build -t $(IMAGE):$(TAG) .

# To connect a Java debugger to the Jenkins instance running in the docker container, simply add the following
# options to the "docker run" command (just after the port mappings):
#
#       -p 5500:5500 -e JAVA_OPTS=-Xrunjdwp:transport=dt_socket,server=y,address=5500,suspend=n
#	
# If using boot2docker, you need to tell your remote debugger to use the boot2docker VM ip (ala boot2docker ip).

ifeq ($(shell uname -s),Darwin)
    STAT_OPT = -f
else
    STAT_OPT = -c
endif

DOCKER_RUN=docker run --rm -p 127.0.0.1:8080:8080 -v /var/run/docker.sock:/var/run/docker.sock --group-add=$(shell stat $(STAT_OPT) %g /var/run/docker.sock)

run:	build
	$(DOCKER_RUN) $(IMAGE):$(TAG)

clean:
	rm -rf certs plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
