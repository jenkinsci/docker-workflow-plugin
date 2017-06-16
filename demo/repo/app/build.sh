set -ex
mvn -Dmaven.repo.local=/m2repo -B -DskipTests -f /src package
