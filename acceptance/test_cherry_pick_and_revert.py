"""Copying a commit and cherry-picking it onto another branch, and reverting a commit, when they go through or fail
other than on conflicts. The ones that stop on conflicts are in test_conflicts.py."""


def diverged(repo):
    """Main and a branch feature that each add a file of their own since they parted, feature checked out."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("switch", "main")
    repo.commit("Add three", **{"three.txt": "three\n"})
    repo("switch", "feature")


def files(repo, rev="HEAD") -> list:
    return repo("ls-tree", "-r", "--name-only", rev).splitlines()


def test_cherry_pick_a_copied_commit_onto_another_branch(repo, jazygit):
    diverged(repo)
    feature = repo("rev-parse", "feature")
    main = repo("rev-parse", "main")
    ui = jazygit()
    ui.commits.copy("Add two")
    ui.branches.checkout("main")

    ui.commits.cherry_pick()

    assert repo.in_progress() is None
    assert repo.subjects() == ["Add two", "Add three", "Add one"]
    assert repo("rev-parse", "HEAD^") == main
    assert repo("show", "--format=", "HEAD") == repo("show", "--format=", feature)
    assert files(repo) == ["one.txt", "three.txt", "two.txt"]
    assert repo("rev-parse", "feature") == feature
    assert repo.status() == {}
    # The copy is done with once it is picked.
    ui.app.wait_gone("to cherry-pick")


def test_a_cherry_pick_that_fails_is_undone_and_the_commit_stays_copied(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    # Main makes the change of Add two in a commit of its own, so picking it changes nothing.
    repo.commit("Add two on main", **{"two.txt": "two\n"})
    repo("switch", "feature")
    main = repo("rev-parse", "main")
    ui = jazygit()
    copied = repo.short(repo.find("Add two"))
    ui.commits.copy("Add two")
    ui.branches.checkout("main")

    ui.commits.try_to_cherry_pick()

    ui.app.wait_for(f"Cherry-pick of {copied} failed")
    # Enter closes the error; Esc, once it is closed, would let go of the copy.
    ui.app.press("enter")
    ui.app.wait_gone("╭Error")
    ui.caught_up()
    assert repo.in_progress() is None
    assert repo("rev-parse", "main") == main
    assert repo.status() == {}
    assert f"Copied {copied} to cherry-pick" in ui.app.text()

    # Picked again once the change is no longer on main.
    repo("reset", "--hard", "HEAD~")
    ui.commits.cherry_pick()

    assert repo.subjects() == ["Add two", "Add three", "Add one"]
    assert repo.status() == {}


def test_a_merge_commit_cannot_be_copied(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    repo("merge", "--no-ff", "--no-edit", "feature")
    ui = jazygit()

    ui.commits.try_to_copy("Merge branch 'feature'")
    # Keys are handled in turn: once the focus moved on, the copy was refused, or it would show.
    ui.app.focus("Branches")

    assert "to cherry-pick" not in ui.app.text()


def test_escape_lets_go_of_the_copied_commit(repo, jazygit):
    diverged(repo)
    main = repo("rev-parse", "main")
    ui = jazygit()
    ui.commits.copy("Add two")
    ui.branches.checkout("main")

    ui.commits.let_go_of_copy()
    ui.app.focus("Commits")
    ui.app.press("V")
    # With nothing copied, V asks nothing: the focus moves on, which a dialog would keep.
    ui.app.focus("Branches")

    assert "Cherry-pick commit" not in ui.app.text()
    ui.caught_up()
    assert repo("rev-parse", "main") == main


def test_revert_a_commit_adds_one_that_undoes_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    reverted = repo.find("Add two")
    ui = jazygit()

    ui.commits.revert("Add two")

    assert repo.in_progress() is None
    assert repo.subjects() == ['Revert "Add two"', "Add three", "Add two", "Add one"]
    assert repo.message() == f'Revert "Add two"\n\nThis reverts commit {reverted}.'
    assert files(repo) == ["one.txt", "three.txt"]
    assert repo.status() == {}


def test_revert_a_merge_commit_undoes_it_against_its_first_parent(repo, jazygit):
    diverged(repo)
    repo("switch", "main")
    repo("merge", "--no-ff", "--no-edit", "feature")
    merge = repo("rev-parse", "HEAD")
    main = repo("rev-parse", "HEAD^1")
    ui = jazygit()

    ui.commits.revert("Merge branch 'feature'")

    assert repo.in_progress() is None
    assert repo.subjects()[:2] == ["Revert \"Merge branch 'feature'\"", "Merge branch 'feature'"]
    assert repo.parents() == 1
    assert repo.message() == (f"Revert \"Merge branch 'feature'\"\n\nThis reverts commit {merge}, reversing\n"
                              f"changes made to {main}.")
    # What feature brought in is gone; what main had stays.
    assert files(repo) == ["one.txt", "three.txt"]
    assert repo.status() == {}
