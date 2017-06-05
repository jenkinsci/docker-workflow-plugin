set -ex
export DISPLAY=:42
vncserver :42 -localhost -nolisten tcp
mvn -Dmaven.repo.local=/m2repo -B -f /src test
import -window root /src/screenshot.jpg
