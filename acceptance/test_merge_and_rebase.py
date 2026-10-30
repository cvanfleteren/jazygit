"""Merging a branch into the checked out one, and rebasing the checked out one onto a branch, when they go through.
The ones that stop on conflicts are in test_conflicts.py."""


def ahead(repo):
    """Main, checked out, and a branch feature with one commit more: a merge of it can fast-forward."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("switch", "main")


def diverged(repo):
    """Main, checked out, and a branch feature that each add a file of their own since they parted."""
    ahead(repo)
    repo.commit("Add three", **{"three.txt": "three\n"})


def files(repo, rev="HEAD") -> list:
    return repo("ls-tree", "-r", "--name-only", rev).splitlines()


def test_a_merge_fast_forwards_when_it_can(repo, jazygit):
    ahead(repo)
    ui = jazygit()

    ui.branches.merge("feature")

    assert repo("rev-parse", "main") == repo("rev-parse", "feature")
    assert repo.subjects() == ["Add two", "Add one"]
    assert repo.parents() == 1
    assert repo.status() == {}


def test_a_merge_that_cannot_fast_forward_makes_a_merge_commit(repo, jazygit):
    diverged(repo)
    main = repo("rev-parse", "main")
    ui = jazygit()

    ui.branches.merge("feature")

    assert repo.in_progress() is None
    assert repo.subjects()[0] == "Merge branch 'feature'"
    assert repo("rev-parse", "HEAD^1") == main
    assert repo("rev-parse", "HEAD^2") == repo("rev-parse", "feature")
    assert files(repo) == ["one.txt", "three.txt", "two.txt"]
    assert repo.status() == {}


def test_a_merge_with_a_merge_commit_makes_one_even_when_it_could_fast_forward(repo, jazygit):
    ahead(repo)
    main = repo("rev-parse", "main")
    ui = jazygit()

    ui.branches.merge("feature", mode="merge commit")

    assert repo.subjects()[0] == "Merge branch 'feature'"
    assert repo("rev-parse", "HEAD^1") == main
    assert repo("rev-parse", "HEAD^2") == repo("rev-parse", "feature")
    assert files(repo) == ["one.txt", "two.txt"]
    assert repo.status() == {}


def test_a_squash_merge_left_uncommitted_stages_the_changes_of_the_branch(repo, jazygit):
    diverged(repo)
    main = repo("rev-parse", "main")
    ui = jazygit()

    ui.branches.merge("feature", mode="squash")

    assert repo("rev-parse", "main") == main
    assert repo.in_progress() is None
    assert repo.status() == {"two.txt": "A "}
    assert repo.read("two.txt") == "two\n"


def test_a_squash_merge_committed_makes_one_commit_of_the_whole_branch(repo, jazygit):
    diverged(repo)
    repo("switch", "feature")
    repo.commit("Change two", **{"two.txt": "two, changed\n"})
    repo("switch", "main")
    main = repo("rev-parse", "main")
    ui = jazygit()

    ui.branches.merge("feature", mode="squash and commit")

    assert repo.subjects() == ["Squashed commit of the following:", "Add three", "Add one"]
    assert repo("rev-parse", "HEAD^") == main
    assert repo.parents() == 1
    # The message lists the squashed commits, the last one first, as git writes it.
    message = repo.message()
    assert message.index("    Change two") < message.index("    Add two")
    assert repo("show", "HEAD:two.txt") == "two, changed"
    assert files(repo) == ["one.txt", "three.txt", "two.txt"]
    assert repo.status() == {}


def test_a_squash_merge_with_nothing_to_squash_commits_nothing(repo, jazygit):
    ahead(repo)
    # All of main is on feature already.
    repo("switch", "feature")
    feature = repo("rev-parse", "feature")
    ui = jazygit()

    ui.branches.try_to_merge("main", mode="squash and commit")

    # It tells what it ran once it is done: the merge, and no commit after it.
    ui.app.wait_for("git merge --squash refs/heads/main")
    ui.caught_up()
    assert "git commit" not in ui.app.text()
    assert repo("rev-parse", "feature") == feature
    assert repo.status() == {}


def rebase_ready(repo):
    """Feature, checked out, with two commits of its own since it left main, which got one since; none touch the
    same file."""
    diverged(repo)
    repo("switch", "feature")
    repo.commit("Add four", **{"four.txt": "four\n"})


def test_rebase_onto_a_branch_puts_the_commits_of_the_checked_out_one_on_top_of_it(repo, jazygit):
    rebase_ready(repo)
    feature = repo("rev-parse", "feature")
    main = repo("rev-parse", "main")
    ui = jazygit()

    ui.branches.rebase_onto("main")

    assert repo.in_progress() is None
    assert repo.current_branch() == "feature"
    assert repo.subjects() == ["Add four", "Add two", "Add three", "Add one"]
    assert repo("merge-base", "feature", "main") == main
    assert repo("rev-list", "--merges", "feature") == ""
    assert repo("rev-parse", "ORIG_HEAD") == feature
    assert repo("rev-parse", "main") == main
    assert files(repo) == ["four.txt", "one.txt", "three.txt", "two.txt"]
    assert repo.status() == {}


def test_rebase_brings_the_uncommitted_changes_back_afterwards(repo, jazygit):
    rebase_ready(repo)
    main = repo("rev-parse", "main")
    repo.write("two.txt", "two, being worked on\n")
    repo.write("four.txt", "four, being worked on\n")
    repo("add", "four.txt")
    repo.write("notes.txt", "untracked\n")
    ui = jazygit()

    ui.branches.rebase_onto("main")

    assert repo.in_progress() is None
    assert repo.subjects() == ["Add four", "Add two", "Add three", "Add one"]
    assert repo("merge-base", "feature", "main") == main
    assert repo("show", "HEAD:two.txt") == "two"
    assert repo.read("two.txt") == "two, being worked on\n"
    assert repo.read("four.txt") == "four, being worked on\n"
    assert repo.read("notes.txt") == "untracked\n"
    # Git brings the stash back without --index: what was staged comes back unstaged.
    assert repo.status() == {"two.txt": " M", "four.txt": " M", "notes.txt": "??"}
    assert repo.stashes() == []
