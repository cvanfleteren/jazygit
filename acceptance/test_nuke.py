"""Nuking a branch: deleting it, its worktree and its branch on origin after one confirmation (ADR 43)."""

import shutil

from harness import Git


def published(repo, origin):
    """Main with one commit, and a branch feature with one more, both pushed to origin, which they track."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    repo("switch", "--create", "feature")
    repo.commit("Add feature", **{"feature.txt": "feature\n"})
    repo("push", "--set-upstream", "origin", "feature")
    repo("switch", "main")


def worktree(repo, tmp_path, branch: str) -> Git:
    """Adds a worktree for the existing branch, next to the repository, as repo-<branch>."""
    path = tmp_path / f"repo-{branch}"
    repo("worktree", "add", str(path), branch)
    return Git(path, repo.env)


def test_nuke_a_branch_with_its_locked_worktree_with_changes_and_its_remote_branch(repo, origin, tmp_path, jazygit):
    published(repo, origin)
    there = worktree(repo, tmp_path, "feature")
    there.commit("Not pushed", **{"local.txt": "local\n"})
    there.write("feature.txt", "changed\n")
    there.write("new.txt", "new\n")
    repo("worktree", "lock", "--reason", "on a stick", str(there.path))
    ui = jazygit()

    ui.branches.nuke("feature")

    assert repo.branches() == ["main"]
    assert repo.worktrees() == {repo.path: "main"}
    assert not there.path.exists()
    assert origin.branches() == ["main"]
    assert repo.remote_branches() == ["origin/main"]


def test_nuke_a_branch_whose_worktree_is_gone_and_locked(repo, tmp_path, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("branch", "feature")
    there = worktree(repo, tmp_path, "feature")
    repo("worktree", "lock", str(there.path))
    shutil.rmtree(there.path)
    ui = jazygit()

    ui.branches.nuke("feature")

    assert repo.branches() == ["main"]
    assert repo.worktrees() == {repo.path: "main"}


def test_nuke_a_local_branch_with_commits_of_its_own(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "abandoned")
    repo.commit("Try something", **{"try.txt": "try\n"})
    repo("switch", "main")
    ui = jazygit()

    ui.branches.nuke("abandoned")

    assert repo.branches() == ["main"]
    assert repo.current_branch() == "main"


def test_nuke_a_branch_made_from_origin_main_keeps_main_on_origin(repo, origin, jazygit):
    published(repo, origin)
    repo("branch", "--track", "fix", "origin/main")
    ui = jazygit()

    ui.branches.nuke("fix")

    assert repo.branches() == ["feature", "main"]
    assert origin.branches() == ["feature", "main"]


def test_nuke_a_branch_following_another_remote_keeps_it_there(repo, origin, tmp_path, jazygit):
    published(repo, origin)
    upstream = Git(tmp_path / "upstream.git", repo.env)
    upstream.path.mkdir()
    upstream("init", "--bare", "--initial-branch=main")
    repo("remote", "add", "upstream", str(upstream.path))
    repo("push", "upstream", "main:develop")
    repo("branch", "--track", "develop", "upstream/develop")
    ui = jazygit()

    ui.branches.nuke("develop")

    assert repo.branches() == ["feature", "main"]
    assert upstream.branches() == ["develop"]


def test_the_default_branch_and_the_branch_checked_out_here_are_not_nuked(repo, origin, jazygit):
    published(repo, origin)
    repo("switch", "feature")
    ui = jazygit()

    ui.branches.try_to_nuke("main")
    ui.app.wait_for("main is the default branch and cannot be deleted")
    ui.app.press("escape")
    ui.branches.try_to_nuke("feature")
    ui.app.wait_for("is the one shown")

    assert repo.branches() == ["feature", "main"]
    assert origin.branches() == ["feature", "main"]


def test_a_branch_checked_out_in_the_main_worktree_is_not_nuked(repo, tmp_path, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "trunk")
    worktree(repo, tmp_path, "main")
    ui = jazygit()
    linked = ui.worktrees.switch_to("repo-main")

    linked.branches.try_to_nuke("trunk")

    linked.app.wait_for("is the main one")
    assert repo.branches() == ["main", "trunk"]


def test_when_the_remote_refuses_the_rest_is_nuked_and_what_is_left_is_said(repo, origin, jazygit):
    published(repo, origin)
    hook = origin.path / "hooks" / "pre-receive"
    hook.write_text("#!/bin/sh\necho 'no deleting here' >&2\nexit 1\n")
    hook.chmod(0o755)
    ui = jazygit()

    ui.branches.try_to_nuke("feature")
    ui.confirm("Nuke branch feature")

    # The error shows once all three steps ran.
    ui.app.wait_for("Nuking branch feature left origin/feature")
    assert repo.branches() == ["main"]
    assert origin.branches() == ["feature", "main"]
