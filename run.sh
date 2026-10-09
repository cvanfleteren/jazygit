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

JAR="target/jazygit-fat.jar"
DEBUG_PORT=5005

if [[ ! -f "$JAR" ]]; then
    mvn -q -DskipTests package
fi

if [[ "${1:-}" == "--debug" ]]; then
    echo "Waiting for debugger to attach on port ${DEBUG_PORT}..."
    exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT} -jar "$JAR"
else
    exec java -jar "$JAR"
fi
