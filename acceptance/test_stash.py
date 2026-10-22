"""Looking at what a stash holds."""


def test_selecting_a_stash_shows_what_it_stashed(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("new.txt", "brand new\n")
    repo("stash", "push", "--include-untracked", "--message", "older")
    repo.write("one.txt", "one\nnewer\n")
    repo("stash", "push", "--message", "newer")
    # The pane is as tall as its stashes, and with two rows the cursor's is not the only background.
    repo.write("one.txt", "one\nnewest\n")
    repo("stash", "push", "--message", "newest")
    ui = jazygit()

    ui.stash.select("older")

    screen = ui.app.text()
    assert "Branch: main" in screen
    assert "+changed" in screen
    # Its untracked file is part of it.
    assert "+brand new" in screen
    assert "+newer" not in screen
