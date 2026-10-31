import argparse
import fcntl
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path

import pytest

from harness import App, Git, eventually, isolated_env
from pages import Jazygit

ROOT = Path(__file__).resolve().parent.parent


def pytest_addoption(parser):
    parser.addoption("--jar", help="the jazygit fat jar to test; defaults to the newest in jazygit-app/target")
    parser.addoption("--shard", type=shard, metavar="I/N",
                     help="runs only the I-th of N shares of the tests, so that N machines share them")


def shard(value: str) -> tuple[int, int]:
    """The I and N of I/N, refused unless 1 <= I <= N: another share would run the wrong tests, or none."""
    try:
        index, count = (int(part) for part in value.split("/"))
    except ValueError:
        raise argparse.ArgumentTypeError(f"{value!r} is not I/N, such as 1/4") from None
    if not 1 <= index <= count:
        raise argparse.ArgumentTypeError(f"{value!r} has no share {index} of {count}: I goes from 1 to N")
    return index, count


# Last, after -k and -m have deselected, so that the slices are those of the tests that run.
@pytest.hookimpl(trylast=True)
def pytest_collection_modifyitems(config, items):
    """Starts each slow test first, on a worker of its own: started late, or after another slow one, they would keep
    the run going long after the others are done.

    worksteal hands each worker an even, contiguous slice of the tests, and an idle worker steals from the end of
    another's slice, so a slow test at the head of a slice runs first and the rest of the slice moves elsewhere.

    A shard takes every N-th test, so that the tests of a file, and the slow ones next to each other, spread over
    the shards."""
    shard = config.getoption("--shard")
    if shard:
        index, count = shard
        config.hook.pytest_deselected(items=[item for i, item in enumerate(items) if i % count != index - 1])
        items[:] = [item for i, item in enumerate(items) if i % count == index - 1]
    workers = getattr(config, "workerinput", {}).get("workercount", 1)
    slow = [item for item in items if item.get_closest_marker("slow")]
    rest = [item for item in items if not item.get_closest_marker("slow")]
    heads = [slow[i::workers] for i in range(workers)]
    ordered, remaining = [], len(items)
    for i, head in enumerate(heads):
        size = remaining // (workers - i)
        fill = max(size - len(head), 0)
        ordered += head + rest[:fill]
        del rest[:fill]
        remaining -= size
    items[:] = ordered + rest


