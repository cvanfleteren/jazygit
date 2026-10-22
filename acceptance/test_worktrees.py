"""Adding worktrees, for a branch, a new branch or a commit, and switching to, removing, locking and pruning them."""

import shutil

from harness import Git


def two_commits(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})


def worktree(repo, tmp_path, branch: str) -> Git:
    """Adds a worktree for the existing branch, next to the repository, as repo-<branch>."""
    path = tmp_path / f"repo-{branch}"
    repo("worktree", "add", str(path), branch)
    return Git(path, repo.env)


def test_new_worktree_for_a_branch_goes_next_to_the_repository(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature", "HEAD~1")
    ui = jazygit()

    ui.branches.new_worktree("feature")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-feature": "feature"}
    assert Git(tmp_path / "repo-feature", repo.env).subjects() == ["Add one"]
    assert repo.current_branch() == "main"
    assert repo.status() == {}


def test_new_worktree_where_typed(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    ui = jazygit()

    ui.branches.new_worktree("feature", location=tmp_path / "elsewhere")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "elsewhere": "feature"}
    assert (tmp_path / "elsewhere" / "two.txt").read_text() == "two\n"


def test_new_branch_in_a_new_worktree(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "base", "HEAD~1")
    ui = jazygit()

    ui.branches.new_branch_worktree("base", "feature/login")

    # Named after the branch, with its slash made a dash.
    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-feature-login": "feature/login"}
    assert repo("rev-parse", "feature/login") == repo("rev-parse", "base")
    assert repo.branches() == ["base", "feature/login", "main"]
    assert repo.current_branch() == "main"


def test_new_worktree_for_the_checked_out_branch_makes_a_new_branch(repo, tmp_path, jazygit):
    two_commits(repo)
    ui = jazygit()

    # git checks a branch out in one worktree only: the checked out one gets a new branch.
    ui.branches.new_branch_worktree("main", "topic")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-topic": "topic"}
    assert repo("rev-parse", "topic") == repo("rev-parse", "main")


def test_new_branch_in_a_new_worktree_following_a_remote_branch(repo, origin, clone, tmp_path, jazygit):
    two_commits(repo)
    repo("push", "--set-upstream", "origin", "main")
    teammate = clone()
    teammate("switch", "--create", "theirs")
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push", "--set-upstream", "origin", "theirs")
    repo("fetch")
    ui = jazygit()

    ui.branches.new_branch_worktree_from_remote("origin/theirs")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-theirs": "theirs"}
    assert repo.upstream("theirs") == "origin/theirs"
    assert repo.subjects("theirs") == ["Add theirs", "Add two", "Add one"]
    assert (tmp_path / "repo-theirs" / "theirs.txt").read_text() == "theirs\n"


def test_new_worktree_from_the_worktrees_on_a_new_branch_at_a_commit(repo, tmp_path, jazygit):
    two_commits(repo)
    ui = jazygit()

    ui.worktrees.new(repo.short(repo.find("Add one")), "fix")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-fix": "fix"}
    assert repo.subjects("fix") == ["Add one"]


def test_new_worktree_from_the_worktrees_for_an_existing_branch(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature", "HEAD~1")
    ui = jazygit()

    # No name: the branch typed itself, which nothing has checked out.
    ui.worktrees.new("feature", location=tmp_path / "feature-tree")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "feature-tree": "feature"}
    assert repo.branches() == ["feature", "main"]


def test_new_worktree_on_no_branch(repo, tmp_path, jazygit):
    two_commits(repo)
    one = repo.find("Add one")
    ui = jazygit()

    ui.worktrees.new(repo.short(one), location=tmp_path / "detached")

    assert repo.worktrees() == {repo.path: "main", tmp_path / "detached": None}
    assert Git(tmp_path / "detached", repo.env)("rev-parse", "HEAD") == one
    assert repo.branches() == ["main"]


def test_add_a_worktree_switch_to_it_and_commit_there(repo, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    ui = jazygit()

    there = ui.branches.new_worktree("feature", switch=True)
    there.repo.write("three.txt", "three\n")
    there.files.commit("Add three")

    assert repo.subjects("feature") == ["Add three", "Add two", "Add one"]
    assert repo.subjects("main") == ["Add two", "Add one"]
    assert not (repo.path / "three.txt").exists()
    assert repo.status() == {}
    assert there.repo.status() == {}


def test_switch_to_a_worktree_and_commit_there(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    there = ui.worktrees.switch_to("repo-feature")
    there.repo.write("one.txt", "one\nchanged\n")
    there.files.commit("Change one")

    assert repo.subjects("feature") == ["Change one", "Add two", "Add one"]
    assert repo.subjects("main") == ["Add two", "Add one"]
    assert repo.read("one.txt") == "one\n"


def test_switch_back_to_the_main_worktree(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    back = ui.worktrees.switch_to("repo-feature").worktrees.switch_to("repo")
    back.repo.write("three.txt", "three\n")
    back.files.commit("Add three")

    assert repo.subjects("main") == ["Add three", "Add two", "Add one"]
    assert repo.subjects("feature") == ["Add two", "Add one"]


def test_checkout_a_branch_checked_out_in_another_worktree_switches_to_that_worktree(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    there = ui.branches.checkout_in_its_worktree("feature")
    there.repo.write("three.txt", "three\n")
    there.files.commit("Add three")

    # Nothing was checked out: the branch was committed to in its own worktree.
    assert repo.current_branch() == "main"
    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-feature": "feature"}
    assert repo.subjects("feature") == ["Add three", "Add two", "Add one"]


def test_remove_a_worktree_keeps_its_branch(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    ui.worktrees.remove("repo-feature")

    assert repo.worktrees() == {repo.path: "main"}
    assert not (tmp_path / "repo-feature").exists()
    assert repo.branches() == ["feature", "main"]


def test_remove_a_worktree_and_delete_its_branch(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    there = worktree(repo, tmp_path, "feature")
    # Not merged anywhere: deleted all the same.
    there.commit("Add three", **{"three.txt": "three\n"})
    ui = jazygit()

    ui.worktrees.remove("repo-feature", branch="delete")

    assert repo.worktrees() == {repo.path: "main"}
    assert not (tmp_path / "repo-feature").exists()
    assert repo.branches() == ["main"]
    assert repo.subjects() == ["Add two", "Add one"]


def test_remove_a_worktree_with_changes_anyway(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    there = worktree(repo, tmp_path, "feature")
    there.write("one.txt", "one\nchanged\n")
    there.write("new.txt", "new\n")
    ui = jazygit()

    ui.worktrees.remove("repo-feature", anyway=True)

    assert repo.worktrees() == {repo.path: "main"}
    assert not (tmp_path / "repo-feature").exists()
    assert repo.branches() == ["feature", "main"]


def test_the_main_worktree_is_not_removed(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    ui.worktrees.try_to_remove("repo")

    ui.app.wait_for("is the main one")
    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-feature": "feature"}


def test_lock_a_worktree_and_unlock_it(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    ui.worktrees.lock("repo-feature")

    assert repo.locked_worktrees() == {tmp_path / "repo-feature": ""}

    ui.worktrees.lock("repo-feature")

    assert repo.locked_worktrees() == {}
    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-feature": "feature"}


def test_a_locked_worktree_is_not_removed(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    repo("worktree", "lock", "--reason", "on a stick", str(tmp_path / "repo-feature"))
    ui = jazygit()

    ui.worktrees.try_to_remove("repo-feature")

    ui.app.wait_for("is locked")
    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-feature": "feature"}
    assert (tmp_path / "repo-feature" / "one.txt").exists()


def test_prune_forgets_the_worktrees_whose_directory_is_gone(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "lost")
    repo("branch", "kept")
    worktree(repo, tmp_path, "lost")
    worktree(repo, tmp_path, "kept")
    shutil.rmtree(tmp_path / "repo-lost")
    ui = jazygit()

    ui.worktrees.prune()

    assert repo.worktrees() == {repo.path: "main", tmp_path / "repo-kept": "kept"}
    # Its branch stays, and can be checked out again.
    assert repo.branches() == ["kept", "lost", "main"]


def test_delete_a_branch_checked_out_in_another_worktree_with_that_worktree(repo, tmp_path, jazygit):
    two_commits(repo)
    repo("branch", "feature")
    worktree(repo, tmp_path, "feature")
    ui = jazygit()

    ui.branches.delete("feature", where="worktree")

    assert repo.worktrees() == {repo.path: "main"}
    assert not (tmp_path / "repo-feature").exists()
    assert repo.branches() == ["main"]
