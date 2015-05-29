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
TAG=$(shell cat docker-workflow-version.txt)
IMAGE=jenkinsci/docker-workflow-demo
CACHE=$(shell pwd)/.cache

# Builds a demo based on a released version of the plugin.
build:
	docker build -t $(IMAGE):$(TAG) -f Dockerfile_release .

# Builds a demo based on the current local snapshot build of the plugin.
build-snapshot:
	[ -f ../target/docker-workflow.hpi ] || mvn -f .. -DskipTests clean install
	mkdir -p snapshot-plugins
	cp -v ../target/docker-workflow.hpi snapshot-plugins/docker-workflow.jpi
	echo 'You will need to first: make -C …/jenkinsci/workflow-plugin/demo build-snapshot'
	docker build -t $(IMAGE):SNAPSHOT -f Dockerfile_snapshot .

#	
# To connect a Java debugger to the Jenkins instance running in the docker container, simply add the following
# options to the "docker run" command (just after the port mappings):
#
#       -p 5500:5500 -e JAVA_OPTS=-Xrunjdwp:transport=dt_socket,server=y,address=5500,suspend=n
#	
# If using boot2docker, you need to tell your remote debugger to use the boot2docker VM ip (ala boot2docker ip).
#

run:	build
	docker run --rm -p 8080:8080 -p 8081:8081 -p 8022:22 -p 18080:18080 --add-host=docker.example.com:127.0.0.1 -ti --privileged $(IMAGE):$(TAG)

run-snapshot:	build-snapshot
	docker run --rm -p 8080:8080 -p 8081:8081 -p 8022:22 -p 18080:18080 --add-host=docker.example.com:127.0.0.1 -ti --privileged $(IMAGE):SNAPSHOT

run-cached:	build
	mkdir -p $(CACHE)/docker
	sudo rm -rf $(CACHE)/docker/containers $(CACHE)/docker/linkgraph.db
	mkdir -p $(CACHE)/maven
	docker run --rm -p 8080:8080 -p 8081:8081 -p 8022:22 -p 18080:18080 --add-host=docker.example.com:127.0.0.1 -ti --privileged -v $(CACHE)/docker:/var/lib/docker -v $(CACHE)/maven:/m2repo $(IMAGE):$(TAG)

run-snapshot-cached:	build-snapshot
	mkdir -p $(CACHE)/docker
	sudo rm -rf $(CACHE)/docker/containers $(CACHE)/docker/linkgraph.db
	mkdir -p $(CACHE)/maven
	docker run --rm -p 8080:8080 -p 8081:8081 -p 8022:22 -p 18080:18080 --add-host=docker.example.com:127.0.0.1 -ti --privileged -v $(CACHE)/docker:/var/lib/docker -v $(CACHE)/maven:/m2repo $(IMAGE):SNAPSHOT

clean:
	rm -rf snapshot-plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag -f $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
