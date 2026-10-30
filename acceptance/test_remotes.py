"""Pushing and pulling branches, and working with the remote branches: checking them out and deleting them."""


def published(repo, origin):
    """Main with one commit, and a branch feature with one more, both pushed to origin, which they track."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    repo("switch", "--create", "feature")
    repo.commit("Add feature", **{"feature.txt": "feature\n"})
    repo("push", "--set-upstream", "origin", "feature")
    repo("switch", "main")


def test_push_a_branch_without_upstream_to_origin(repo, origin, jazygit):
    published(repo, origin)
    repo("switch", "--create", "new")
    repo.commit("Add new", **{"new.txt": "new\n"})
    ui = jazygit()

    ui.branches.push("new")

    assert origin("rev-parse", "new") == repo("rev-parse", "new")
    assert repo.upstream("new") == "origin/new"
    assert repo.sync("new") == ""


def test_push_a_branch_under_another_name(repo, origin, jazygit):
    published(repo, origin)
    repo("switch", "--create", "local-name")
    repo.commit("Add new", **{"new.txt": "new\n"})
    ui = jazygit()

    ui.branches.push("local-name", upstream="origin shared-name")

    assert origin("rev-parse", "shared-name") == repo("rev-parse", "local-name")
    assert origin.branches() == ["feature", "main", "shared-name"]
    assert repo.upstream("local-name") == "origin/shared-name"


def test_push_commits_to_the_upstream(repo, origin, jazygit):
    published(repo, origin)
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    ui = jazygit()

    ui.branches.push("main")

    assert origin.subjects("main") == ["Add three", "Add two", "Add one"]
    assert repo.sync("main") == ""


def test_push_a_branch_that_is_not_checked_out(repo, origin, jazygit):
    published(repo, origin)
    repo("switch", "feature")
    repo.commit("Add more", **{"more.txt": "more\n"})
    repo("switch", "main")
    ui = jazygit()

    ui.branches.push("feature")

    assert origin.subjects("feature") == ["Add more", "Add feature", "Add one"]
    assert repo.current_branch() == "main"


def test_force_push_a_rewritten_branch(repo, origin, jazygit):
    published(repo, origin)
    repo("switch", "feature")
    repo("commit", "--amend", "--message", "Add the feature")
    repo("switch", "main")
    ui = jazygit()

    ui.branches.force_push("feature")

    assert origin.subjects("feature") == ["Add the feature", "Add one"]
    assert repo.sync("feature") == ""


def test_pull_what_was_fetched(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("fetch")
    ui = jazygit()

    ui.branches.pull("main")

    assert repo.subjects() == ["Add theirs", "Add one"]
    assert (repo.path / "theirs.txt").read_text() == "theirs\n"
    assert repo.sync("main") == ""


def test_pull_fetches_what_was_not_fetched_yet(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    ui = jazygit()

    ui.branches.pull("main")

    assert repo.subjects() == ["Add theirs", "Add one"]


def test_pull_rebases_local_commits_onto_the_upstream(repo, origin, clone, jazygit):
    published(repo, origin)
    repo("config", "pull.rebase", "true")
    teammate = clone()
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo.commit("Add mine", **{"mine.txt": "mine\n"})
    repo("fetch")
    ui = jazygit()

    ui.branches.pull("main")

    assert repo.subjects() == ["Add mine", "Add theirs", "Add one"]
    assert repo.sync("main") == "ahead 1"


def test_pull_a_branch_that_is_not_checked_out_fast_forwards_it(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate("switch", "feature")
    teammate.commit("Add more", **{"more.txt": "more\n"})
    teammate("push")
    repo("fetch")
    ui = jazygit()

    ui.branches.pull("feature")

    assert repo.subjects("feature") == ["Add more", "Add feature", "Add one"]
    assert repo.current_branch() == "main"
    assert not (repo.path / "more.txt").exists()


def test_pull_refuses_to_fast_forward_a_branch_with_unpushed_commits(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate("switch", "feature")
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("switch", "feature")
    repo.commit("Add mine", **{"mine.txt": "mine\n"})
    repo("switch", "main")
    repo("fetch")
    ui = jazygit()

    ui.branches.try_to_pull("feature")

    ui.app.wait_for("Cannot fast-forward branch feature: it has commits that are not pushed")
    assert repo.subjects("feature") == ["Add mine", "Add feature", "Add one"]


def test_pull_a_branch_without_upstream(repo, origin, jazygit):
    published(repo, origin)
    repo("branch", "local-only")
    ui = jazygit()

    ui.branches.try_to_pull("local-only")

    ui.app.wait_for("Branch local-only has no upstream")


def test_a_branch_whose_upstream_was_deleted_is_gone(repo, origin, clone, jazygit):
    published(repo, origin)
    clone()("push", "origin", "--delete", "feature")
    repo("fetch", "--prune")
    ui = jazygit()

    ui.branches.try_to_pull("feature")

    assert ui.branches.shows_gone("feature")
    ui.app.wait_for("Branch feature has no upstream anymore: origin/feature was deleted on the remote")
    assert repo.sync("feature") == "gone"


def test_checkout_a_remote_branch_creates_a_local_branch_that_follows_it(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate("switch", "--create", "theirs")
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push", "--set-upstream", "origin", "theirs")
    repo("fetch")
    ui = jazygit()

    ui.branches.checkout_remote("origin/theirs")

    assert repo.current_branch() == "theirs"
    assert repo.upstream("theirs") == "origin/theirs"
    assert repo.subjects() == ["Add theirs", "Add one"]


def test_checkout_a_remote_branch_that_a_local_branch_follows(repo, origin, jazygit):
    published(repo, origin)
    ui = jazygit()

    ui.branches.checkout_remote("origin/feature")

    assert repo.current_branch() == "feature"
    assert repo.branches() == ["feature", "main"]


def test_checkout_a_remote_branch_whose_name_is_taken(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate("switch", "--create", "topic")
    teammate.commit("Add their topic", **{"topic.txt": "theirs\n"})
    teammate("push", "--set-upstream", "origin", "topic")
    repo("fetch")
    repo("branch", "topic")
    ui = jazygit()

    ui.branches.checkout_remote("origin/topic", name="their-topic")

    assert repo.current_branch() == "their-topic"
    assert repo.upstream("their-topic") == "origin/topic"
    assert repo.subjects() == ["Add their topic", "Add one"]
    assert repo.upstream("topic") == ""


def test_delete_a_remote_branch(repo, origin, jazygit):
    published(repo, origin)
    ui = jazygit()

    ui.branches.delete_remote("origin/feature")

    assert origin.branches() == ["main"]
    assert repo.remote_branches() == ["origin/main"]
    # The local branch stays, and shows its upstream is gone.
    assert repo.branches() == ["feature", "main"]
    ui.branches.select("feature")
    assert ui.branches.shows_gone("feature")


def test_the_default_branch_is_not_deleted_on_the_remote(repo, origin, jazygit):
    published(repo, origin)
    ui = jazygit()

    ui.branches.try_to_delete_remote("origin/main")

    ui.app.wait_for("main is the default branch and cannot be deleted")
    assert origin.branches() == ["feature", "main"]


def test_delete_a_branch_on_the_remote_only(repo, origin, jazygit):
    published(repo, origin)
    ui = jazygit()

    ui.branches.delete("feature", where="remote")

    assert origin.branches() == ["main"]
    assert repo.branches() == ["feature", "main"]


def test_delete_a_branch_locally_and_on_the_remote(repo, origin, jazygit):
    published(repo, origin)
    ui = jazygit()

    ui.branches.delete("feature", where="both")

    assert origin.branches() == ["main"]
    assert repo.branches() == ["main"]
    assert repo.remote_branches() == ["origin/main"]


def test_reset_the_checked_out_branch_onto_its_upstream(repo, origin, jazygit):
    published(repo, origin)
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    ui = jazygit()

    ui.branches.reset_onto_upstream("hard")

    assert repo.subjects() == ["Add one"]
    assert repo("status", "--porcelain") == ""


def test_soft_reset_onto_the_upstream_keeps_the_changes_staged(repo, origin, jazygit):
    published(repo, origin)
    repo.commit("Add two", **{"two.txt": "two\n"})
    ui = jazygit()

    ui.branches.reset_onto_upstream("soft")

    assert repo.subjects() == ["Add one"]
    assert repo("diff", "--cached", "--name-only") == "two.txt"


def test_mixed_reset_onto_the_upstream_keeps_the_changes_unstaged(repo, origin, jazygit):
    published(repo, origin)
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Change one", **{"one.txt": "one\nchanged\n"})
    ui = jazygit()

    ui.branches.reset_onto_upstream("mixed")

    assert repo.subjects() == ["Add one"]
    assert repo("rev-parse", "main") == repo("rev-parse", "origin/main")
    assert repo.status() == {"one.txt": " M", "two.txt": "??"}
    assert repo.read("one.txt") == "one\nchanged\n"


def test_hard_reset_onto_the_upstream_discards_the_uncommitted_changes_once_confirmed(repo, origin, jazygit):
    published(repo, origin)
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.write("one.txt", "one\nuncommitted\n")
    ui = jazygit()

    # Asks once more, as it discards them.
    ui.branches.reset_onto_upstream("hard")

    assert repo.subjects() == ["Add one"]
    assert repo.read("one.txt") == "one\n"
    assert repo.status() == {}


def test_rename_a_branch_keeps_its_upstream(repo, origin, jazygit):
    published(repo, origin)
    ui = jazygit()

    ui.branches.rename("feature", "feat")

    assert repo.branches() == ["feat", "main"]
    assert repo.upstream("feat") == "origin/feature"
    assert origin.branches() == ["feature", "main"]


def test_pull_keeps_the_uncommitted_changes(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate.commit("Add two", **{"two.txt": "two\n"})
    teammate("push")
    repo.write("one.txt", "one\nuncommitted\n")
    repo.write("notes.txt", "untracked\n")
    ui = jazygit()

    ui.branches.pull("main")

    assert repo.subjects() == ["Add two", "Add one"]
    assert repo.read("one.txt") == "one\nuncommitted\n"
    assert repo.status() == {"one.txt": " M", "notes.txt": "??"}


def test_pull_of_a_diverged_branch_is_refused_when_only_fast_forwards_are_allowed(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("config", "pull.ff", "only")
    repo.commit("Add mine", **{"mine.txt": "mine\n"})
    main = repo("rev-parse", "main")
    ui = jazygit()

    ui.branches.try_to_pull("main")

    ui.app.wait_for("Pulling branch main failed")
    assert repo("rev-parse", "main") == main
    assert repo.in_progress() is None
    assert repo.status() == {}


def test_pull_a_branch_checked_out_in_another_worktree_is_refused(repo, origin, clone, tmp_path, jazygit):
    published(repo, origin)
    repo("worktree", "add", str(tmp_path / "repo-feature"), "feature")
    teammate = clone()
    teammate("switch", "feature")
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("fetch")
    feature = repo("rev-parse", "feature")
    ui = jazygit()

    ui.branches.try_to_pull("feature")

    # Fast-forwarding it here would leave that worktree with the commits as changes to undo.
    ui.app.wait_for("Fast-forwarding branch feature failed: it is checked out")
    assert repo("rev-parse", "feature") == feature
    assert not (tmp_path / "repo-feature" / "theirs.txt").exists()
