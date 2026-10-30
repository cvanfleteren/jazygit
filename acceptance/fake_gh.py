"""A stand-in for gh that answers what a test told it to, so that the app talks to no GitHub.

The test writes its answers to the JSON file FAKE_GH_ANSWERS names: a list of [text, answer] pairs. A call whose
arguments, joined by spaces, contain the text of a pair prints that pair's answer, the first that matches; any other
call fails, as gh does outside a GitHub repository. Each call is logged, as its arguments, one JSON list per line, to
the file FAKE_GH_LOG names."""

import json
import os
import sys


def main(args: list) -> int:
    with open(os.environ["FAKE_GH_LOG"], "a") as log:
        log.write(json.dumps(args) + "\n")
    if args[:1] == ["--version"]:
        print("gh version 2.80.0 (2026-01-01)")
        return 0
    with open(os.environ["FAKE_GH_ANSWERS"]) as file:
        answers = json.load(file)
    joined = " ".join(args)
    for text, answer in answers:
        if text in joined:
            print(answer)
            return 0
    print("none of the git remotes configured for this repository point to a known GitHub host", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
