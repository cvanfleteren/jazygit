"""Fetching from the remotes by hand, with f, while the app does not fetch by itself (ADR 125)."""

import shutil
from pathlib import Path


def without_auto_fetch(env):
    """Turns fetching at the start and every minute off in the settings file, so that only f fetches."""
    config = Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"
    with config.open("a") as file:
        file.write("\n[git]\nauto-fetch = false\n")


def pushed_by_a_teammate(repo, origin, clone):
    """Main with one commit, pushed to origin, which tracks it; a teammate pushed one more since, not fetched."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    teammate = clone()
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")


def test_f_in_the_files_fetches_what_a_teammate_pushed(repo, origin, clone, env, jazygit):
    without_auto_fetch(env)
    pushed_by_a_teammate(repo, origin, clone)
    ui = jazygit()
    ui.files.focus()
    assert repo.sync("main") == ""

    ui.app.press("f")

    ui.app.eventually(lambda: repo.sync("main") == "behind 1", "main to be fetched")
    ui.caught_up()
    ui.app.wait_for("fetched")


def test_f_in_the_branches_fetches_what_a_teammate_pushed(repo, origin, clone, env, jazygit):
    without_auto_fetch(env)
    pushed_by_a_teammate(repo, origin, clone)
    ui = jazygit()
    ui.branches.select("main")
    assert repo.sync("main") == ""

    ui.app.press("f")

    ui.app.eventually(lambda: repo.sync("main") == "behind 1", "main to be fetched")
    ui.caught_up()
    ui.app.wait_for("fetched")


def test_a_fetch_that_fails_says_so_in_the_line_at_the_bottom(repo, origin, env, jazygit):
    without_auto_fetch(env)
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    shutil.rmtree(origin.path)
    ui = jazygit()
    ui.files.focus()

    ui.app.press("f")

    ui.app.wait_for("last fetch failed")
