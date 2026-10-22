"""Resetting the checked out branch to an older commit from the commits pane."""


def history(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Change one", **{"one.txt": "one\nchanged\n"})


def test_soft_reset_keeps_the_changes_since_staged(repo, jazygit):
    history(repo)
    ui = jazygit()

    ui.commits.reset("Add one", mode="soft")

    assert repo.subjects() == ["Add one"]
    assert repo.status() == {"one.txt": "M ", "two.txt": "A "}
    assert repo.read("one.txt") == "one\nchanged\n"


def test_mixed_reset_keeps_the_changes_since_unstaged(repo, jazygit):
    history(repo)
    ui = jazygit()

    ui.commits.reset("Add one", mode="mixed")

    assert repo.subjects() == ["Add one"]
    assert repo.status() == {"one.txt": " M", "two.txt": "??"}


def test_mixed_reset_keeps_the_uncommitted_changes(repo, jazygit):
    history(repo)
    repo.write("two.txt", "two\nuncommitted\n")
    ui = jazygit()

    ui.commits.reset("Add two", mode="mixed")

    assert repo.subjects() == ["Add two", "Add one"]
    assert repo.status() == {"one.txt": " M", "two.txt": " M"}
    assert repo.read("two.txt") == "two\nuncommitted\n"


def test_hard_reset_without_changes_drops_the_commits_since(repo, jazygit):
    history(repo)
    ui = jazygit()

    ui.commits.reset("Add one", mode="hard")

    assert repo.subjects() == ["Add one"]
    assert repo.status() == {}
    assert repo.read("two.txt") is None


def test_hard_reset_discards_the_uncommitted_changes_once_confirmed(repo, jazygit):
    history(repo)
    repo.write("one.txt", "one\nuncommitted\n")
    repo.write("new.txt", "untracked\n")
    ui = jazygit()

    ui.commits.reset("Add two", mode="hard")

    assert repo.subjects() == ["Add two", "Add one"]
    assert repo.read("one.txt") == "one\n"
    # Untracked files are not git's to discard.
    assert repo.status() == {"new.txt": "??"}
