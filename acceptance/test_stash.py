"""Stashing the changes, and looking at, popping and dropping a stash."""


def test_selecting_a_stash_shows_what_it_stashed(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("new.txt", "brand new\n")
    repo("stash", "push", "--include-untracked", "--message", "older")
    repo.write("one.txt", "one\nnewer\n")
    repo("stash", "push", "--message", "newer")
    # The pane is as tall as its stashes, and with two rows the cursor's is not the only background.
    repo.write("one.txt", "one\nnewest\n")
    repo("stash", "push", "--message", "newest")
    ui = jazygit()

    ui.stash.select("older")

    screen = ui.app.text()
    assert "Branch: main" in screen
    assert "+changed" in screen
    # Its untracked file is part of it.
    assert "+brand new" in screen
    assert "+newer" not in screen


def test_stash_staged_unstaged_and_untracked_changes_under_a_name(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nstaged\n")
    repo("add", "one.txt")
    repo.write("two.txt", "two\nunstaged\n")
    repo.write("new.txt", "new\n")
    ui = jazygit()

    ui.files.stash("parser")

    assert repo.status() == {}
    assert repo.stashes() == ["parser"]
    assert repo("stash", "show", "--include-untracked", "--name-only").splitlines() == [
        "new.txt", "one.txt", "two.txt"]


def test_stash_without_a_name_is_named_by_git(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    ui = jazygit()

    ui.files.stash()

    assert repo.status() == {}
    assert repo.stashes() == ["WIP on main: " + repo.short("HEAD") + " Add one"]


def test_pop_a_stash_brings_its_changes_back_as_they_were_staged_and_removes_it(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "two.txt": "two\n", "three.txt": "three\n"})
    repo.write("one.txt", "one\nolder\n")
    repo("stash", "push", "--message", "older")
    repo.write("two.txt", "two\nstaged\n")
    repo("add", "two.txt")
    repo.write("two.txt", "two\nstaged\nunstaged\n")
    repo.write("three.txt", "three\nunstaged\n")
    repo.write("new.txt", "new\n")
    repo("stash", "push", "--include-untracked", "--message", "newer")
    ui = jazygit()

    ui.stash.pop("newer")

    assert repo.stashes() == ["older"]
    assert repo.status() == {"two.txt": "MM", "three.txt": " M", "new.txt": "??"}
    assert repo("show", ":two.txt") == "two\nstaged"
    assert repo.read("two.txt") == "two\nstaged\nunstaged\n"


def test_pop_brings_staged_changes_back_unstaged_when_they_no_longer_apply_staged(repo, jazygit):
    repo.commit("Add eight lines", **{"one.txt": "1\n2\n3\n4\n5\n6\n7\n8\n"})
    repo.write("one.txt", "1\n2\n3\n4\nstaged\n6\n7\n8\n")
    repo("add", "one.txt")
    repo("stash", "push", "--message", "stashed")
    # A change two lines away: the staged change no longer applies to what is staged, but merges cleanly.
    repo.commit("Change line three", **{"one.txt": "1\n2\nlater\n4\n5\n6\n7\n8\n"})
    ui = jazygit()

    ui.stash.pop("stashed")

    assert repo.stashes() == []
    assert repo.status() == {"one.txt": " M"}
    assert repo.read("one.txt") == "1\n2\nlater\n4\nstaged\n6\n7\n8\n"


def test_pop_with_something_staged_brings_the_stash_back_unstaged_and_keeps_what_is_staged(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nstashed\n")
    repo("add", "one.txt")
    repo("stash", "push", "--message", "stashed")
    repo.write("two.txt", "two\nstaged\n")
    repo("add", "two.txt")
    ui = jazygit()

    ui.stash.pop("stashed")

    assert repo.stashes() == []
    assert repo.status() == {"one.txt": " M", "two.txt": "M "}
    assert repo.read("one.txt") == "one\nstashed\n"


def test_pop_an_older_stash_leaves_the_newer_one(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nolder\n")
    repo("stash", "push", "--message", "older")
    repo.write("two.txt", "two\nnewer\n")
    repo("stash", "push", "--message", "newer")
    ui = jazygit()

    ui.stash.pop("older")

    assert repo.stashes() == ["newer"]
    assert repo.status() == {"one.txt": " M"}
    assert repo.read("one.txt") == "one\nolder\n"


def test_pop_is_refused_when_it_would_overwrite_changes(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nstashed\n")
    repo("stash", "push", "--message", "stashed")
    repo.write("one.txt", "one\nlocal\n")
    # Two stashes, so that the cursor's row is the only one on its background.
    repo.write("two.txt", "two\n")
    repo("stash", "push", "--include-untracked", "--message", "other")
    repo.write("one.txt", "one\nlocal\n")
    ui = jazygit()

    ui.stash.try_to_pop("stashed")

    ui.app.wait_for("Unstash failed")
    assert repo.stashes() == ["other", "stashed"]
    assert repo.read("one.txt") == "one\nlocal\n"


def test_drop_a_stash_leaves_the_others_and_the_working_tree(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nolder\n")
    repo("stash", "push", "--message", "older")
    repo.write("one.txt", "one\nnewer\n")
    repo("stash", "push", "--message", "newer")
    repo.write("one.txt", "one\nuncommitted\n")
    ui = jazygit()

    ui.stash.drop("older")

    assert repo.stashes() == ["newer"]
    assert repo.read("one.txt") == "one\nuncommitted\n"


def test_checkout_brings_the_changes_across_through_a_stash(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "1\n2\n3\n4\n5\n", "two.txt": "two\n"})
    repo("branch", "feature")
    repo.commit("Change one on main", **{"one.txt": "main\n2\n3\n4\n5\n"})
    # Staged and unstaged; the staged change to a file the checkout would overwrite, far enough from what main
    # changed in it to apply on feature.
    repo.write("one.txt", "main\n2\n3\n4\nstaged\n")
    repo("add", "one.txt")
    repo.write("two.txt", "two\nunstaged\n")
    ui = jazygit()

    ui.branches.checkout_bringing_changes("feature")

    assert repo.current_branch() == "feature"
    assert repo.read("one.txt") == "1\n2\n3\n4\nstaged\n"
    assert repo.status() == {"one.txt": "M ", "two.txt": " M"}
    assert repo.stashes() == []
