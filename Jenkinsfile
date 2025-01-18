/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  useContainerAgent: false, // Set to `false` if you need to use Docker for containerized tests
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'linux', jdk: 17],
    // TODO Windows tests seem to be failing on temporary Windows CI infrastructure from https://github.com/jenkins-infra/helpdesk/issues/4490
    //[platform: 'maven-17-windows', jdk: 17], // TODO Docker-based tests fail when using Docker on Windows. The maven-windows agents do not have Docker installed so tests that require Docker are skipped.
])
