set -ex
docker tag localhost/examplecorp/spring-petclinic:$BUILD_TAG localhost/examplecorp/spring-petclinic:latest
docker push localhost/examplecorp/spring-petclinic:latest
