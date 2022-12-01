FROM jenkins/agent:4.10-5-jdk11
USER root
RUN cat /etc/os-release
# TODO https://github.com/moby/moby/issues/15717 alas; no curl or wget in image
ADD https://download.docker.com/linux/debian/dists/bullseye/pool/stable/amd64/docker-ce-cli_20.10.20~3-0~debian-bullseye_amd64.deb /tmp/docker.deb
RUN dpkg -i /tmp/docker.deb
RUN docker version || :
# Stay as root, so we can access docker.sock
