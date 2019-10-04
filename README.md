Docker Pipeline Plugin
=====================================

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/docker-workflow.svg)](https://plugins.jenkins.io/docker-workflow)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/docker-workflow-plugin.svg?label=changelog)](https://github.com/jenkinsci/docker-workflow-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/docker-workflow.svg?color=blue)](https://plugins.jenkins.io/docker-workflow)

Jenkins plugin which allows building, testing, and using Docker images from Jenkins Pipeline projects.

Summary
---

A full description is available in the plugin’s [documentation](https://go.cloudbees.com/docs/plugins/docker-workflow/).

Demo
---
The plugin has a Docker-based demo. See the [demo README](demo/README.md) page for setup and launch guidelines.

Releasing
---

Prior to release, edit `demo/plugins.txt` to use the snapshot version and run:

    mvn -DskipTests clean install
    make -C demo run

and verify that the demo works.

After the Maven release completes, update the `docker-workflow` version in `demo/plugins.txt` to the release version and run

    make -C demo run
    
to sanity check, then

    make -C demo push-latest

License
---
[MIT License](http://opensource.org/licenses/MIT)

Changelog
---

* For new versions, see [GitHub Releases](https://github.com/jenkinsci/docker-workflow-plugin/releases)
* For versions 1.19 and older, see the [plugin's Wiki page](https://wiki.jenkins.io/display/JENKINS/Docker+Pipeline+Plugin)
