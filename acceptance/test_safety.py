"""What the app promises not to lose or overwrite: other people's commits on the remote, branches it is not asked to
touch, commits no other branch has, uncommitted changes, and the repository as it was when an operation of several
git commands fails half-way."""

import stat


def published(repo, origin):
    """Main with one commit, and a branch feature with one more, both pushed to origin, which they track."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    repo("switch", "--create", "feature")
    repo.commit("Add feature", **{"feature.txt": "feature\n"})
    repo("push", "--set-upstream", "origin", "feature")
    repo("switch", "main")


def three_commits(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})


def failing_commits(repo):
    """Makes every commit git makes from here on fail, as a hook can, other than on conflicts: a cherry-pick or a
    rebase then stops half-way, with nothing to resolve."""
    hook = repo.git_dir / "hooks" / "prepare-commit-msg"
    hook.parent.mkdir(exist_ok=True)
    hook.write_text("#!/bin/sh\nexit 1\n")
    hook.chmod(hook.stat().st_mode | stat.S_IEXEC)


def close_error(ui, text: str):
    """Waits for the error that says text, and closes it."""
    ui.app.wait_for(text)
    ui.app.press("enter")
    ui.app.wait_gone("╭Error")


def test_a_force_push_does_not_overwrite_what_others_pushed_since_the_last_fetch(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    ui = jazygit()
    # The app fetches as it starts, and then only once a minute: what is pushed after, it has not fetched.
    ui.app.wait_for("fetched")
    teammate("switch", "feature")
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("switch", "feature")
    repo("commit", "--amend", "--message", "Add the feature")
    repo("switch", "main")

    ui.branches.try_to_force_push("feature")

    close_error(ui, "Pushing branch feature failed")
    ui.caught_up()
    assert origin.subjects("feature") == ["Add theirs", "Add feature", "Add one"]
    assert repo.subjects("feature") == ["Add the feature", "Add one"]


def test_a_branch_that_is_only_behind_its_upstream_is_not_force_pushed(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate("switch", "feature")
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("fetch")
    ui = jazygit()

    ui.branches.try_to_push("feature")

    close_error(ui, "Branch feature is behind its upstream, so there is nothing to push; pull it instead")
    ui.caught_up()
    assert "Force push" not in ui.app.text()
    assert origin.subjects("feature") == ["Add theirs", "Add feature", "Add one"]
    assert repo.subjects("feature") == ["Add feature", "Add one"]


def test_a_branch_is_not_renamed_to_a_name_git_would_read_as_an_option(repo, jazygit):
    three_commits(repo)
    repo("switch", "--create", "feature", "HEAD~1")
    main = repo("rev-parse", "main")
    feature = repo("rev-parse", "feature")
    ui = jazygit()

    # As "git branch -m --force feature", this would overwrite main with feature.
    ui.branches.try_to_rename("feature", "--force")
    # Keys are handled in turn: what is typed after the refused name goes in the dialog, which stays open.
    ui.app.type("x")
    ui.app.wait_for("--forcex")
    ui.app.press("escape")
    ui.app.wait_gone("Rename branch feature")

    ui.caught_up()
    assert repo.branches() == ["feature", "main"]
    assert repo("rev-parse", "main") == main
    assert repo("rev-parse", "feature") == feature
    assert repo.current_branch() == "feature"


def test_a_branch_made_from_a_remote_branch_is_not_pushed_onto_that_branch(repo, origin, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("push", "--set-upstream", "origin", "main")
    repo("switch", "--create", "develop")
    repo.commit("Add develop", **{"develop.txt": "develop\n"})
    repo("push", "origin", "develop")
    repo("switch", "main")
    repo("branch", "--delete", "--force", "develop")
    ui = jazygit()
    ui.branches.new_from_remote("origin/develop", "my-feature")
    # It follows origin/develop, as git's branch.autoSetupMerge has it.
    assert repo.upstream("my-feature") == "origin/develop"
    repo.commit("Add mine", **{"mine.txt": "mine\n"})

    ui.branches.push_refused_by_push_default("my-feature")

    assert origin.subjects("develop") == ["Add develop", "Add one"]
    assert origin("rev-parse", "my-feature") == repo("rev-parse", "my-feature")
    assert repo.upstream("my-feature") == "origin/my-feature"


def test_deleting_a_branch_with_commits_on_no_other_branch_asks_first_and_keeps_it_when_told_no(repo, jazygit):
    three_commits(repo)
    repo("switch", "--create", "abandoned")
    repo.commit("Try something", **{"try.txt": "try\n"})
    repo("switch", "main")
    abandoned = repo("rev-parse", "abandoned")
    ui = jazygit()

    ui.branches.try_to_delete("abandoned")

    ui.app.wait_for("1 commit on abandoned is on no other branch and will be")
    ui.app.wait_for("lost. Delete anyway?")
    ui.app.press("escape")
    ui.app.wait_gone("anyway?")
    ui.caught_up()
    assert repo.branches() == ["abandoned", "main"]
    assert repo("rev-parse", "abandoned") == abandoned


def test_a_merge_merges_the_branch_when_a_tag_has_its_name(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("switch", "main")
    # Git takes the tag for a bare "feature": merging it would be "Already up to date".
    repo("tag", "feature", "main")
    ui = jazygit()

    ui.branches.merge("feature")

    assert repo("rev-parse", "refs/heads/main") == repo("rev-parse", "refs/heads/feature")
    assert repo.subjects() == ["Add two", "Add one"]


def test_a_rebase_goes_onto_the_branch_when_a_tag_has_its_name(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("switch", "main")
    repo.commit("Add three", **{"three.txt": "three\n"})
    repo("tag", "main", "HEAD~1")
    repo("switch", "feature")
    main = repo("rev-parse", "refs/heads/main")
    ui = jazygit()

    ui.branches.rebase_onto("main")

    assert repo("merge-base", "refs/heads/feature", "refs/heads/main") == main
    assert repo.subjects() == ["Add two", "Add three", "Add one"]


def overwritten_on_checkout(repo):
    """Main, checked out, with an uncommitted change to one.txt, which feature changes too: checking out feature
    would overwrite it."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Change one on feature", **{"one.txt": "feature\n"})
    repo("switch", "main")
    repo.write("one.txt", "mine\n")


