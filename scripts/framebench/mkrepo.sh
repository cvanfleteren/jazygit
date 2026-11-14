#!/usr/bin/env bash
# A test repository with uncommitted changes to a Java file, a Markdown file and a JSON file.
set -e
SRC=$(cd "$(dirname "$0")/../.." && pwd)
R=${FRAMEBENCH_DIR:-/tmp/jazygit-framebench}/benchrepo
rm -rf "$R"
mkdir -p "$R"
cd "$R"
git init -q -b main
git config user.email bench@example.com
git config user.name Bench
cp "$SRC/jazygit-app/src/main/java/net/vanfleteren/jazygit/ui/diff/RichDiff.java" Code.java
cat "$SRC"/documentation/adr/010[0-9]-*.md > README.md
python3 -c '
import json
data = {"items": [{"id": i, "name": "item %d" % i, "tags": ["a", "b", "c"], "nested": {"x": i, "y": [i, i + 1]}} for i in range(150)]}
open("data.json", "w").write(json.dumps(data, indent=2))
'
git add -A
git commit -q -m "base"
for i in $(seq 1 30); do git commit -q --allow-empty -m "commit $i"; done
# Changes spread through each file.
sed -i '0~40s/$/ \/\/ changed/' Code.java
sed -i '0~25s/$/ (changed)/' README.md
python3 -c '
import json
d = json.load(open("data.json"))
for item in d["items"][::10]:
    item["name"] += " changed"
open("data.json", "w").write(json.dumps(d, indent=2))
'
git status --short
wc -l Code.java README.md data.json
