set -ex
cd `dirname $0`

# https://docs.docker.com/engine/userguide/eng-image/multistage-build/ would not be a good match here since docker-build does not support volumes, needed for caching the local repository between builds

BUILD_IMG=examplecorp/spring-petclinic-build:$BUILD_TAG
BUILD_CTR=spring-petclinic-build-$BUILD_TAG

# Prepare the image in which we will build the app.
docker build -t $BUILD_IMG -f app/Dockerfile.build app

# Run a temporary container to build the app and grab petclinic.war out of it.
trap "docker rm -f $BUILD_CTR; rm -f app/petclinic.war" EXIT
docker run --name $BUILD_CTR -v m2repo:/m2repo $BUILD_IMG /src/build.sh
docker cp $BUILD_CTR:/src/target/petclinic.war app

# Build the actual application image.
docker build -t localhost/examplecorp/spring-petclinic:$BUILD_TAG app
