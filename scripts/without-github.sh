#!/usr/bin/env bash
# Builds jazygit without GitHub (ADR 171): in a copy of the repository, deletes GitHub's modules — features-github,
# core-gh and jazygit-github-app-tests —, the lines of the poms that list them and the line of GitHubFeature.FEATURE
# in Features.ALL, and runs ./mvnw verify on what is left. The repository itself is left as it is.
#
#   scripts/without-github.sh [DIRECTORY] [MAVEN ARGUMENTS...]
#
# DIRECTORY is where the copy goes, by default a new temporary directory; it is made anew. The Maven arguments go to
# ./mvnw after verify, as -o or -T1C.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
copy="${1:-$(mktemp -d)/jazygit-without-github}"
shift || true

rm -rf "$copy"
mkdir -p "$copy"
# What git tracks, as it is in the working tree: no build output, nothing ignored.
(cd "$root" && git ls-files -z --cached --others --exclude-standard) \
    | (cd "$root" && xargs -0 cp --parents -t "$copy" 2>/dev/null || true)
cd "$copy"

rm -rf jazygit-features/features-github jazygit-core/core-gh jazygit-github-app-tests
for pom in $(find . -name pom.xml -not -path "*/target/*"); do
    python3 "$root/scripts/pom_strip.py" "$pom" features-github core-gh jazygit-github-app-tests
done

features=jazygit-app/src/main/java/net/vanfleteren/jazygit/app/keys/Features.java
sed -i -e '/^import net\.vanfleteren\.jazygit\.features\.github\.GitHubFeature;$/d' -e '/^ *GitHubFeature\.FEATURE,$/d' \
    "$features"
if grep -q "GitHubFeature" "$features"; then
    echo "Features.java still names GitHubFeature: the line to delete is not one of its own" >&2
    exit 1
fi

echo "Building jazygit without GitHub in $copy"
./mvnw -B verify "$@"
