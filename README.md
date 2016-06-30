CloudBees Docker Pipeline Plugin
=====================================

Jenkins plugin which allows building, testing, and using Docker images from Jenkins Pipeline projects.

Summary
---

A full description is available in the plugin’s [documentation](https://documentation.cloudbees.com/docs/cje-user-guide/docker-workflow.html).

Demo
---
The plugin has a Docker-based demo. See the [demo README](demo/README.md) page for setup and launch guidelines.

Releasing
---

Prior to release, run:

    mvn -DskipTests clean install
    make -C demo clean run-snapshot

and verify that the demo works.

License
---
[MIT License](http://opensource.org/licenses/MIT)
