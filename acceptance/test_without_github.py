"""GitHub applies to a repository only with a remote on GitHub and gh found (ADR 171): otherwise nothing of it shows,
but its section in the settings."""

from pathlib import Path

# What GitHub shows when it applies: its screen, named in the status panel, and a key of the branches pane in the
# help, which shows the keys of the panel.
SCREEN = "PRs"
BRANCH_KEY = "Open branch on GitHub"


def help_of_branches(ui) -> str:
    """The text of the help of the branches pane, with main selected; the help is closed again."""
    ui.branches.select("main")
    ui.app.press("?")
    ui.app.wait_for("Keybindings")
    text = ui.app.text()
    ui.app.press("escape")
    ui.app.wait_gone("Keybindings")
    return text


def settings_text(ui) -> str:
    """The text of the settings panel; it is closed again."""
    ui.app.press("ctrl+s")
    ui.app.wait_for("gh executable")
    text = ui.app.text()
    ui.app.press("escape")
    ui.app.wait_gone("gh executable")
    return text


def pushed(repo):
    repo.commit("Initial commit", **{"README.md": "# Repo\n"})
    repo("push", "--quiet", "origin", "main")


def test_github_shows_with_a_remote_on_github_and_gh(repo, origin, gh, jazygit):
    pushed(repo)
    ui = jazygit()
    ui.app.wait_for(SCREEN)

    assert BRANCH_KEY in help_of_branches(ui)


def test_nothing_of_github_shows_without_a_remote_on_github(repo, tmp_path, env, gh, jazygit):
    bare = tmp_path / "elsewhere.git"
    repo("init", "--quiet", "--bare", "--initial-branch=main", str(bare))
    repo("remote", "add", "origin", str(bare))
    pushed(repo)
    ui = jazygit()
    ui.caught_up()

    assert_no_github(ui)


def test_nothing_of_github_shows_without_gh(repo, tmp_path, env, jazygit):
    bare = tmp_path / "origin.git"
    repo("init", "--quiet", "--bare", "--initial-branch=main", str(bare))
    on_github = f"https://github.com/{tmp_path.name}/origin.git"
    repo("config", f"url.{bare}.insteadOf", on_github)
    repo("remote", "add", "origin", on_github)
    pushed(repo)
    config = Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"
    config.write_text(config.read_text() + f'\n[github]\nexecutable = "{tmp_path / "no-gh"}"\n')
    ui = jazygit()
    ui.caught_up()

    assert_no_github(ui)


def assert_no_github(ui):
    """What a GitHub repository shows is not there; the panes are, and the settings with GitHub's section. The screen
    is looked for again last, once GitHub had all that time to be found."""
    # ctrl+p switches to GitHub's screen only when GitHub applies.
    ui.app.press("ctrl+p")
    assert SCREEN not in ui.app.text()
    assert BRANCH_KEY not in help_of_branches(ui)
    assert "GitHub" in settings_text(ui)
    assert SCREEN not in ui.app.text()
