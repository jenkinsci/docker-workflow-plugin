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

TAG=$(shell perl -n -e 'if (/docker-workflow:(.+)/) {print $$1}' plugins.txt)
IMAGE=jenkinsci/docker-workflow-demo

# Builds a demo based on a released version of the plugin.
build:
	docker build -t $(IMAGE):$(TAG) .

# Builds a demo based on the current local snapshot build of the plugin.
build-snapshot:
	docker build -t $(IMAGE):RELEASE .
	mkdir -p snapshot-plugins
	for p in $$(cat plugins.txt|perl -pe s/:.+//g); do echo looking for snapshot builds of $$p; for g in org/jenkins-ci/plugins; do if [ -f ~/.m2/repository/$$g/$$p/maven-metadata-local.xml ]; then cp -v $$(ls -1 ~/.m2/repository/$$g/$$p/*-SNAPSHOT/*.hpi | tail -1) snapshot-plugins/$$p.jpi; fi; done; done
	docker build -f Dockerfile-snapshot -t $(IMAGE):SNAPSHOT .

# To connect a Java debugger to the Jenkins instance running in the docker container, simply add the following
# options to the "docker run" command (just after the port mappings):
#
#       -p 5500:5500 -e JAVA_OPTS=-Xrunjdwp:transport=dt_socket,server=y,address=5500,suspend=n
#	
# If using boot2docker, you need to tell your remote debugger to use the boot2docker VM ip (ala boot2docker ip).

DOCKER_RUN=docker run --rm -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock

run:	build
	$(DOCKER_RUN) $(IMAGE):$(TAG)

run-snapshot:	build-snapshot
	$(DOCKER_RUN) $(IMAGE):SNAPSHOT

clean:
	rm -rf snapshot-plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag -f $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
