"""Making, checking out, renaming and deleting branches, and moving commits between them."""

from steps import select_branch, select_commit, type_over


def three_commits(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})


def test_new_branch_from_the_checked_out_branch(repo, jazygit):
    three_commits(repo)
    app = jazygit()

    select_branch(app, repo, "main")
    app.press("n")
    app.wait_for("New branch from main")
    app.type("feature")
    app.press("enter")

    app.eventually(lambda: repo.current_branch() == "feature", "feature to be checked out")
    assert repo.branches() == ["feature", "main"]
    assert repo("rev-parse", "feature") == repo("rev-parse", "main")


def test_checkout_a_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "older", "HEAD~2")
    app = jazygit()

    select_branch(app, repo, "older")
    app.press("space")

    app.eventually(lambda: repo.current_branch() == "older", "older to be checked out")
    assert repo.subjects() == ["Add one"]
    assert not (repo.path / "two.txt").exists()


def test_rename_a_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "feature")
    app = jazygit()

    select_branch(app, repo, "feature")
    app.press("R")
    app.wait_for("Rename branch feature")
    type_over(app, "renamed")
    app.press("enter")

    app.eventually(lambda: repo.branches() == ["main", "renamed"], "feature to be renamed")
    assert repo.current_branch() == "main"


def test_rename_the_checked_out_branch(repo, jazygit):
    three_commits(repo)
    app = jazygit()

    select_branch(app, repo, "main")
    app.press("R")
    app.wait_for("Rename branch main")
    type_over(app, "trunk")
    app.press("enter")

    app.eventually(lambda: repo.current_branch() == "trunk", "main to be renamed")
    assert repo.branches() == ["trunk"]
    assert repo.subjects() == ["Add three", "Add two", "Add one"]


def test_delete_a_merged_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "done", "HEAD~1")
    app = jazygit()

    select_branch(app, repo, "done")
    app.press("d")
    app.wait_for("Delete branch done")
    app.press("d")

    app.eventually(lambda: repo.branches() == ["main"], "done to be deleted")
    assert repo.subjects() == ["Add three", "Add two", "Add one"]


def test_delete_a_branch_with_commits_of_its_own(repo, jazygit):
    three_commits(repo)
    repo("switch", "--create", "abandoned")
    repo.commit("Try something", **{"try.txt": "try\n"})
    repo("switch", "main")
    app = jazygit()

    select_branch(app, repo, "abandoned")
    app.press("d")
    app.wait_for("Delete branch abandoned")
    app.press("d")

    app.eventually(lambda: repo.branches() == ["main"], "abandoned to be deleted")
    assert repo.current_branch() == "main"


def test_new_branch_from_an_older_commit(repo, jazygit):
    three_commits(repo)
    app = jazygit()

    select_commit(app, repo, "Add two")
    app.press("n")
    app.wait_for(f"New branch from {repo.short('HEAD~1')}")
    app.type("from-two")
    app.press("enter")

    app.eventually(lambda: repo.current_branch() == "from-two", "from-two to be checked out")
    assert repo.subjects() == ["Add two", "Add one"]
    assert repo.subjects("main") == ["Add three", "Add two", "Add one"]


def test_move_commits_to_a_new_branch(repo, jazygit):
    three_commits(repo)
    repo.commit("Add four", **{"four.txt": "four\n"})
    app = jazygit()

    select_commit(app, repo, "Add three")
    app.press("N")
    app.wait_for("Move 2 commits of main to")
    app.wait_for("New branch…")
    app.press("enter")
    app.wait_for("Move 2 commits of main to a new branch")
    app.type("wrong-branch")
    app.press("enter")

    app.eventually(lambda: repo.current_branch() == "wrong-branch", "wrong-branch to be checked out")
    assert repo.subjects() == ["Add four", "Add three", "Add two", "Add one"]
    assert repo.subjects("main") == ["Add two", "Add one"]
    assert repo("status", "--porcelain") == ""


def test_move_commits_onto_an_existing_branch(repo, jazygit):
    three_commits(repo)
    repo("branch", "elsewhere", "HEAD~2")
    repo.commit("Add four", **{"four.txt": "four\n"})
    app = jazygit()

    select_commit(app, repo, "Add four")
    app.press("N")
    app.wait_for("Move 1 commit of main to")
    app.press("/")
    app.type("elsewhere")
    app.wait_gone("New branch…")
    app.press("enter")

    app.eventually(lambda: repo.current_branch() == "elsewhere", "elsewhere to be checked out")
    assert repo.subjects() == ["Add four", "Add one"]
    assert repo.subjects("main") == ["Add three", "Add two", "Add one"]
    assert repo("status", "--porcelain") == ""
