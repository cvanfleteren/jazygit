"""The reflog of HEAD, read only, in a tab of the commits pane (ADR 186)."""


def test_the_reflog_tab_lists_where_head_has_been_and_follows_an_operation(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("checkout", "-q", "-b", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    ui = jazygit()
    ui.app.wait_for("Add two")
    ui.app.focus("Commits")

    ui.app.press("]")

    ui.app.wait_in("Commits", "main → feature")
    assert ui.app.underlined_in_title("Commits") == "Reflog"
    rows = ui.app.pane_text("Commits")
    assert "commit        Add two" in rows, rows
    assert "first commit  Add one" in rows, rows
    # The newest entry is at the commit HEAD is at.
    assert rows.splitlines()[0].lstrip("> ").startswith("◆"), rows

    # Not through the page's checkout, which waits for the commits pane to show the log.
    ui.app.focus("Branches")
    ui.app.move_cursor("Branches", "main")
    ui.branches.wait_for_log("main")
    ui.app.press("space")

    # Read again after the operation, while the tab is still shown.
    ui.app.wait_in("Commits", "feature → main")
    assert repo.current_branch() == "main"


def test_a_commit_only_the_reflog_keeps_is_marked_lost(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("checkout", "-q", "-b", "tmp")
    repo.commit("Add dropped", **{"dropped.txt": "dropped\n"})
    dropped = repo.short("HEAD")
    repo("checkout", "-q", "main")
    repo("branch", "-q", "-D", "tmp")
    ui = jazygit()
    ui.app.wait_for("Add one")
    ui.app.focus("Commits")

    ui.app.press("]")

    # The page comes with what keeps its commits (ADR 188).
    ui.app.wait_in("Commits", "✗")
    rows = ui.app.pane_text("Commits").splitlines()
    marked = [row for row in rows if "✗" in row]
    assert marked and all(dropped in row for row in marked), rows
    assert any("Add dropped" in row for row in marked), rows

    ui.app.move_cursor("Commits", dropped)

    # The content panel says what the entry is above its commit.
    ui.app.wait_for("✗ Lost: no branch, tag, remote or HEAD has this commit")
    ui.app.wait_for("commit: Add dropped")


def test_on_a_detached_head_the_commits_behind_it_are_kept_by_head_not_lost(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("checkout", "-q", "--detach")
    repo.commit("Add first", **{"first.txt": "first\n"})
    first = repo.short("HEAD")
    repo.commit("Add second", **{"second.txt": "second\n"})
    ui = jazygit()
    ui.app.wait_for("Add second")
    ui.app.focus("Commits")

    ui.app.press("]")

    ui.app.wait_in("Commits", "commit        Add first")
    ui.app.move_cursor("Commits", first)

    ui.app.wait_for("only HEAD, which is past it")
    assert "✗" not in ui.app.pane_text("Commits"), ui.app.pane_text("Commits")


def test_a_checkout_outside_the_app_that_keeps_heads_commit_shows_while_the_reflog_is_open(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("branch", "other")
    ui = jazygit()
    ui.app.wait_for("Add one")
    ui.app.focus("Commits")
    ui.app.press("]")
    ui.app.wait_in("Commits", "first commit")

    # HEAD's commit stays the same, so the log is not read again: the stamp of the reflog tells (ADR 186).
    repo("checkout", "-q", "other")

    ui.app.wait_in("Commits", "main → other")
