"""+ and - make the focused pane of the left column taller or shorter, Z maximizes it (ADR 92), and Z on the content
collapses the left column (ADR 104)."""

from pathlib import Path

import pytest


def config(env) -> Path:
    return Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"


def rows(ui, pane: str) -> int:
    """How many rows the pane of the left column shows inside its border."""
    return len(ui.app.pane_rows(pane))


@pytest.mark.parametrize("animations", ["on", "off"])
def test_plus_and_minus_make_the_focused_pane_taller_and_shorter(repo, env, jazygit, animations):
    if animations == "off":
        with config(env).open("a") as file:
            file.write("animations = false\n")
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")
    ui.app.focus("Branches")
    # 20 percent of the 44 rows of the left column, borders included.
    ui.app.eventually(lambda: rows(ui, "Branches") == 7, "the branches 9 rows tall")

    ui.app.press("+")

    # 25 percent, taken from the commits under them.
    ui.app.eventually(lambda: rows(ui, "Branches") == 9, "the branches 11 rows tall")
    ui.app.eventually(lambda: "repository-heights = [25, 25, 25]" in config(env).read_text(), "the heights kept")

    ui.app.press("-")

    ui.app.eventually(lambda: rows(ui, "Branches") == 7, "the branches 9 rows tall again")


def test_z_maximizes_the_focused_pane_until_z_on_it_again(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")
    ui.app.focus("Commits")
    ui.app.eventually(lambda: rows(ui, "Files") == 9, "the files 11 rows tall")

    ui.app.press("Z")

    # The others a row in a border each: 44 - 3 - 3 * 3 = 32 rows for the commits.
    ui.app.eventually(lambda: rows(ui, "Commits") == 30, "the commits maximized")
    assert rows(ui, "Files") == 1
    assert rows(ui, "Branches") == 1

    ui.app.focus("Files")
    assert rows(ui, "Commits") == 30, "the commits stay maximized when the focus moves"
    ui.app.focus("Commits")
    ui.app.press("Z")

    ui.app.eventually(lambda: rows(ui, "Files") == 9, "the panes as they were")


def test_z_on_the_content_collapses_the_left_column_until_the_focus_goes_back(repo, jazygit):
    """Z on the content panel collapses the left column to an empty panel marked », and leaving the content for the
    pane opens it again (ADR 104)."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")
    ui.app.focus("Commits")
    ui.app.press("right")

    ui.app.press("Z")

    ui.app.eventually(lambda: ui.app.text().startswith("╭»╮╭"), "the left column collapsed")
    assert "Commits" not in ui.app.panes()

    ui.app.press("escape")

    ui.app.eventually(lambda: ui.app.focused_pane() == "Commits", "the column open, the focus back on the commits")
