"""The code of a diff in the rich view, coloured by its syntax (ADR 53)."""

from pathlib import Path

from pages import FilesPane


def staged_java_change(repo, jazygit, env, settings=""):
    """Starts jazygit with the ui settings settings on a staged change to a Java file, and shows it rich."""
    repo.commit("Add a class", **{"A.java": "class A {}\n"})
    repo.write("A.java", "class A { int count; }\n")
    repo("add", "A.java")
    if settings:
        config = Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text("[ui]\n" + settings)
    ui = jazygit()
    ui.files.select(FilesPane.ALL)
    ui.app.wait_for("diff --git a/A.java b/A.java")
    ui.app.press("ctrl+d")
    ui.app.wait_for("modified  A.java")
    return ui


def test_a_keyword_of_a_changed_line_has_a_colour_of_its_own(repo, jazygit, env):
    ui = staged_java_change(repo, jazygit, env)

    assert ui.app.colour_at("int count") != ui.app.colour_at("int count", len("int "))


def test_with_the_syntax_turned_off_a_changed_line_has_one_colour(repo, jazygit, env):
    ui = staged_java_change(repo, jazygit, env, "diff-syntax = false\n")

    assert ui.app.colour_at("int count") == ui.app.colour_at("int count", len("int "))
