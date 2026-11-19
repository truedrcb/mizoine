#!/usr/bin/env bash
# Starts Mizoine. Copy it into the folder with the jar, users.yaml and repositories/, then: ./mizoine.sh
#
#   mizoine/
#   ├── mizoine.sh
#   ├── mizoine-1.0.0.jar      (from GitHub releases, or target/ after mvn verify)
#   ├── users.yaml
#   └── repositories/          (one <name>.yaml per repository)
#
# Further settings are passed on: ./mizoine.sh --server.port=12014
# Environment: JAVA (default: java on the PATH), JAR (default: the newest mizoine-*.jar here).
set -euo pipefail

cd "$(dirname "$0")"

# Java reads file names in the charset of the locale. Without UTF-8 (no LANG, as in many services),
# "Rücksendeportal.pdf" is shown as "R��cksendeportal.pdf" and cannot be opened.
# LC_ALL and LC_CTYPE would override LANG, so they are removed.
export LANG=C.UTF-8
unset LC_ALL LC_CTYPE

JAVA=${JAVA:-java}
if [[ -z ${JAR:-} ]]; then
	shopt -s nullglob
	jars=(mizoine-*.jar)
	JAR=$(printf '%s\n' "${jars[@]}" | sort -V | tail -n 1)
fi
if [[ -z $JAR || ! -f $JAR ]]; then
	echo "No mizoine-*.jar in $(pwd); set JAR=/path/to/mizoine.jar" >&2
	exit 1
fi

exec "$JAVA" -jar "$JAR" \
	--mizoine.users-file=users.yaml \
	--mizoine.repositories-dir=repositories \
	"$@"
