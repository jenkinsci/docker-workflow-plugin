Docker Pipeline Plugin
=====================================

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/docker-workflow.svg)](https://plugins.jenkins.io/docker-workflow)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/docker-workflow-plugin.svg?label=changelog)](https://github.com/jenkinsci/docker-workflow-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/docker-workflow.svg?color=blue)](https://plugins.jenkins.io/docker-workflow)

Jenkins plugin which allows building, testing, and using Docker images from Jenkins Pipeline projects.

Summary
---

A full description is available in the plugin’s [documentation](https://go.cloudbees.com/docs/plugins/docker-workflow/).

Building
---
```console
rm -rf target               # if you want a 100% clean build
mvn package -DskipTests
```

This will create a target/docker-workflow.hpi file, which can be uploaded manually to Jenkins by clicking Dashboard -> Manage Jenkins -> Manage Plugins -> Advanced, then scrolling down to the section titled Deploy Plugin, clicking Deploy Plugin, selecting this file, and clicking Deploy.

After deploying, you will need to restart Jenkins.  When Jenkins is running in Docker, it apparently is not capable of restarting itself, so you'll have to stop the Docker container and restart it.

Demo
---
The plugin has an outdated Docker-based demo. See the [demo README from v1.12](https://github.com/jenkinsci/docker-workflow-plugin/tree/docker-workflow-1.12/demo) for setup and launch guidelines.

License
---
[MIT License](http://opensource.org/licenses/MIT)

Changelog
---

* For new versions, see [GitHub Releases](https://github.com/jenkinsci/docker-workflow-plugin/releases)
* For versions 1.19 and older, see the [plugin's Wiki page](https://wiki.jenkins.io/display/JENKINS/Docker+Pipeline+Plugin)
