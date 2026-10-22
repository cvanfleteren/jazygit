import fcntl
import hashlib
import os
import shutil
from pathlib import Path

import pytest

from harness import App, Git, eventually, isolated_env
from pages import Jazygit

ROOT = Path(__file__).resolve().parent.parent


def pytest_addoption(parser):
    parser.addoption("--jar", help="the jazygit fat jar to test; defaults to the newest in jazygit-app/target")


# Last, after -k and -m have deselected, so that the slices are those of the tests that run.
@pytest.hookimpl(trylast=True)
def pytest_collection_modifyitems(config, items):
    """Starts each slow test first, on a worker of its own: started late, or after another slow one, they would keep
    the run going long after the others are done.

    worksteal hands each worker an even, contiguous slice of the tests, and an idle worker steals from the end of
    another's slice, so a slow test at the head of a slice runs first and the rest of the slice moves elsewhere."""
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


# The app reads the status again every 100ms instead of 750ms, so that it sees sooner what a test wrote.
JVM_OPTIONS = ["--enable-native-access=ALL-UNNAMED", "-Dtamboui.backend=panama", "-Djazygit.refreshInterval=100"]


@pytest.fixture(scope="session")
def class_archive(java, jar, tmp_path_factory) -> list:
    """The JVM options that start the app from a class data archive of this jar and java, made once, next to the jar:
    starting a dozen apps at once, the JVMs are slow to load and verify the classes each for itself.

    The first worker to get here makes it, from an app that starts and quits, while the others wait; the apps only
    ever read a whole archive, since a JVM that writes one while others map it can crash them."""
    java_path = os.path.realpath(shutil.which(java))
    key = hashlib.sha256(f"{java_path} {jar.stat().st_mtime_ns} {jar.stat().st_size}".encode())
    archive = jar.with_name(f"{jar.stem}-{key.hexdigest()[:12]}.jsa")
    with open(jar.with_name(f"{jar.stem}.jsa.lock"), "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not archive.exists():
            for stale in jar.parent.glob(f"{jar.stem}-*.jsa"):
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
            try:
                Jazygit(app, repo).caught_up()
                app.press("q")
                eventually(lambda: app.pump() or app.exited, "jazygit to quit and write the class data archive")
            finally:
                app.close()
            # A JDK without an archive of its own (or run with -Xshare:off) writes none, and runs as it is.
            if made.exists():
                made.replace(archive)
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
