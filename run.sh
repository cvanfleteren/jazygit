#!/usr/bin/env bash
#
# Convenience launcher for jazygit.
#
# Usage:
#   ./run.sh            # build (if needed) and run the app normally
#   ./run.sh --debug    # run the app suspended, waiting for a debugger to attach on port 5005
#
# Must be run from a real terminal (e.g. IntelliJ's Terminal tool window),
# since jazygit is a TUI app that needs a real PTY attached to stdin/stdout.

set -euo pipefail

cd "$(dirname "$0")"

JAR_GLOB="target/jazygit-*-fat.jar"
DEBUG_PORT=5005

newest_jar() {
    local jars=($JAR_GLOB)
    [[ -e "${jars[0]}" ]] || return 0
    ls -t "${jars[@]}" | head -n 1
}

JAR="$(newest_jar)"

# Rebuild when there is no jar yet, or when the sources or pom are newer than it.
if [[ -z "$JAR" ]] || [[ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]]; then
    mvn -q -DskipTests package
    JAR="$(newest_jar)"
fi

if [[ "${1:-}" == "--debug" ]]; then
    echo "Waiting for debugger to attach on port ${DEBUG_PORT}..."
    exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT} -jar "$JAR"
else
    exec java -jar "$JAR"
fi