def test_declining_to_bring_the_changes_along_leaves_them_and_the_branch_as_they_were(repo, jazygit):
    overwritten_on_checkout(repo)
    ui = jazygit()

    ui.branches.try_to_checkout("feature")

    ui.app.wait_for("Checkout feature")
    ui.app.press("escape")
    ui.app.wait_gone("Checkout feature")
    ui.caught_up()
    assert repo.current_branch() == "main"
    assert repo.status() == {"one.txt": " M"}
    assert repo.read("one.txt") == "mine\n"
    assert repo.stashes() == []


def test_changes_that_do_not_apply_on_the_branch_checked_out_stay_in_the_stash(repo, jazygit):
    overwritten_on_checkout(repo)
    ui = jazygit()

    # Not checkout_bringing_changes, which waits for the app to show the repository while the error is open.
    ui.branches.try_to_checkout("feature")
    ui.confirm("Checkout feature")

    close_error(ui, "Checked out, but your stashed changes could not be applied and stay in the stash")
    ui.caught_up()
    assert repo.current_branch() == "feature"
    assert len(repo.stashes()) == 1
    assert repo("show", "stash@{0}:one.txt") == "mine"
    assert repo.subjects() == ["Change one on feature", "Add one"]


def test_moving_commits_onto_a_branch_is_undone_when_copying_them_fails(repo, jazygit):
    three_commits(repo)
    repo("branch", "elsewhere", "HEAD~2")
    repo.commit("Add four", **{"four.txt": "four\n"})
    main = repo("rev-parse", "main")
    elsewhere = repo("rev-parse", "elsewhere")
    failing_commits(repo)
    ui = jazygit()

    ui.commits.try_to_move_to_branch("Add four", "elsewhere")

    close_error(ui, "Moving commits to elsewhere failed")
    ui.caught_up()
    assert repo.in_progress() is None
    assert repo.current_branch() == "main"
    assert repo("rev-parse", "main") == main
    assert repo("rev-parse", "elsewhere") == elsewhere
    assert repo.status() == {}


def test_moving_commits_to_a_new_branch_elsewhere_is_undone_when_copying_them_fails(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    repo("switch", "main")
    repo.commit("Add main", **{"main.txt": "main\n"})
    repo("switch", "feature")
    feature = repo("rev-parse", "feature")
    failing_commits(repo)
    ui = jazygit()

    ui.commits.try_to_move_to_new_branch("Add three", "fix", start="main")

    close_error(ui, "Moving commits to fix failed")
    ui.caught_up()
    assert repo.in_progress() is None
    assert repo.branches() == ["feature", "main"]
    assert repo.current_branch() == "feature"
    assert repo("rev-parse", "feature") == feature
    assert repo.status() == {}


def test_a_pull_whose_rebase_fails_is_undone(repo, origin, clone, jazygit):
    published(repo, origin)
    teammate = clone()
    teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
    teammate("push")
    repo("config", "pull.rebase", "true")
    repo.commit("Add mine", **{"mine.txt": "mine\n"})
    main = repo("rev-parse", "main")
    failing_commits(repo)
    ui = jazygit()

    ui.branches.try_to_pull("main")

    close_error(ui, "Pulling branch main failed")
    ui.caught_up()
    assert repo.in_progress() is None
    assert repo.current_branch() == "main"
    assert repo("rev-parse", "main") == main
    assert repo.status() == {}
