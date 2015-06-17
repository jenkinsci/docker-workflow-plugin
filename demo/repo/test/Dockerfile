FROM maven:3.3.3-jdk-8
RUN apt-get update
# adapted from https://github.com/jenkinsci/acceptance-test-harness/blob/a4adf775ebebb8cd21caca493f558b7ba9b79757/src/main/resources/org/jenkinsci/test/acceptance/docker/fixtures/XvncSlaveContainer/Dockerfile#L2-13
RUN apt-get install -y vnc4server imagemagick iceweasel
RUN mkdir /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix/
ENV XAUTHORITY /root/.Xauthority
RUN mkdir /root/.vnc && (echo changeme; echo changeme) | vncpasswd /root/.vnc/passwd
RUN touch /root/.vnc/xstartup && chmod a+x /root/.vnc/xstartup
