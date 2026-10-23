"""
Starting jazygit outside a repository: a dialog asks what to open instead (ADR 28).
"""
import pytest

from conftest import JVM_OPTIONS
from harness import App, Git, eventually
from pages import Jazygit


@pytest.fixture
def outside(java, jar, class_archive, tmp_path, env):
    """Starts jazygit on the folder path, which is in no repository, and returns the running app."""
    apps = []

    def start(path) -> App:
        app = App([java, *class_archive, *JVM_OPTIONS, "-jar", str(jar), str(path)], tmp_path, env)
        apps.append(app)
        return app

    yield start
    for app in apps:
        app.close()


def folder(tmp_path, name="notes"):
    path = tmp_path / name
    path.mkdir()
    return path


def test_init_makes_the_folder_a_repository_and_opens_it(tmp_path, env, outside):
    path = folder(tmp_path)
    app = outside(path)
    app.wait_for("is not inside a git repository")

    app.press("i")

    repo = Git(path, env)
    eventually(lambda: (path / ".git").is_dir(), "the folder to be a repository")
    Jazygit(app, repo).caught_up()
    assert not repo.has_head()


def test_another_folder_inside_a_repository_opens_it(tmp_path, env, outside):
    repo = Git(folder(tmp_path, "project"), env)
    repo("init", "--initial-branch=main")
    repo.commit("Initial commit", **{"README.md": "# Project\n"})
    app = outside(folder(tmp_path))
    app.wait_for("is not inside a git repository")

    app.press("o")
    ui = Jazygit(app, repo)
    ui.enter_text("Open the folder", "../project")

    ui.caught_up()


def test_a_recent_repository_opens(tmp_path, env, outside):
    repo = Git(folder(tmp_path, "project"), env)
    repo("init", "--initial-branch=main")
    repo.commit("Initial commit", **{"README.md": "# Project\n"})
    first = outside(repo.path)
    Jazygit(first, repo).caught_up()
    first.press("q")
    eventually(lambda: first.pump() or first.exited, "jazygit to quit")
    app = outside(folder(tmp_path))
    app.wait_for("is not inside a git repository")

    app.press("r")
    app.wait_for("Recent repositories")
    app.press("enter")

    Jazygit(app, repo).caught_up()


def test_without_recent_repositories_none_are_offered(tmp_path, outside):
    app = outside(folder(tmp_path))
    app.wait_for("is not inside a git repository")

    assert "(r)" not in app.text()


def test_a_path_to_no_folder_is_said_and_can_be_corrected(tmp_path, env, outside):
    app = outside(folder(tmp_path))
    app.wait_for("is not inside a git repository")

    app.press("o")
    Jazygit(app, None).enter_text("Open the folder", "typo")
    app.wait_for("There is no folder")

    app.press("escape")
    app.wait_for("is not inside a git repository")


def test_a_path_git_refuses_is_said_and_can_be_corrected(tmp_path, env, outside):
    Git(tmp_path, env)("init", "--bare", "--initial-branch=main", str(tmp_path / "bare.git"))
    app = outside(folder(tmp_path))
    app.wait_for("is not inside a git repository")

    app.press("o")
    Jazygit(app, None).enter_text("Open the folder", "../bare.git")
    app.wait_for("has no working tree")
    assert "../bare.git" in app.text(), "the path stays as it was typed"

    app.press("escape")
    app.wait_for("is not inside a git repository")


def test_another_folder_in_no_repository_is_asked_about(tmp_path, env, outside):
    other = folder(tmp_path, "other")
    app = outside(folder(tmp_path))
    app.wait_for("notes is not inside a git repository")

    app.press("o")
    Jazygit(app, None).enter_text("Open the folder", "../other")
    app.wait_for("other is not inside a git repository")

    app.press("i")
    eventually(lambda: (other / ".git").is_dir(), "the other folder to be a repository")


def test_a_path_that_does_not_exist_cannot_be_made_a_repository(tmp_path, outside):
    app = outside(tmp_path / "missing")
    app.wait_for("missing does not exist")
    assert "git init" not in app.text()

    app.press("i")
    app.press("q")
    eventually(lambda: app.pump() or app.exited, "jazygit to quit")
    assert not (tmp_path / "missing").exists()


@pytest.mark.parametrize("key", ["q", "escape", "ctrl+c"])
def test_quitting_ends_jazygit_without_an_error(tmp_path, outside, key):
    app = outside(folder(tmp_path))
    app.wait_for("is not inside a git repository")

    app.press(key)

    eventually(lambda: app.pump() or app.exited, "jazygit to quit")
    assert app.process.exitstatus == 0
    assert not (tmp_path / "notes" / ".git").exists()
