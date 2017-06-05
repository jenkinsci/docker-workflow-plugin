set -ex
cd `dirname $0`

APP_IMG=localhost/examplecorp/spring-petclinic:$BUILD_TAG
TEST_IMG=examplecorp/spring-petclinic-tests:$BUILD_TAG
TEST_CTR=spring-petclinic-tests-$BUILD_TAG

# Create the test container.
docker build -t $TEST_IMG test

# Run the application in a separate container.
cid=`docker run -d $APP_IMG`
trap "docker rm -f $cid $TEST_CTR" EXIT

# Run the test container.
docker run --name=$TEST_CTR --link=$cid:petclinic -v m2repo:/m2repo $TEST_IMG /src/test.sh
docker cp $TEST_CTR:/src/screenshot.jpg .
