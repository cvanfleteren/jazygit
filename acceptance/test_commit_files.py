"""The files of a commit, a stash or a branch as a tree in the files pane, and the rich view of a diff (ADR 44, 61)."""

from harness import eventually


def test_the_files_of_a_commit_show_in_the_files_pane_each_with_its_own_diff(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "src/two.txt": "two\n"})
    repo.commit("Change both", **{"one.txt": "one\nmore\n", "src/two.txt": "two\nother\n"})
    repo.write("work.txt", "work\n")
    ui = jazygit()
    ui.commits.select("Change both")
    short = repo.short("HEAD")

    ui.app.press("enter")

    ui.app.wait_in("Files", "two.txt")
    assert f"Files of {short}" in ui.app.text()
    assert "work.txt" not in ui.app.pane_text("Files")
    # The files may be listed already, following the highlight (ADR 141): Enter moves the focus a frame later.
    ui.app.eventually(lambda: ui.app.focused_pane() == "Files", "the focus in the files")
    ui.app.move_cursor("Files", "two.txt")
    ui.app.wait_for(f"{short} · src/two.txt")
    assert "+other" in ui.app.text()
    assert "+more" not in ui.app.text()

    ui.app.press("escape")

    # Back on the commits, the files still follow the highlighted one (ADR 141).
    ui.app.eventually(lambda: ui.app.focused_pane() == "Commits", "the focus back on the commits")
    assert f"Files of {short}" in ui.app.text()
    assert "work.txt" not in ui.app.pane_text("Files")


def test_the_files_follow_the_highlighted_commit_without_taking_the_focus(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.write("work.txt", "work\n")
    ui = jazygit()

    ui.commits.select("Add two")

    ui.app.wait_in("Files", "two.txt")
    assert f"Files of {repo.short('HEAD')}" in ui.app.text()
    assert ui.app.focused_pane() == "Commits"

    ui.commits.select("Add one")

    ui.app.wait_in("Files", "one.txt")
    assert "two.txt" not in ui.app.pane_text("Files")
    assert ui.app.focused_pane() == "Commits"

    # The branch checked out lists the files of the working tree.
    ui.branches.select("main")

    ui.app.wait_in("Files", "work.txt")
    assert "Files of" not in ui.app.text()


def test_the_files_follow_the_highlighted_branch_against_its_base(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("checkout", "-q", "-b", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("checkout", "-q", "main")
    ui = jazygit()

    ui.branches.select("feature")

    ui.app.wait_in("Files", "two.txt")
    assert "Files of feature · vs main" in ui.app.text()
    assert "one.txt" not in ui.app.pane_text("Files")
    assert ui.app.focused_pane() == "Branches"


def test_the_main_branch_not_checked_out_lists_what_its_tip_changed(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("checkout", "-q", "-b", "feature")
    ui = jazygit()

    ui.branches.select("main")

    ui.app.wait_in("Files", "two.txt")
    assert f"Files of main · {repo.short('main')}" in ui.app.text()
    assert "one.txt" not in ui.app.pane_text("Files")


def test_the_files_of_a_stash_include_its_untracked_files(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("new.txt", "brand new\n")
    repo("stash", "push", "--include-untracked", "--message", "work")
    ui = jazygit()
    ui.stash.select("work")

    ui.app.press("enter")

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

    ui.show_rich_diffs()

    ui.app.wait_for("modified  one.txt")
    assert "1   │ - count = 1" in ui.app.text()
    assert "  1 │ + count = 2" in ui.app.text()
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
    # The files may be listed already, following the highlight (ADR 141): F moves the focus a frame later.
    ui.app.eventually(lambda: ui.app.focused_pane() == "Files", "the focus in the files")

    ui.app.press("escape")

    ui.app.eventually(lambda: ui.app.focused_pane() == "Branches", "the focus back on the branches")
