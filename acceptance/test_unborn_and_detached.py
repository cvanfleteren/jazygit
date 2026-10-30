"""A repository before its first commit, where the branch checked out has no commits yet, and one whose HEAD is
detached, on a commit instead of a branch."""


def test_before_the_first_commit_there_is_no_branch_to_make_a_branch_from(repo, jazygit):
    repo.write("one.txt", "one\n")
    ui = jazygit()
    ui.caught_up()

    ui.app.focus("Branches")
    ui.app.press("n")
    # Keys are handled in turn: once the focus moved on, a dialog the key opened would show.
    ui.app.focus("Files")

    assert "New branch from" not in ui.app.text()
    assert ui.app.pane_text("Branches").strip() == ""
    assert repo.branches() == []


def test_the_first_commit_makes_the_branch_that_branches_can_then_be_made_from(repo, jazygit):
    repo.write("one.txt", "one\n")
    ui = jazygit()

    ui.files.commit("Add one")
    ui.branches.new("feature")

    assert repo.branches() == ["feature", "main"]
    assert repo.current_branch() == "feature"
    assert repo.subjects() == ["Add one"]


def detached(repo):
    """Main with three commits, and HEAD detached at the second."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    repo("switch", "--detach", "HEAD~1")


def test_a_detached_head_shows_its_commits(repo, jazygit):
    detached(repo)
    ui = jazygit()

    ui.caught_up()

    assert f"HEAD detached at {repo.short('HEAD')}" in ui.app.pane_text("Repo")
    assert repo.current_branch() == ""


def test_commit_on_a_detached_head_and_make_a_branch_of_it(repo, jazygit):
    detached(repo)
    main = repo("rev-parse", "main")
    repo.write("four.txt", "four\n")
    ui = jazygit()

    ui.files.commit("Add four")
    ui.commits.new_branch("Add four", "rescued")

    assert repo.current_branch() == "rescued"
    assert repo.subjects() == ["Add four", "Add two", "Add one"]
    assert repo("rev-parse", "main") == main


def test_checkout_a_branch_from_a_detached_head(repo, jazygit):
    detached(repo)
    ui = jazygit()

    ui.branches.checkout("main")

    assert repo.current_branch() == "main"
    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo.status() == {}
