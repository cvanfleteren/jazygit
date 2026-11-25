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


def open_reflog(ui):
    """Focuses the commits pane and shows its Reflog tab, once it lists entries."""
    ui.app.focus("Commits")
    ui.app.press("]")
    ui.app.wait_in("Commits", "first commit")


def test_a_checkout_outside_the_app_that_keeps_heads_commit_shows_while_the_reflog_is_open(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("branch", "other")
    ui = jazygit()
    ui.app.wait_for("Add one")
    open_reflog(ui)

    # HEAD's commit stays the same, so the log is not read again: the stamp of the reflog tells (ADR 186).
    repo("checkout", "-q", "other")

    ui.app.wait_in("Commits", "main → other")


def test_a_branch_at_the_entry_before_a_drop_brings_the_dropped_commit_back(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    before = repo.short("HEAD")
    ui = jazygit()
    ui.commits.drop("Add two")
    assert "Add two" not in repo.subjects()
    open_reflog(ui)

    # The entry of the commit HEAD was at before the drop (ADR 194).
    ui.app.move_cursor("Commits", before)
    ui.app.press("n")
    ui.enter_text("New branch from", "rescued")

    ui.app.eventually(lambda: repo.current_branch() == "rescued", "rescued checked out")
    assert repo.subjects("rescued") == ["Add three", "Add two", "Add one"]
    # The tab stays shown, and reads the checkout of the new branch.
    ui.app.wait_in("Commits", "main → rescued")


def test_a_branch_at_the_entry_head_left_a_deleted_branch_from_is_named_after_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("checkout", "-q", "-b", "feature")
    repo.commit("Add feature", **{"feature.txt": "feature\n"})
    tip = repo("rev-parse", "HEAD")
    repo("checkout", "-q", "main")
    repo("branch", "-q", "-D", "feature")
    ui = jazygit()
    ui.app.wait_for("Add one")
    open_reflog(ui)

    # The entry just older than "checkout: moving from feature to main" is where feature stood.
    ui.app.move_cursor("Commits", repo.short(tip))
    ui.app.press("n")
    ui.app.wait_for("New branch from")
    # Confirmed as it starts out: the name of the deleted branch.
    ui.app.press("enter")

    ui.app.eventually(lambda: repo.current_branch() == "feature", "feature recreated and checked out")
    assert repo("rev-parse", "feature") == tip


def test_reset_to_an_entry_and_check_one_out(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    one = repo("rev-parse", "HEAD")
    repo.commit("Add two", **{"two.txt": "two\n"})
    two = repo("rev-parse", "HEAD")
    ui = jazygit()
    ui.app.wait_for("Add two")
    open_reflog(ui)

    ui.app.move_cursor("Commits", repo.short(one))
    ui.app.press("g")
    # Hard, on a clean tree, which asks nothing more: a mixed reset would leave two.txt in the way of the checkout.
    ui.confirm("Reset to", key="h")

    ui.app.eventually(lambda: repo("rev-parse", "main") == one, "main reset to Add one")
    assert repo.current_branch() == "main"

    # Add two is only in the reflog now; checking it out is detached (ADR 137).
    ui.app.wait_in("Commits", "reset")
    ui.app.move_cursor("Commits", repo.short(two))
    ui.app.press("space")

    ui.app.eventually(lambda: repo.current_branch() == "" and repo("rev-parse", "HEAD") == two, "Add two checked out")
