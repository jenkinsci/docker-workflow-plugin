node {
  git '/tmp/repo'

  def maven = docker.image('maven:3.3.9-jdk-8'); // https://registry.hub.docker.com/_/maven/

  stage('Mirror') {
    // First make sure the slave has this image.
    // (If you could set your registry below to mirror Docker Hub,
    // this would be unnecessary as maven.inside would pull the image.)
    maven.pull()
  }

  // We are pushing to a private secure Docker registry in this demo.
  // 'docker-registry-login' is the username/password credentials ID as defined in Jenkins Credentials.
  // This is used to authenticate the Docker client to the registry.
  docker.withRegistry('https://localhost/', 'docker-registry-login') {

    stage('Build') {
      // Spin up a Maven container to build the petclinic app from source.
      // First set up a shared Maven repo so we don't need to download all dependencies on every build.
      maven.inside {
        sh "mvn -o -Dmaven.repo.local=${pwd tmp: true}/m2repo -f app -B -DskipTests clean package"
        // The app .war and Dockerfile are now available in the workspace. See below.
      }
    }

    def pcImg
    stage('Bake Docker image') {
      // Use the spring-petclinic Dockerfile (see above 'maven.inside()' block)
      // to build a container that can run the app.
      // The Dockerfile is in the app subdir of the active workspace
      // (see above maven.inside() block), so we specify that.
      // The Dockerfile expects the petclinic.war file to be in the 'target' dir
      // relative to its own directory, which will be the case.
      pcImg = docker.build("examplecorp/spring-petclinic:${env.BUILD_TAG}", 'app')

      // Let us tag and push the newly built image. Will tag using the image name provided
      // in the 'docker.build' call above (which included the build number on the tag).
      pcImg.push();
    }

    stage('Test Image') {
      // Spin up a Maven + Xvnc test container, linking it to the petclinic app container
      // allowing the Maven tests to send HTTP requests between the containers.
      def testImg = docker.build('examplecorp/spring-petclinic-tests:snapshot', 'test')
      // Run the petclinic app in its own Docker container.
      pcImg.withRun {petclinic ->
        testImg.inside("--link=${petclinic.id}:petclinic") {
          // https://github.com/jenkinsci/workflow-plugin/blob/master/basic-steps/CORE-STEPS.md#build-wrappers
          wrap([$class: 'Xvnc', takeScreenshot: true, useXauthority: true]) {
            sh "mvn -o -Dmaven.repo.local=${pwd tmp: true}/m2repo -f test -B clean test"
          }
        }
      }
      input "How do you like ${env.BUILD_URL}artifact/screenshot.jpg?"
    }

    stage('Promote Image') {
      // All the tests passed. We can now retag and push the 'latest' image.
      pcImg.push('latest')
    }
  }
}
