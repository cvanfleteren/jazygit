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

if ! compgen -G "$JAR_GLOB" > /dev/null; then
    mvn -q -DskipTests package
fi

JAR="$(ls $JAR_GLOB | head -n 1)"

if [[ "${1:-}" == "--debug" ]]; then
    echo "Waiting for debugger to attach on port ${DEBUG_PORT}..."
    exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT} -jar "$JAR"
else
    exec java -jar "$JAR"
fi
