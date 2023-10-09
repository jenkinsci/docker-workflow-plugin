buildPlugin(useContainerAgent: false, configurations: [
  [platform: 'linux', jdk: 21],
  [platform: 'maven-17-windows', jdk: 17] // TODO Docker-based tests fail when using Docker on Windows. The maven-windows agents do not have Docker installed so tests that require Docker are skipped.
])
