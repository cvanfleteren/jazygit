"""Counts the frames jazygit draws, and those that change nothing on the screen, in a few views.

Each scenario starts the app afresh on the bench repository, goes to its view, lets it settle, then measures an idle
window and a window of cursor moves. The counts come from FrameStats (a line per second), the frame times from the
JFR events TamboUI records for each draw.

Build the fat jar first (./mvnw -DskipTests package), make the repository with mkrepo.sh, then, from the dev shell:
    python scripts/framebench/framebench.py ["diff Code.java" ...]
FRAMEBENCH_DIR (default /tmp/jazygit-framebench) holds the repository and the results; BENCH_PROPS adds -D options.
"""
import os
import re
import subprocess
import sys
import time
from pathlib import Path

WT = Path(__file__).resolve().parents[2]
TMP = Path(os.environ.get("FRAMEBENCH_DIR", "/tmp/jazygit-framebench"))
REPO = TMP / "benchrepo"
sys.path.insert(0, str(WT / "acceptance"))
from harness import App  # noqa: E402

JAVA = os.environ.get("JAVA", "java")
JAR = WT / "jazygit-app/target/jazygit-0.1.0-SNAPSHOT-fat.jar"
IDLE = 20
MOVES = 20
MOVE_EVERY = 0.25


def pumped_sleep(app, seconds):
    end = time.time() + seconds
    while time.time() < end:
        app.pump()
        time.sleep(0.05)


def at_second_start(app):
    pumped_sleep(app, 1 - (time.time() % 1) + 0.01)
    return int(time.time())


SCENARIOS = {
    # name: (keys to get there, text on screen once there, keys of a cursor move)
    "files, all diffs": ([], "Code.java", "down"),
    "commits": (["focus:Commits"], "commit 30", "down"),
    "diff Code.java": (["down", "right"], "modified  Code.java", "down"),
    "rendered README.md": (["down", "down", "ctrl+t", "right"], "rendered", "down"),
    "rendered data.json": (["down", "down", "down", "ctrl+t", "right", "j", "enter"], "rendered", "j"),
}


def run(name, keys, ready, move):
    slug = re.sub(r"[^a-z]+", "-", name.lower()).strip("-")
    stats = TMP / f"stats-{slug}.txt"
    jfr = TMP / f"rec-{slug}.jfr"
    stats.unlink(missing_ok=True)
    env = {k: v for k, v in os.environ.items() if k != "GH_TOKEN"}
    env["XDG_CONFIG_HOME"] = str(TMP / "xdg")
    env["TERM"] = "xterm-256color"
    app = App([JAVA, *os.environ.get("BENCH_PROPS", "").split(), f"-Djazygit.frameStats={stats}", f"-XX:StartFlightRecording=filename={jfr},settings=profile",
               "-jar", str(JAR)], REPO, env)
    app.wait_for("Code.java", timeout=30)
    app.focus("Files")
    for k in keys:
        if k.startswith("focus:"):
            app.focus(k[6:])
        else:
            app.press(k)
        pumped_sleep(app, 0.5)
    app.wait_for(ready, timeout=15)
    pumped_sleep(app, 3)
    idle_from = at_second_start(app)
    pumped_sleep(app, IDLE)
    idle_to = int(time.time())
    moves_from = at_second_start(app)
    for _ in range(MOVES):
        app.press(move)
        pumped_sleep(app, MOVE_EVERY - 0.05)
    pumped_sleep(app, 1)
    moves_to = at_second_start(app)
    screen = app.text()
    app.press("q")
    pumped_sleep(app, 3)
    return slug, stats, jfr, (idle_from, idle_to), (moves_from, moves_to), screen


def window(stats, span):
    rows = []
    for line in stats.read_text().splitlines():
        fields = dict(f.split("=") for f in line.split()[1:])
        second = int(line.split()[0])
        if span[0] <= second < span[1]:
            rows.append({k: float(v) for k, v in fields.items()})
    total = {k: sum(r[k] for r in rows) for k in ("frames", "changing", "needless", "cells", "renderMs")}
    return len(rows), total


def jfr_frames(jfr, span):
    """The durations in ms of the TamboUI draws, whole frames, that start in span (wall-clock seconds)."""
    import datetime
    out = subprocess.run(["jfr", "print", "--events", "dev.tamboui.terminal.draw", str(jfr)],
                         capture_output=True, text=True).stdout
    durations = []
    for event in out.split("dev.tamboui.terminal.draw {")[1:]:
        start = re.search(r"startTime = (\d+):(\d+):([\d.]+) \((\S+)\)", event)
        duration = re.search(r"duration = ([\d.]+) (ms|us|s|ns)", event)
        if not (start and duration):
            continue
        h, mnt, sec, day = start.groups()
        at = datetime.datetime.fromisoformat(f"{day}T{int(h):02d}:{int(mnt):02d}:00").timestamp() + float(sec)
        if span[0] <= at < span[1]:
            value, unit = float(duration.group(1)), duration.group(2)
            durations.append(value * {"ms": 1, "us": 1e-3, "ns": 1e-6, "s": 1e3}[unit])
    return durations


def described(durations):
    if not durations:
        return "no draws recorded"
    d = sorted(durations)
    return (f"{len(d)} draws, median {d[len(d) // 2]:.1f} ms, p95 {d[int(len(d) * 0.95)]:.1f} ms,"
            f" total {sum(d):.0f} ms")


def main():
    only = sys.argv[1:] or list(SCENARIOS)
    for name in only:
        keys, ready, move = SCENARIOS[name]
        slug, stats, jfr, idle, moves, screen = run(name, keys, ready, move)
        n, t = window(stats, idle)
        m, u = window(stats, moves)
        print(f"== {name}")
        print(f"  idle {n}s: {t['frames'] / n:.2f} frames/s, needless {t['needless'] / n:.2f}/s,"
              f" render() {t['renderMs'] / max(t['frames'], 1):.1f} ms/frame")
        print(f"    whole frames: {described(jfr_frames(jfr, idle))}")
        print(f"  {MOVES} moves over {m}s: {u['frames']:.0f} frames ({u['frames'] / MOVES:.2f}/move),"
              f" needless {u['needless']:.0f}, render() {u['renderMs'] / max(u['frames'], 1):.1f} ms/frame")
        print(f"    whole frames: {described(jfr_frames(jfr, moves))}")
        (TMP / f"screen-{slug}.txt").write_text(screen)
        sys.stdout.flush()


main()
