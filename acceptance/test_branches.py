"""Making, checking out, renaming and deleting branches, and moving commits between them."""


def three_commits(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})


def test_new_branch_from_the_checked_out_branch(repo, jazygit):
    three_commits(repo)
    ui = jazygit()

    ui.branches.new("feature")

    assert repo.current_branch() == "feature"
    assert repo.branches() == ["feature", "main"]
    assert repo("rev-parse", "feature") == repo("rev-parse", "main")


def test_checkout_a_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "older", "HEAD~2")
    ui = jazygit()

    ui.branches.checkout("older")

    assert repo.current_branch() == "older"
    assert repo.subjects() == ["Add one"]
    assert not (repo.path / "two.txt").exists()


def test_rename_a_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "feature")
    ui = jazygit()

    ui.branches.rename("feature", "renamed")

    assert repo.branches() == ["main", "renamed"]
    assert repo.current_branch() == "main"


def test_rename_the_checked_out_branch(repo, jazygit):
    three_commits(repo)
    ui = jazygit()

    ui.branches.rename("main", "trunk")

    assert repo.current_branch() == "trunk"
    assert repo.branches() == ["trunk"]
    assert repo.subjects() == ["Add three", "Add two", "Add one"]


def test_delete_a_merged_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "done", "HEAD~1")
    ui = jazygit()

    ui.branches.delete("done")

    assert repo.branches() == ["main"]
    assert repo.subjects() == ["Add three", "Add two", "Add one"]


def test_delete_a_branch_with_commits_of_its_own(repo, jazygit):
    three_commits(repo)
    repo("switch", "--create", "abandoned")
    repo.commit("Try something", **{"try.txt": "try\n"})
    repo("switch", "main")
    ui = jazygit()

    ui.branches.delete("abandoned")

    assert repo.branches() == ["main"]
    assert repo.current_branch() == "main"


def test_new_branch_from_an_older_commit(repo, jazygit):
    three_commits(repo)
    ui = jazygit()

    ui.commits.new_branch("Add two", "from-two")

    assert repo.current_branch() == "from-two"
    assert repo.subjects() == ["Add two", "Add one"]
    assert repo.subjects("main") == ["Add three", "Add two", "Add one"]


def test_move_commits_to_a_new_branch(repo, jazygit):
    three_commits(repo)
    repo.commit("Add four", **{"four.txt": "four\n"})
    ui = jazygit()

    ui.commits.move_to_new_branch("Add three", "wrong-branch")

    assert repo.current_branch() == "wrong-branch"
    assert repo.subjects() == ["Add four", "Add three", "Add two", "Add one"]
    assert repo.subjects("main") == ["Add two", "Add one"]
    assert repo("status", "--porcelain") == ""


def test_move_commits_onto_an_existing_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "elsewhere", "HEAD~2")
    repo.commit("Add four", **{"four.txt": "four\n"})
    ui = jazygit()

    ui.commits.move_to_branch("Add four", "elsewhere")

    assert repo.current_branch() == "elsewhere"
    assert repo.subjects() == ["Add four", "Add one"]
    assert repo.subjects("main") == ["Add three", "Add two", "Add one"]
    assert repo("status", "--porcelain") == ""


def test_a_branch_says_how_far_its_base_moved_on(repo, jazygit):
    three_commits(repo)
    repo("branch", "feature", "HEAD~2")
    repo("branch", "stacked", "HEAD~2")
    repo("config", "branch.stacked.gh-merge-base", "feature")
    ui = jazygit()
    ui.caught_up()

    # feature misses the two commits main got since; stacked has all of feature, its parent.
    assert ui.app.eventually(lambda: ui.branches.behind("feature"), "how far feature is behind main") == 2
    assert ui.branches.behind("stacked") is None
    assert ui.branches.behind("main") is None
