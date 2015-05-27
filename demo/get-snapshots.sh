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
# TODO: This is temp (resurrected from earlier). Will be replaced by an entry in plugins.txt once we release this plugin. Then delete this script.
#

PROJECT_DIR=..
PLUGIN_HPI=$PROJECT_DIR/target/docker-workflow.hpi

if [[ ! -f $PLUGIN_HPI ]]; then
    # Build the plugin  
    pushd $PROJECT_DIR
    mvn clean install -DskipTests
    popd
fi

# Cleanup JENKINS_HOME
PLUGINS_DIR=./JENKINS_HOME/plugins
rm -rf $PLUGINS_DIR
mkdir -p $PLUGINS_DIR

# Copy and pin this project plugin
cp $PLUGIN_HPI $PLUGINS_DIR

# Rename and pin all plugins.
pushd $PLUGINS_DIR 
for hpiFile in *.hpi
do
  prefix=$(echo $hpiFile | sed 's/\(.*\)\.hpi/\1/')
  mv $hpiFile $prefix.jpi
  touch $prefix.jpi.pinned
done
popd


