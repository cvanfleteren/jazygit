"""Operations that stop on conflicts: merging, rebasing, cherry-picking, reverting, pulling and rewriting commits. The
app leaves them stopped; the conflicts are resolved with git, and the operation continued, skipped or aborted from
the app (M)."""

import pytest


def diverged(repo):
    """Main and a branch feature that each change one.txt since they parted, feature checked out."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Change one on feature", **{"one.txt": "feature\n"})
    repo("switch", "main")
    repo.commit("Change one on main", **{"one.txt": "main\n"})
    repo("switch", "feature")


def test_a_merge_that_stops_on_conflicts_is_continued_once_they_are_resolved(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    ui = jazygit()

    ui.branches.merge("feature")

    assert repo.in_progress() == "merge"
    assert repo.conflicts() == ["one.txt"]

    repo.resolve("one.txt", "both\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.subjects()[0] == "Merge branch 'feature'"
    assert repo.parents() == 2
    assert repo("show", "HEAD:one.txt") == "both"
    assert repo.status() == {}


def test_abort_a_merge_brings_the_branch_back_as_it_was(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    before = repo("rev-parse", "main")
    ui = jazygit()
    ui.branches.merge("feature", mode="merge commit")
    assert repo.in_progress() == "merge"

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == before
    assert repo.read("one.txt") == "main\n"
    assert repo.status() == {}


def test_continue_is_refused_while_conflicts_remain(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    ui = jazygit()
    ui.branches.merge("feature")

    ui.try_to_continue()

    ui.app.wait_for("1 file still has conflicts")
    assert repo.in_progress() == "merge"
    assert repo.conflicts() == ["one.txt"]


def test_a_merge_cannot_be_skipped(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    ui = jazygit()
    ui.branches.merge("feature")

    ui.try_to_skip()
    # The dialog is still open, so continuing is chosen from it, and refused.
    ui.app.press("c")

    ui.app.wait_for("1 file still has conflicts")
    assert "Skip a commit of the merge" not in ui.app.text()
    assert repo.in_progress() == "merge"


def test_a_squash_merge_that_stops_on_conflicts_is_committed_once_they_are_resolved(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    ui = jazygit()

    ui.branches.merge("feature", mode="squash and commit")

    # Git keeps no merge in progress for a squash merge: what it merged is only in the working tree.
    ui.app.wait_for("nothing was committed")
    assert repo.in_progress() is None
    assert repo.conflicts() == ["one.txt"]
    assert repo.subjects() == ["Change one on main", "Add one"]

    repo.resolve("one.txt", "both\n")
    ui.files.commit("Squash feature")

    assert repo.subjects() == ["Squash feature", "Change one on main", "Add one"]
    assert repo.parents() == 1
    assert repo("show", "HEAD:one.txt") == "both"


def test_a_rebase_that_stops_on_conflicts_is_continued_once_they_are_resolved(repo, jazygit):
    diverged(repo)
    repo.commit("Add two", **{"two.txt": "two\n"})
    ui = jazygit()

    ui.branches.rebase_onto("main")

    assert repo.in_progress() == "rebase"
    assert repo.conflicts() == ["one.txt"]

    repo.resolve("one.txt", "both\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "feature"
    assert repo.subjects() == ["Add two", "Change one on feature", "Change one on main", "Add one"]
    assert repo.read("one.txt") == "both\n"


def test_a_rebase_continued_stops_again_on_the_conflicts_of_a_later_commit(repo, jazygit):
    diverged(repo)
    repo.commit("Change one again on feature", **{"one.txt": "feature again\n"})
    ui = jazygit()
    ui.branches.rebase_onto("main")

    repo.resolve("one.txt", "both\n")
    ui.continue_in_progress()

    assert repo.in_progress() == "rebase"
    assert repo.conflicts() == ["one.txt"]
    assert repo.subjects() == ["Change one on feature", "Change one on main", "Add one"]

    repo.resolve("one.txt", "both again\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "feature"
    assert repo.subjects() == ["Change one again on feature", "Change one on feature", "Change one on main", "Add one"]
    assert repo.read("one.txt") == "both again\n"


def test_skip_the_commit_a_rebase_stopped_at_leaves_it_out(repo, jazygit):
    diverged(repo)
    repo.commit("Add two", **{"two.txt": "two\n"})
    ui = jazygit()
    ui.branches.rebase_onto("main")

    ui.skip_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "feature"
    assert repo.subjects() == ["Add two", "Change one on main", "Add one"]
    assert repo.read("one.txt") == "main\n"


def test_skip_the_first_of_two_conflicting_commits_stops_again_on_the_second(repo, jazygit):
    diverged(repo)
    repo.commit("Change one again on feature", **{"one.txt": "feature again\n"})
    ui = jazygit()
    ui.branches.rebase_onto("main")
    first = repo("rev-parse", "REBASE_HEAD")

    ui.skip_in_progress()

    # Stopped on the same file, from the same HEAD: only the commit it stopped at tells.
    assert repo.in_progress() == "rebase"
    assert repo.conflicts() == ["one.txt"]
    assert repo("rev-parse", "REBASE_HEAD") != first

    repo.resolve("one.txt", "both\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "feature"
    assert repo.subjects() == ["Change one again on feature", "Change one on main", "Add one"]


def test_abort_a_rebase_brings_the_branch_and_the_uncommitted_changes_back(repo, jazygit):
    diverged(repo)
    before = repo("rev-parse", "feature")
    repo.write("two.txt", "uncommitted\n")
    repo("add", "two.txt")
    ui = jazygit()
    ui.branches.rebase_onto("main")
    assert repo.in_progress() == "rebase"

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "feature"
    assert repo("rev-parse", "feature") == before
    assert repo.read("one.txt") == "feature\n"
    assert repo.read("two.txt") == "uncommitted\n"
    assert repo.stashes() == []


def test_a_cherry_pick_that_stops_on_conflicts_is_continued_once_they_are_resolved(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.commits.copy("Change one on feature")
    ui.branches.checkout("main")

    ui.commits.cherry_pick()

    assert repo.in_progress() == "cherry-pick"
    assert repo.conflicts() == ["one.txt"]

    repo.resolve("one.txt", "both\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.subjects() == ["Change one on feature", "Change one on main", "Add one"]
    assert repo("show", "HEAD:one.txt") == "both"
    assert repo.subjects("feature") == ["Change one on feature", "Add one"]


def test_abort_a_cherry_pick_brings_the_branch_back_as_it_was(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.commits.copy("Change one on feature")
    ui.branches.checkout("main")
    before = repo("rev-parse", "main")
    ui.commits.cherry_pick()

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == before
    assert repo.read("one.txt") == "main\n"
    assert repo.status() == {}


def test_skip_the_commit_a_cherry_pick_stopped_at_leaves_the_branch_as_it_was(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.commits.copy("Change one on feature")
    ui.branches.checkout("main")
    before = repo("rev-parse", "main")
    ui.commits.cherry_pick()

    ui.skip_in_progress()

    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == before
    assert repo.read("one.txt") == "main\n"
    assert repo.status() == {}


def changed_twice(repo):
    """Main, on which one.txt is added, then changed, then changed again."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Change one", **{"one.txt": "changed\n"})
    repo.commit("Change one again", **{"one.txt": "changed again\n"})