@pytest.fixture(scope="session")
def jar(request) -> Path:
    given = request.config.getoption("--jar")
    if given:
        return Path(given).resolve()
    jars = sorted((ROOT / "jazygit-app" / "target").glob("jazygit-*-fat.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        pytest.exit("no jar in jazygit-app/target: run ./mvnw -DskipTests package, or pass --jar", returncode=2)
    return jars[-1]


@pytest.fixture(scope="session")
def java() -> str:
    """JAZYGIT_JAVA, else the java in JAVA_HOME, else java on the PATH."""
    if "JAZYGIT_JAVA" in os.environ:
        return os.environ["JAZYGIT_JAVA"]
    if "JAVA_HOME" in os.environ:
        return str(Path(os.environ["JAVA_HOME"]) / "bin" / "java")
    return "java"


# The app reads the status again every 100ms instead of every second, so that it sees sooner what a test wrote.
JVM_OPTIONS = ["--enable-native-access=ALL-UNNAMED", "-Dtamboui.backend=panama", "-Djazygit.refreshInterval=100"]


@pytest.fixture(scope="session")
def class_archive(java, jar, tmp_path_factory) -> list:
    """The JVM options that start the app from a class data archive of this jar and java, made once, next to the jar:
    starting a dozen apps at once, the JVMs are slow to load and verify the classes each for itself.

    The first worker to get here makes it, from an app that starts and quits, while the others wait; the apps only
    ever read a whole archive, since a JVM that writes one while others map it can crash them. One that could not be
    made is not tried again by the others, for this jar: on a runner too busy for it, each try waits a whole timeout."""
    java_path = os.path.realpath(shutil.which(java))
    key = hashlib.sha256(f"{java_path} {jar.stat().st_mtime_ns} {jar.stat().st_size}".encode())
    archive = jar.with_name(f"{jar.stem}-{key.hexdigest()[:12]}.jsa")
    failed = archive.with_suffix(".none")
    with open(jar.with_name(f"{jar.stem}.jsa.lock"), "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not archive.exists() and not failed.exists():
            for stale in [*jar.parent.glob(f"{jar.stem}-*.jsa"), *jar.parent.glob(f"{jar.stem}-*.none")]:
                stale.unlink()
            made = archive.with_suffix(".partial")
            path = tmp_path_factory.mktemp("archive")
            env = isolated_env(path)
            repo = Git(path / "repo", env)
            repo.path.mkdir()
            repo("init", "--initial-branch=main")
            repo.commit("Initial commit", **{"README.md": "# Archive\n"})
            app = App([java, f"-XX:ArchiveClassesAtExit={made}", *JVM_OPTIONS, "-jar", str(jar), str(repo.path)],
                      repo.path, env)
            written = False
            try:
                Jazygit(app, repo).caught_up()
                app.press("q")
                eventually(lambda: app.pump() or app.exited, "jazygit to quit and write the class data archive")
                written = True
            except AssertionError as e:
                # The archive only makes the apps start sooner: one the JVM did not get to write, as when it started
                # too slowly on a busy runner, fails no test. The apps run without it, and a test of a slow start
                # still fails on its own.
                print(f"no class data archive: {e}", file=sys.stderr)
            finally:
                app.close()
            # A JDK without an archive of its own (or run with -Xshare:off) writes none, and runs as it is.
            if written and made.exists():
                made.replace(archive)
            else:
                made.unlink(missing_ok=True)
                if not written:
                    failed.touch()
    if not archive.exists():
        return []
    # The JVM tells on stdout, so on the app's screen, where an option differs from the JDK's own archive.
    return [f"-XX:SharedArchiveFile={archive}", "-Xlog:cds=off"]


@pytest.fixture
def env(tmp_path) -> dict:
    home = tmp_path / "home"
    home.mkdir()
    return isolated_env(home)


@pytest.fixture
def repo(tmp_path, env) -> Git:
    """An empty repository on branch main."""
    path = tmp_path / "repo"
    path.mkdir()
    git = Git(path, env)
    git("init", "--initial-branch=main")
    return git


@pytest.fixture
def origin(tmp_path, env, repo) -> Git:
    """An empty bare repository, the remote origin of the repository; push to it to give the branches an
    upstream."""
    path = tmp_path / "origin.git"
    path.mkdir()
    git = Git(path, env)
    git("init", "--bare", "--initial-branch=main")
    repo("remote", "add", "origin", str(path))
    return git


@pytest.fixture
def clone(tmp_path, env, origin):
    """Clones origin, as it is when called, the way someone else working on it would: their commits reach the
    repository by being pushed to origin."""
    def make(name="teammate") -> Git:
        git = Git(tmp_path / name, env)
        Git(tmp_path, env)("clone", str(origin.path), str(git.path))
        return git

    return make


class FakeGh:
    """gh as the app finds it on the PATH: fake_gh.py, answering what it is told to (see there)."""

    def __init__(self, path: Path, env: dict):
        bin_dir = path / "bin"
        bin_dir.mkdir()
        self.answers_file = path / "answers.json"
        self.log_file = path / "calls.log"
        self.answers_file.write_text("[]")
        self.log_file.write_text("")
        gh = bin_dir / "gh"
        gh.write_text(f"#!/bin/sh\nexec {sys.executable} {ROOT / 'acceptance' / 'fake_gh.py'} \"$@\"\n")
        gh.chmod(0o755)
        env["PATH"] = f"{bin_dir}{os.pathsep}{env.get('PATH', '')}"
        env["FAKE_GH_ANSWERS"] = str(self.answers_file)
        env["FAKE_GH_LOG"] = str(self.log_file)

    def answer(self, *pairs):
        """Answers each call that contains the text of a pair with its answer, a string or what json makes of it,
        after the seconds a third element of the pair says, if it has one; the pairs given before go after these."""
        given = json.loads(self.answers_file.read_text())
        added = [[text, answer if isinstance(answer, str) else json.dumps(answer), *wait]
                 for text, answer, *wait in pairs]
        self.answers_file.write_text(json.dumps(added + given))

    def calls(self) -> list:
        """The calls so far, each the list of its arguments."""
        return [json.loads(line) for line in self.log_file.read_text().splitlines() if line]


@pytest.fixture
def gh(tmp_path, env) -> FakeGh:
    """A fake gh on the PATH of the app; ask for it before starting the app."""
    path = tmp_path / "gh"
    path.mkdir()
    return FakeGh(path, env)


@pytest.fixture
def jazygit(java, jar, class_archive, repo, env):
    """Starts jazygit on the repository, with the options args, and returns its page object; call it once the
    repository is set up."""
    apps = []

    def start(*args) -> Jazygit:
        app = App([java, *class_archive, *JVM_OPTIONS, "-jar", str(jar), *args, str(repo.path)], repo.path, env)
        apps.append(app)
        return Jazygit(app, repo)

    yield start
    for app in apps:
        app.close()
