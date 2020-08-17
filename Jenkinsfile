def builds = [:]
builds['linux-with-docker-wrapper'] = { buildPlugin(platforms: ['docker']) }
// TODO Docker-based tests fail on 2019
builds['windows-wrapper'] = { buildPlugin(useAci: true, platforms: ['windows']) }
parallel builds
