"""Operations that stop on conflicts: merging, rebasing, cherry-picking, reverting, pulling and rewriting commits. The
app leaves them stopped; the conflicts are resolved with git, or in the app a file at a time (ADR 198), and the
operation continued, skipped or aborted from the app (M)."""

import shutil
import subprocess

import pytest

# The dev shell has mergiraf; where it is not installed, the tests that need it are skipped (ADR 201).
needs_mergiraf = pytest.mark.skipif(shutil.which("mergiraf") is None, reason="mergiraf is not installed")


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


def test_a_rebase_is_resolved_by_keeping_the_side_of_what_it_rebases_onto(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")

    # In a rebase, HEAD's side is main's: git's ours.
    ui.files.keep("one.txt", "ours")

    assert repo.conflicts() == []
    assert repo.read("one.txt") == "main\n"
    ui.app.wait_for("1 of 1 resolved")
    ui.continue_in_progress()
    assert repo.in_progress() is None
    assert repo.read("one.txt") == "main\n"


def test_a_merge_is_resolved_by_keeping_the_side_of_the_merged_branch(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    ui = jazygit()
    ui.branches.merge("feature")

    ui.files.keep("one.txt", "theirs")

    assert repo.conflicts() == []
    assert repo.read("one.txt") == "feature\n"
    assert repo.status() == {"one.txt": "M "}


def test_the_resolve_dialog_names_the_sides_and_keeps_one(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    ui.files.select("one.txt")
    ui.app.press("r")
    ui.app.wait_for("Keep main's version")
    # The commit being rebased, by its sha, as the rebase's steps name it (ADR 199).
    ui.app.wait_for(f"Keep {repo.short(repo.find('Change one on feature', 'feature'))}'s version")
    ui.app.press("escape")

    ui.files.resolve("one.txt", "t")

    assert repo.read("one.txt") == "feature\n"
    assert repo.conflicts() == []


def test_enter_on_a_file_in_conflict_tells_what_each_choice_does_and_keeps_one(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    ui.files.select("one.txt")

    ui.app.press("enter")
    ui.app.wait_for("Resolve one.txt")
    # Every choice says what it does and the command it runs, in the dialog (ADR 200).
    ui.app.wait_for("git checkout --ours -- one.txt && git add -- one.txt")
    ui.app.wait_for("git checkout --theirs -- one.txt && git add -- one.txt")
    assert ui.app.text().count("Takes the file as it is there") == 2, ui.app.text()
    ui.app.press("escape")

    ui.changing_repo("one.txt resolved", lambda: (
        ui.app.press("enter"), ui.confirm("Resolve one.txt", key="t")))

    assert repo.read("one.txt") == "feature\n"
    assert repo.conflicts() == []


def test_the_resolve_dialog_opens_on_a_conflict_a_stash_left(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "stashed\n")
    repo("stash", "push")
    repo.commit("Change one", **{"one.txt": "committed\n"})
    with pytest.raises(subprocess.CalledProcessError):
        repo("stash", "pop")
    assert repo.in_progress() is None
    assert repo.conflicts() == ["one.txt"]
    ui = jazygit()

    ui.files.select("one.txt")
    ui.app.press("r")
    ui.app.wait_for("Keep main's version")
    ui.app.wait_for("Keep the incoming side's version")
    ui.app.press("t")

    ui.app.eventually(lambda: repo.conflicts() == [], "the stash's side was kept")
    assert repo.read("one.txt") == "stashed\n"


def test_a_side_that_deleted_the_file_deletes_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Change one on feature", **{"one.txt": "feature\n"})
    repo("switch", "main")
    repo("rm", "-q", "one.txt")
    repo("commit", "-qm", "Remove one on main")
    repo("switch", "feature")
    ui = jazygit()
    ui.branches.rebase_onto("main")
    assert repo.conflicts() == ["one.txt"]

    ui.files.keep("one.txt", "ours")

    assert repo.conflicts() == []
    assert repo.read("one.txt") is None


def test_a_resolved_conflict_is_brought_back(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    ui.files.keep("one.txt", "theirs")

    ui.files.restore("one.txt")

    assert repo.conflicts() == ["one.txt"]
    assert "<<<<<<< " in repo.read("one.txt")


def test_a_conflict_resolved_to_heads_side_stays_listed_and_is_brought_back(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    # In a rebase, HEAD's side is main's: once kept, git lists no change for the file.
    ui.files.keep("one.txt", "ours")
    assert repo.status() == {}

    # It stays listed among the changed files, so it is found without listing all files (ADR 198).
    ui.app.wait_for("one.txt ✓")
    ui.files.restore("one.txt")

    assert repo.conflicts() == ["one.txt"]
    assert "<<<<<<< " in repo.read("one.txt")


def test_staging_a_file_with_conflict_markers_asks_first(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    ui.files.select("one.txt")
    ui.app.press("space")

    ui.app.wait_for("one.txt still has conflict markers")
    assert repo.conflicts() == ["one.txt"]
    ui.changing_repo("one.txt staged anyway", lambda: ui.app.press("s"))

    assert repo.conflicts() == []
    assert "<<<<<<< " in repo.read("one.txt")


def test_staging_a_file_in_conflict_without_markers_stages_it(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    repo.write("one.txt", "both\n")

    ui.files.stage("one.txt")

    assert repo.conflicts() == []
    assert repo.read("one.txt") == "both\n"


def test_continuing_with_conflict_markers_staged_says_it_commits_them(repo, jazygit):
    diverged(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    repo("add", "one.txt")

    ui.caught_up()
    ui.app.press("M")

    ui.app.wait_for("Continue, with conflict markers in 1 file")
    ui.app.wait_for("one.txt still has conflict markers")


def python_conflicts(repo):
    """Main and feature that each added an import to imports.py, which mergiraf puts together, and changed the same
    line of value.py, which it cannot; main checked out."""
    repo.commit("Add the modules", **{"imports.py": "import a\n\n\ndef f():\n    return 1\n",
                                       "value.py": "def f():\n    return 1\n"})
    repo("switch", "--create", "feature")
    repo.commit("Change them on feature", **{"imports.py": "import a\nimport b\n\n\ndef f():\n    return 1\n",
                                              "value.py": "def f():\n    return 3\n"})
    repo("switch", "main")
    repo.commit("Change them on main", **{"imports.py": "import a\nimport c\n\n\ndef f():\n    return 1\n",
                                           "value.py": "def f():\n    return 2\n"})


def test_the_merge_tool_takes_a_file_in_conflict_and_git_stages_it(repo, jazygit):
    diverged(repo)
    repo("config", "merge.tool", "theirs")
    repo("config", "mergetool.theirs.cmd", 'cp "$REMOTE" "$MERGED"')
    repo("config", "mergetool.theirs.trustExitCode", "true")
    ui = jazygit()
    ui.branches.rebase_onto("main")
    ui.files.select("one.txt")

    ui.app.press("enter")
    ui.app.wait_for("g  Merge it in git's merge tool")
    ui.app.wait_for("git -c mergetool.keepBackup=false mergetool -- one.txt")
    ui.app.press("escape")
    ui.changing_repo("one.txt merged in the merge tool", lambda: (
        ui.app.press("enter"), ui.app.wait_for("g  Merge it in git's merge tool"), ui.app.press("g")))

    # In a rebase, the merge tool's REMOTE is the commit being rebased.
    assert repo.read("one.txt") == "feature\n"
    assert repo.conflicts() == []
    assert not (repo.path / "one.txt.orig").exists()


@needs_mergiraf
def test_mergiraf_solves_what_it_can_and_leaves_the_file_for_the_user_to_stage(repo, jazygit):
    python_conflicts(repo)
    ui = jazygit()
    ui.branches.merge("feature")
    ui.files.select("imports.py")

    ui.app.press("enter")
    ui.app.wait_for("m  Solve it with mergiraf")
    ui.app.press("m")
    ui.app.wait_for("imports.py has no conflict markers left: space stages it")

    assert repo.read("imports.py") == "import a\nimport c\nimport b\n\n\ndef f():\n    return 1\n"
    assert repo.conflicts() == ["imports.py", "value.py"]
    assert not (repo.path / "imports.py.orig").exists()


@needs_mergiraf
def test_what_mergiraf_cannot_solve_is_left_in_conflict(repo, jazygit):
    python_conflicts(repo)
    ui = jazygit()
    ui.branches.merge("feature")
    ui.files.select("value.py")

    ui.app.press("enter")
    ui.app.wait_for("m  Solve it with mergiraf")
    ui.app.press("m")

    ui.app.wait_for("value.py still has 1 conflict, the first at line 2")
    assert repo.conflicts() == ["imports.py", "value.py"]


def test_a_file_in_conflict_opens_in_the_editor_set_at_its_first_conflict(repo, env, jazygit):
    from test_editors import file_editors, opened
    repo.commit("Add three", **{"three.txt": "a\nb\nc\n"})
    repo("switch", "--create", "feature")
    repo.commit("Change c on feature", **{"three.txt": "a\nb\nfeature\n"})
    repo("switch", "main")
    repo.commit("Change c on main", **{"three.txt": "a\nb\nmain\n"})
    repo("switch", "feature")
    file_editors(env, ("Marker", "opened", None))
    ui = jazygit()
    ui.branches.rebase_onto("main")

    ui.files.edit("three.txt")

    assert opened(ui, repo.path / "opened") == f"{repo.path / 'three.txt'}:3"


@needs_mergiraf
def test_with_mergiraf_as_gits_merge_driver_u_brings_back_what_it_made_of_the_file(repo, jazygit):
    repo("config", "merge.mergiraf.name", "mergiraf")
    repo("config", "merge.mergiraf.driver", "mergiraf merge --git %O %A %B -s %S -x %X -y %Y -p %P -l %L")
    repo.commit("Merge Python with mergiraf", **{".gitattributes": "*.py merge=mergiraf\n"})
    python_conflicts(repo)
    ui = jazygit()
    ui.branches.merge("feature")
    # The driver solved imports.py; only value.py is left in conflict.
    assert repo.conflicts() == ["value.py"]
    ui.files.resolve("value.py", "o")
    assert repo.conflicts() == []

    ui.files.restore("value.py")

    # git checkout -m merges the file again, through the driver: what comes back is the conflict as the merge stopped
    # with it, with mergiraf's own labels, not git's.
    assert repo.conflicts() == ["value.py"]
    assert "<<<<<<< ours" in repo.read("value.py")
