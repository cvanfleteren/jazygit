"""Making commits and rewriting them: amending, rewording, squashing, fixing up and dropping."""

from steps import select_commit, stage_and_commit, type_over


def three_commits(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})


def test_add_commits_one_after_another(repo, jazygit):
    repo.write("one.txt", "one\n")
    app = jazygit()

    stage_and_commit(app, repo, "Add one", **{"one.txt": "one\n"})
    stage_and_commit(app, repo, "Add two", **{"two.txt": "two\n"})

    app.focus("Files")
    repo.write("three.txt", "three\n")
    app.wait_in("Files", "three.txt")
    app.press("space")
    app.eventually(lambda: repo("diff", "--cached", "--name-only") == "three.txt", "three.txt to be staged")
    app.press("c")
    app.wait_for("Commit summary")
    app.type("Add three")
    app.press("tab")
    app.type("Because two is not enough.")
    app.press("ctrl+s")

    app.eventually(lambda: len(repo.subjects()) == 3, "a third commit")
    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo.message() == "Add three\n\nBecause two is not enough."
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt", "two.txt"]
    assert repo("status", "--porcelain") == ""


def test_amend_the_last_commit_with_the_staged_changes(repo, jazygit):
    three_commits(repo)
    repo.write("three.txt", "three\nand a bit\n")
    repo("add", "three.txt")
    repo.write("unrelated.txt", "not staged\n")
    app = jazygit()

    app.focus("Files")
    app.wait_in("Files", "unrelated.txt")
    app.press("A")
    app.wait_for("Amend last commit")
    app.press("enter")

    app.eventually(lambda: repo("diff", "--cached", "--name-only") == "", "the staged changes to be committed")
    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo("show", "HEAD:three.txt") == "three\nand a bit"
    assert repo("status", "--porcelain") == "?? unrelated.txt"


def test_amend_an_older_commit_rewrites_the_ones_after_it(repo, jazygit):
    three_commits(repo)
    repo.write("one.txt", "one, amended\n")
    repo("add", "one.txt")
    app = jazygit()

    select_commit(app, repo, "Add one")
    app.press("A")
    app.wait_for("Amend commit")
    app.press("enter")

    app.eventually(lambda: repo("show", f"{repo.find('Add one')}:one.txt") == "one, amended", "Add one amended")
    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo("show", "HEAD:one.txt") == "one, amended"
    assert repo("show", "HEAD:three.txt") == "three"
    assert repo("status", "--porcelain") == ""


def test_reword_a_commit_in_the_middle(repo, jazygit):
    three_commits(repo)
    tree = repo("rev-parse", "HEAD^{tree}")
    app = jazygit()

    select_commit(app, repo, "Add two")
    app.press("r")
    app.wait_for("Commit summary")
    type_over(app, "Add the second file")
    app.press("enter")

    app.eventually(lambda: repo.subjects() == ["Add three", "Add the second file", "Add one"], "the reworded commit")
    assert repo("rev-parse", "HEAD^{tree}") == tree
    assert repo.current_branch() == "main"


def test_squash_a_commit_into_the_one_below(repo, jazygit):
    three_commits(repo)
    app = jazygit()

    select_commit(app, repo, "Add three")
    app.press("s")
    app.wait_for("Squash commit")
    app.press("enter")

    app.eventually(lambda: len(repo.subjects()) == 2, "two commits")
    assert repo.subjects() == ["Add two", "Add one"]
    assert "Add three" in repo.message()
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt", "two.txt"]


def test_fixup_keeps_the_message_of_the_commit_below(repo, jazygit):
    three_commits(repo)
    app = jazygit()

    select_commit(app, repo, "Add two")
    app.press("f")
    app.wait_for("Fixup and use this commit's message")
    app.press("f")

    app.eventually(lambda: repo.subjects() == ["Add three", "Add one"], "two commits")
    assert repo.message("HEAD~") == "Add one"
    assert repo("ls-tree", "-r", "--name-only", "HEAD~").splitlines() == ["one.txt", "two.txt"]


def test_fixup_can_keep_the_message_of_the_selected_commit(repo, jazygit):
    three_commits(repo)
    app = jazygit()

    select_commit(app, repo, "Add three")
    app.press("f")
    app.wait_for("Fixup and use this commit's message")
    app.press("C")

    app.eventually(lambda: repo.subjects() == ["Add three", "Add one"], "two commits")
    assert repo.message() == "Add three"
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt", "two.txt"]


def test_drop_a_commit_keeps_the_uncommitted_changes(repo, jazygit):
    three_commits(repo)
    repo.write("one.txt", "one, still being worked on\n")
    app = jazygit()

    select_commit(app, repo, "Add two")
    app.press("d")
    app.wait_for("Drop commit")
    app.press("enter")

    app.eventually(lambda: repo.subjects() == ["Add three", "Add one"], "Add two to be dropped")
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt"]
    assert not (repo.path / "two.txt").exists()
    app.eventually(lambda: repo("status", "--porcelain") == "M one.txt", "the change to one.txt to be back")
    assert (repo.path / "one.txt").read_text() == "one, still being worked on\n"