def test_a_revert_that_stops_on_conflicts_is_continued_once_they_are_resolved(repo, jazygit):
    changed_twice(repo)
    ui = jazygit()

    ui.commits.revert("Change one")

    assert repo.in_progress() == "revert"
    assert repo.conflicts() == ["one.txt"]

    repo.resolve("one.txt", "one\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.subjects() == ['Revert "Change one"', "Change one again", "Change one", "Add one"]
    assert repo("show", "HEAD:one.txt") == "one"


def test_abort_a_revert_brings_the_branch_back_as_it_was(repo, jazygit):
    changed_twice(repo)
    before = repo("rev-parse", "main")
    ui = jazygit()
    ui.commits.revert("Change one")

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == before
    assert repo.read("one.txt") == "changed again\n"
    assert repo.status() == {}


def test_skip_the_commit_a_revert_stopped_at_leaves_the_branch_as_it_was(repo, jazygit):
    changed_twice(repo)
    before = repo("rev-parse", "main")
    ui = jazygit()
    ui.commits.revert("Change one")

    ui.skip_in_progress()

    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == before
    assert repo.read("one.txt") == "changed again\n"
    assert repo.status() == {}


def test_a_drop_that_stops_on_conflicts_is_continued_once_they_are_resolved(repo, jazygit):
    changed_twice(repo)
    ui = jazygit()

    # Without it, the change after it no longer applies.
    ui.commits.drop("Change one")

    assert repo.in_progress() == "rebase"
    assert repo.conflicts() == ["one.txt"]

    repo.resolve("one.txt", "changed again\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "main"
    assert repo.subjects() == ["Change one again", "Add one"]


def test_abort_a_drop_that_stops_on_conflicts_keeps_the_commit(repo, jazygit):
    changed_twice(repo)
    before = repo("rev-parse", "main")
    ui = jazygit()
    ui.commits.drop("Change one")

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "main"
    assert repo("rev-parse", "main") == before
    assert repo.status() == {}


@pytest.mark.xfail(strict=True, reason="the amend commits the staged changes as a fixup! commit before it rebases, "
                                       "and aborting the rebase leaves that commit on the branch")
def test_abort_an_amend_that_stops_on_conflicts_keeps_the_changes_staged(repo, jazygit):
    changed_twice(repo)
    before = repo("rev-parse", "main")
    repo.write("one.txt", "amended\n")
    repo("add", "one.txt")
    ui = jazygit()
    # The staged change is to what the last commit made of one.txt, which the older one does not have.
    ui.commits.amend("Add one")
    assert repo.in_progress() == "rebase"

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "main"
    assert repo("rev-parse", "main") == before
    assert repo.status() == {"one.txt": "M "}


def published(repo, origin, clone):
    """Main with one.txt, pushed to origin, which a teammate changed and pushed since; fetched."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    teammate = clone()
    teammate.commit("Change one, theirs", **{"one.txt": "theirs\n"})
    teammate("push")
    repo("fetch")


def test_a_pull_that_stops_on_conflicts_while_rebasing_is_continued_once_they_are_resolved(repo, origin, clone,
                                                                                            jazygit):
    published(repo, origin, clone)
    repo("config", "pull.rebase", "true")
    repo.commit("Change one, mine", **{"one.txt": "mine\n"})
    ui = jazygit()

    ui.branches.pull("main")

    assert repo.in_progress() == "rebase"
    assert repo.conflicts() == ["one.txt"]

    repo.resolve("one.txt", "both\n")
    ui.continue_in_progress()

    assert repo.in_progress() is None
    assert repo.current_branch() == "main"
    assert repo.subjects() == ["Change one, mine", "Change one, theirs", "Add one"]
    assert repo.sync("main") == "ahead 1"


def test_abort_a_pull_that_stops_on_conflicts_while_merging(repo, origin, clone, jazygit):
    published(repo, origin, clone)
    repo("config", "pull.rebase", "false")
    repo.commit("Change one, mine", **{"one.txt": "mine\n"})
    before = repo("rev-parse", "main")
    ui = jazygit()
    ui.branches.pull("main")
    assert repo.in_progress() == "merge"

    ui.abort_in_progress()

    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == before
    assert repo.read("one.txt") == "mine\n"
    assert repo.sync("main") == "ahead 1, behind 1"
