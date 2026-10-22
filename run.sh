#!/usr/bin/env bash
#
# Convenience launcher for jazygit.
#
# Usage:
#   ./run.sh [args]          # build (if needed) and run the app normally
#   ./run.sh --debug [args]  # run the app, accepting a debugger on port 5005
#
# Any other arguments go to jazygit itself; ./run.sh --help lists them.
#
# Must be run from a real terminal (e.g. IntelliJ's Terminal tool window),
# since jazygit is a TUI app that needs a real PTY attached to stdin/stdout.

set -euo pipefail

cd "$(dirname "$0")"

JAR_GLOB="jazygit-app/target/jazygit-*-fat.jar"
DEBUG_PORT=5005

newest_jar() {
    local jars=($JAR_GLOB)
    [[ -e "${jars[0]}" ]] || return 0
    ls -t "${jars[@]}" | head -n 1
}

JAR="$(newest_jar)"

# Rebuild when there is no jar yet, or when the sources or poms are newer than it.
if [[ -z "$JAR" ]] || [[ -n "$(find jazygit-core/src jazygit-app/src pom.xml jazygit-core/pom.xml jazygit-app/pom.xml -newer "$JAR" -print -quit)" ]]; then
    mvn -q -DskipTests package
    JAR="$(newest_jar)"
fi

# Nix wraps its JDK's java in a script that puts its gtk/glib on LD_LIBRARY_PATH, for AWT. The commands
# jazygit runs would inherit that, and a tool built against another glibc then fails to load (gh opening the
# browser through kde-open, for one), so start the unwrapped binary when there is one.
JAVA="$(command -v java)"
JAVA="$(readlink -f "$JAVA")"
WRAPPED="$(dirname "$JAVA")/.java-wrapped"
[[ -x "$WRAPPED" ]] && JAVA="$WRAPPED"

if [[ "${1:-}" == "--debug" ]]; then
    shift
    echo "Waiting for debugger to attach on port ${DEBUG_PORT}..."
    exec "$JAVA" -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT} -Dtamboui.backend=panama,jline3 -jar "$JAR" "$@"
else
    exec "$JAVA" -Dtamboui.backend=panama -jar "$JAR" "$@"
fi
