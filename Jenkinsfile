/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  forkCount: '1C', // run this number of tests in parallel for faster feedback.  If the number terminates with a 'C', the value will be multiplied by the number of available CPU cores
  useContainerAgent: false, // Set to `false` if you need to use Docker for containerized tests
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'maven-17-windows', jdk: 17], // TODO Docker-based tests fail when using Docker on Windows. The maven-windows agents do not have Docker installed so tests that require Docker are skipped.
])
