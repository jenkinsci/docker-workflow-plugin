FROM registry:2.5.1
ADD certs/ca.crt certs/ca.key certs/docker-registry.htpasswd /var/registry/certs/
ENV REGISTRY_HTTP_TLS_CERTIFICATE /var/registry/certs/ca.crt
ENV REGISTRY_HTTP_TLS_KEY /var/registry/certs/ca.key
ENV REGISTRY_AUTH htpasswd
ENV REGISTRY_AUTH_HTPASSWD_REALM Registry Realm
ENV REGISTRY_AUTH_HTPASSWD_PATH /var/registry/certs/docker-registry.htpasswd
