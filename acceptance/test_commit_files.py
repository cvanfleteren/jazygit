"""The files of a commit, a stash or a branch as a tree in the files pane, and the rich view of a diff (ADR 44, 61)."""

from harness import eventually


def test_the_files_of_a_commit_show_in_the_files_pane_each_with_its_own_diff(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "src/two.txt": "two\n"})
    repo.commit("Change both", **{"one.txt": "one\nmore\n", "src/two.txt": "two\nother\n"})
    repo.write("work.txt", "work\n")
    ui = jazygit()
    ui.commits.select("Change both")
    short = repo.short("HEAD")

    ui.app.press("F")

    ui.app.wait_in("Files", "two.txt")
    assert f"Files of {short}" in ui.app.text()
    assert "work.txt" not in ui.app.pane_text("Files")
    assert ui.app.focused_pane() == "Files"
    ui.app.move_cursor("Files", "two.txt")
    ui.app.wait_for(f"{short} · src/two.txt")
    assert "+other" in ui.app.text()
    assert "+more" not in ui.app.text()

    ui.app.press("escape")

    ui.app.wait_in("Files", "work.txt")
    ui.app.eventually(lambda: ui.app.focused_pane() == "Commits", "the focus back on the commits")


def test_the_files_of_a_stash_include_its_untracked_files(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("new.txt", "brand new\n")
    repo("stash", "push", "--include-untracked", "--message", "work")
    ui = jazygit()
    ui.stash.select("work")

    ui.app.press("F")

    ui.app.wait_in("Files", "new.txt")
    ui.app.move_cursor("Files", "new.txt")
    ui.app.wait_for("stash@{0} · new.txt")
    assert "+brand new" in ui.app.text()
    assert "+changed" not in ui.app.text()


def test_ctrl_d_draws_the_changes_of_a_commit_rich_and_keeps_that(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "count = 1\n"})
    repo.commit("Change one", **{"one.txt": "count = 2\n"})
    ui = jazygit()
    ui.commits.select("Change one")
    ui.app.wait_for("diff --git a/one.txt b/one.txt")

    ui.app.press("ctrl+d")

    ui.app.wait_for("modified  one.txt")
    assert "1   │ -count = 1" in ui.app.text()
    assert "  1 │ +count = 2" in ui.app.text()
    assert "diff --git" not in ui.app.text()
    ui.app.press("q")
    eventually(lambda: ui.app.pump() or ui.app.exited, "jazygit to quit")

    again = jazygit()
    again.commits.select("Change one")
    again.app.wait_for("modified  one.txt")


def test_f_on_a_branch_without_a_pull_request_shows_its_files_against_its_base(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("checkout", "-q", "-b", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("checkout", "-q", "main")
    # The base moved on after the branch left it: that is no change of the branch (ADR 61).
    repo.commit("Change one", **{"one.txt": "one\nmore\n"})
    ui = jazygit()
    ui.branches.select("feature")

    ui.app.press("F")

    ui.app.wait_in("Files", "two.txt")
    assert "Files of feature · vs main" in ui.app.text()
    assert "one.txt" not in ui.app.pane_text("Files")
    assert ui.app.focused_pane() == "Files"

    ui.app.press("escape")

    ui.app.eventually(lambda: ui.app.focused_pane() == "Branches", "the focus back on the branches")
