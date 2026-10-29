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
    assert "1   │ -count = 1" in text
    assert "  1 │ +count = 2" in text
    assert "modified  two.txt" in text
    assert "1   │ -size = 1" in text
    assert "added  new.txt" in text
    assert "  1 │ +brand new" in text
    assert "diff --git" not in text
