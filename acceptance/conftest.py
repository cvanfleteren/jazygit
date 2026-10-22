import os
from pathlib import Path

import pytest

from harness import App, Git, isolated_env
from pages import Jazygit

ROOT = Path(__file__).resolve().parent.parent


def pytest_addoption(parser):
    parser.addoption("--jar", help="the jazygit fat jar to test; defaults to the newest in jazygit-app/target")


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
def jazygit(java, jar, repo, env):
    """Starts jazygit on the repository, and returns its page object; call it once the repository is set up."""
    apps = []

    def start() -> Jazygit:
        app = App([java, "--enable-native-access=ALL-UNNAMED", "-Dtamboui.backend=panama", "-jar", str(jar), str(repo.path)],
                  repo.path, env)
        apps.append(app)
        return Jazygit(app, repo)

    yield start
    for app in apps:
        app.close()
