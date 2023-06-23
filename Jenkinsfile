buildPlugin(useContainerAgent: false, configurations: [
  [platform: 'linux', jdk: 17],
  [platform: 'maven-11-windows', jdk: 11] // TODO Docker-based tests fail when using Docker on Windows. The maven-windows agents do not have Docker installed so tests that require Docker are skipped.
])
