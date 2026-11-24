#!/usr/bin/env bash
# Starts Mizoine in the background. Copy it into the folder with the jar, users.yaml and repositories/, then:
#   ./mizoine.sh
#
#   mizoine/
#   ├── mizoine.sh
#   ├── mizoine-1.0.0.jar      (from GitHub releases, or target/ after mvn verify)
#   ├── users.yaml
#   ├── repositories/          (one <name>.yaml per repository)
#   ├── mizoine.log            (written by this script: everything Mizoine prints)
#   └── mizoine.pid            (written by this script: the process ID, for stopping)
#
# Stop:   kill $(cat mizoine.pid)
# Further settings are passed on: ./mizoine.sh --server.port=12014
# Environment: JAVA (default: java on the PATH), JAR (default: the newest mizoine-*.jar here).
set -euo pipefail

cd "$(dirname "$0")"
pid_file=mizoine.pid
log_file=mizoine.log

if [[ -f $pid_file ]]; then
	pid=$(cat "$pid_file")
	if kill -0 "$pid" 2>/dev/null; then
		echo "Mizoine is already running (PID $pid). Stop it first: kill $pid" >&2
		exit 1
	fi
	rm -f "$pid_file"   # left over from a crash or a reboot
fi

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

# only this run's part of the log counts below
touch "$log_file"
log_start=$(($(wc -c < "$log_file") + 1))

# nohup: keeps running after the terminal is closed
nohup "$JAVA" -jar "$JAR" \
	--mizoine.users-file=users.yaml \
	--mizoine.repositories-dir=repositories \
	"$@" >> "$log_file" 2>&1 < /dev/null &
pid=$!
echo "$pid" > "$pid_file"

# wait until Spring reports the start, or Java ends (a wrong setting, a port in use): say which
for _ in $(seq 60); do
	if tail -c "+$log_start" "$log_file" | grep -q "Started MizoineApplication"; then
		echo "Mizoine started (PID $pid). Log: $(pwd)/$log_file"
		echo "Stop: kill \$(cat $(pwd)/$pid_file)"
		exit 0
	fi
	if ! kill -0 "$pid" 2>/dev/null; then
		rm -f "$pid_file"
		echo "Mizoine did not start. End of $log_file:" >&2
		tail -c "+$log_start" "$log_file" | tail -n 20 >&2
		exit 1
	fi
	sleep 1
done
echo "Mizoine is still starting after 60 s (PID $pid). Watch: tail -f $(pwd)/$log_file"
echo "Stop: kill \$(cat $(pwd)/$pid_file)"
