"""The staged and unstaged changes of the working tree in the rich view (ADR 47)."""

from pages import FilesPane


def test_ctrl_d_draws_the_staged_and_unstaged_changes_rich(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "count = 1\n", "two.txt": "size = 1\n"})
    repo.write("one.txt", "count = 2\n")
    repo("add", "one.txt")
    repo.write("two.txt", "size = 2\n")
    repo.write("new.txt", "brand new\n")
    ui = jazygit()
    ui.files.select(FilesPane.ALL)
    ui.app.wait_for("diff --git a/two.txt b/two.txt")

    ui.app.press("ctrl+d")

    ui.app.wait_for("modified  one.txt")
    text = ui.app.text()
    assert "Staged changes" in text
    assert "Unstaged changes" in text
    assert "1   │ - count = 1" in text
    assert "  1 │ + count = 2" in text
    assert "modified  two.txt" in text
    assert "1   │ - size = 1" in text
    assert "added  new.txt" in text
    assert "  1 │ + brand new" in text
    assert "diff --git" not in text


def test_the_diff_with_the_focus_is_bordered_like_the_pane_with_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "two\n")
    ui = jazygit()
    ui.files.select("one.txt")
    ui.app.wait_for("╭Unstaged changes")
    focused = ui.app.panes()["Files"]
    assert ui.app.colour_at("╭Unstaged changes") != focused

    ui.app.press("enter")

    ui.app.eventually(lambda: ui.app.colour_at("╭Unstaged changes") == focused, "the diff to be bordered as focused")
    assert ui.app.focused_pane() is None


def test_a_removed_line_is_followed_by_the_line_it_became(repo, jazygit):
    """Changed lines are shown in pairs (ADR 83)."""
    repo.commit("Add versions", **{"versions.txt": "checkstyle = 14.3.0\nerrorprone = 2.51.0\nnative = 1.1.17\n"})
    repo.write("versions.txt", "checkstyle = 14.4.0\nerrorprone = 2.52.0\nnative = 1.1.18\n")
    ui = jazygit()
    ui.files.select("versions.txt")
    ui.app.wait_for("diff --git a/versions.txt b/versions.txt")

    ui.app.press("ctrl+d")

    ui.app.wait_for("modified  versions.txt")
    rows = ui.app.text().splitlines()
    order = [
        "1   │ - checkstyle = 14.3.0",
        "  1 │ + checkstyle = 14.4.0",
        "2   │ - errorprone = 2.51.0",
        "  2 │ + errorprone = 2.52.0",
        "3   │ - native = 1.1.17",
        "  3 │ + native = 1.1.18",
    ]
    found = [next(at for at, row in enumerate(rows) if line in row) for line in order]
    assert found == sorted(found), ui.app.text()


def test_ctrl_t_shows_the_new_version_of_a_markdown_file_rendered(repo, jazygit):
    """A version of Markdown rendered (ADR 103)."""
    repo.commit("Add readme", **{"README.md": "# Title\n\nOld text.\n\n- one\n"})
    repo.write("README.md", "# Title\n\nNew **text**.\n\n- one\n")
    ui = jazygit()
    ui.files.select("README.md")
    ui.app.wait_for("diff --git a/README.md b/README.md")
    ui.app.press("ctrl+d")
    ui.app.wait_for("modified  README.md")
    ui.app.press("enter")

    ui.app.press("ctrl+t")

    ui.app.wait_for("new version  README.md")
    text = ui.app.text()
    assert "· new version, rendered" in text
    assert "▎ New text." in text
    assert "# Title" not in text
    assert "Old text." not in text

    ui.app.press("ctrl+t")

    # As source again, the version of ADR 102: the theme tints the changed line, so its marker is blank.
    ui.app.wait_for("3 │   New **text**.")
    assert "new version, rendered" not in ui.app.text()
