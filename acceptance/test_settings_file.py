"""The settings file opens in git's editor from the settings, and what changed in it applies (ADR 111)."""

from pathlib import Path


def config(env) -> Path:
    return Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"


def editor(tmp_path: Path, command: str) -> Path:
    """An editor that runs command on the file it is given, as "$1", and exits."""
    script = tmp_path / "editor"
    script.write_text(f"#!/bin/sh\n{command}\n")
    script.chmod(0o755)
    return script


def appending_editor(tmp_path: Path, text: str) -> Path:
    """An editor that adds text to the file it is given, and exits."""
    return editor(tmp_path, f"printf '{text}' >> \"$1\"")


def test_the_settings_file_opens_in_the_editor_and_applies_once_it_is_closed(repo, tmp_path, env, jazygit):
    env["GIT_EDITOR"] = str(appending_editor(tmp_path, "animations = false\\n"))
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")
    ui.app.wait_for("Settings file")
    ui.app.wait_for("(m) Animations: on")
    ui.app.press("e")

    ui.app.wait_for("(m) Animations: off")
    assert "animations = false" in config(env).read_text()


def test_a_missing_settings_file_is_made_to_be_edited(repo, tmp_path, env, jazygit):
    config(env).unlink()
    env["GIT_EDITOR"] = str(appending_editor(tmp_path, "[ui]\\nanimations = false\\n"))
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")
    ui.app.wait_for("(m) Animations: on")
    ui.app.press("e")

    ui.app.wait_for("(m) Animations: off")
    assert config(env).read_text() == "[ui]\nanimations = false\n"


def test_an_edit_made_elsewhere_applies_when_the_settings_open(repo, env, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")

    with config(env).open("a") as file:
        file.write("animations = false\n")
    ui.app.press("ctrl+s")

    ui.app.wait_for("(m) Animations: off")


def test_a_value_that_is_not_valid_keeps_the_one_in_use_and_says_so(repo, tmp_path, env, jazygit):
    env["GIT_EDITOR"] = str(editor(tmp_path, "sed -i 's/animations = false/animations = \"sometimes\"/' \"$1\""))
    repo.commit("Add one", **{"one.txt": "one\n"})
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")
    ui.app.wait_for("(m) Animations: on")
    ui.app.press("m")
    ui.app.wait_for("(m) Animations: off")
    ui.app.eventually(lambda: "animations = false" in config(env).read_text(), "the setting to be kept")
    ui.app.press("e")

    ui.app.wait_for("invalid values for ui.animations")
    assert "(m) Animations: off" in ui.app.text()
    assert 'animations = "sometimes"' in config(env).read_text()
