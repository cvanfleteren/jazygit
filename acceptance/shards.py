"""The shares of the tests that the machines of CI run, by how long each test took on main.

Every machine works out all the shares from the same durations and runs its own; a machine that saw other durations
would work out other shares, and run tests another runs too, or leave some out. So CI hands them all one file.

    python acceptance/shards.py merge OLD MEASURED... -o NEW

writes the durations main's build keeps: those just measured, each moved only part of the way from what the test took
before, so that one slow machine doesn't make the next split worse."""
import argparse
import json
from pathlib import Path

# How far a test's duration moves towards what it took this time.
WEIGHT = 0.3


def shares(tests: list[str], durations: dict[str, float], count: int) -> list[list[str]]:
    """The tests split in count shares that take about as long: each test, the longest first, goes to the share that
    takes least until then, of those the one with fewest tests. A test without a duration counts as the average one;
    without any durations, the I-th share takes every count-th test."""
    if not durations:
        return [tests[index::count] for index in range(count)]
    average = sum(durations.values()) / len(durations)
    longest_first = sorted(tests, key=lambda test: -durations.get(test, average))
    split: list[list[str]] = [[] for _ in range(count)]
    totals = [0.0] * count
    for test in longest_first:
        least = min(range(count), key=lambda share: (totals[share], len(split[share])))
        split[least].append(test)
        totals[least] += durations.get(test, average)
    order = {test: position for position, test in enumerate(tests)}
    return [sorted(share, key=order.__getitem__) for share in split]


def merged(old: dict[str, float], measured: dict[str, float]) -> dict[str, float]:
    """The durations of the tests just measured, each moved WEIGHT of the way from its old one; a test that didn't run
    is gone, and a new one takes what it measured."""
    return {test: round(old[test] + WEIGHT * (seconds - old[test]) if test in old else seconds, 2)
            for test, seconds in sorted(measured.items())}


def read(path: Path) -> dict[str, float]:
    """The durations in path, none when there is no such file."""
    return json.loads(path.read_text()) if path.is_file() else {}


def main() -> None:
    parser = argparse.ArgumentParser(description="merges the durations of a build's shares into those kept for main")
    parser.add_argument("command", choices=["merge"])
    parser.add_argument("old", type=Path)
    parser.add_argument("measured", type=Path, nargs="+")
    parser.add_argument("-o", "--output", type=Path, required=True)
    arguments = parser.parse_args()
    measured = {test: seconds for path in arguments.measured for test, seconds in read(path).items()}
    arguments.output.write_text(json.dumps(merged(read(arguments.old), measured), indent=1) + "\n")


if __name__ == "__main__":
    main()
