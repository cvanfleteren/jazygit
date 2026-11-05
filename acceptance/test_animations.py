"""The left column slides to the width < and > step it to, unless the animations are turned off (ADR 81)."""

from pathlib import Path

import pytest


def config(env) -> Path:
    return Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"


def content_edge(ui) -> int:
    """The column the content panel starts at: the second top-left corner of the first row."""
    row = ui.app.text().splitlines()[0]
    return row.find("╭", row.find("╭") + 1)


def test_the_animations_are_turned_off_in_the_settings_and_kept(repo, env, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")
    ui.app.wait_for("(m) Animations: on")
    ui.app.press("m")

    ui.app.wait_for("(m) Animations: off")
    ui.app.eventually(lambda: "animations = false" in config(env).read_text(), "the setting to be kept")


@pytest.mark.parametrize("animations", ["on", "off"])
def test_a_step_makes_the_left_column_wider_with_the_animations_on_or_off(repo, env, jazygit, animations):
    if animations == "off":
        with config(env).open("a") as file:
            file.write("animations = false\n")
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")
    # 30 percent of the 160 columns of the screen.
    ui.app.eventually(lambda: content_edge(ui) == 48, "the left column 48 columns wide")

    ui.app.press(">")

    # 35 percent: where it slides to, or goes at once.
    ui.app.eventually(lambda: content_edge(ui) == 56, "the left column 56 columns wide")
