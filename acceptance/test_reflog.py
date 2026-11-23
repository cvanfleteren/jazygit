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

    # The rows are drawn first; the lost commit is marked once the look for lost commits is done.
    ui.app.wait_in("Commits", "✗")
    rows = ui.app.pane_text("Commits").splitlines()
    marked = [row for row in rows if "✗" in row]
    assert marked and all(dropped in row for row in marked), rows
    assert any("Add dropped" in row for row in marked), rows

    ui.app.move_cursor("Commits", dropped)

    # The content panel says what the entry is above its commit.
    ui.app.wait_for("✗ Lost: no branch, tag or remote has this commit")
    ui.app.wait_for("commit: Add dropped")
