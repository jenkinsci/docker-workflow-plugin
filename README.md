Docker Pipeline Plugin
=====================================

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
