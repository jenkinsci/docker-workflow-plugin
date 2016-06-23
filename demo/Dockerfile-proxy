FROM nginx:1.9.0
RUN apt-get update
RUN apt-get install -y apache2-utils
COPY workflow-reg-proxy.conf /etc/nginx/conf.d/
COPY gen-security-data.sh /usr/local/bin/
RUN /usr/local/bin/gen-security-data.sh /var/registry/certs
