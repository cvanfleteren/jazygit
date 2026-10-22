"""Making commits and rewriting them: amending, rewording, squashing, fixing up and dropping."""


def three_commits(repo):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})


def test_add_commits_one_after_another(repo, jazygit):
    repo.write("one.txt", "one\n")
    ui = jazygit()

    ui.files.commit("Add one")
    repo.write("two.txt", "two\n")
    ui.files.commit("Add two")
    repo.write("three.txt", "three\n")
    ui.files.stage("three.txt")
    ui.files.commit("Add three", description="Because two is not enough.")

    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo.message() == "Add three\n\nBecause two is not enough."
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt", "two.txt"]
    assert repo("status", "--porcelain") == ""


def test_amend_the_last_commit_with_the_staged_changes(repo, jazygit):
    three_commits(repo)
    repo.write("three.txt", "three\nand a bit\n")
    repo("add", "three.txt")
    repo.write("unrelated.txt", "not staged\n")
    ui = jazygit()

    ui.files.amend_last_commit()

    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo("show", "HEAD:three.txt") == "three\nand a bit"
    assert repo("status", "--porcelain") == "?? unrelated.txt"


def test_amend_an_older_commit_rewrites_the_ones_after_it(repo, jazygit):
    three_commits(repo)
    repo.write("one.txt", "one, amended\n")
    repo("add", "one.txt")
    ui = jazygit()

    ui.commits.amend("Add one")

    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo("show", f"{repo.find('Add one')}:one.txt") == "one, amended"
    assert repo("show", "HEAD:one.txt") == "one, amended"
    assert repo("show", "HEAD:three.txt") == "three"
    assert repo("status", "--porcelain") == ""


def test_reword_a_commit_in_the_middle(repo, jazygit):
    three_commits(repo)
    tree = repo("rev-parse", "HEAD^{tree}")
    ui = jazygit()

    ui.commits.reword("Add two", "Add the second file")

    assert repo.subjects() == ["Add three", "Add the second file", "Add one"]
    assert repo("rev-parse", "HEAD^{tree}") == tree
    assert repo.current_branch() == "main"


def test_squash_a_commit_into_the_one_below(repo, jazygit):
    three_commits(repo)
    ui = jazygit()

    ui.commits.squash("Add three")

    assert repo.subjects() == ["Add two", "Add one"]
    assert "Add three" in repo.message()
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt", "two.txt"]


def test_fixup_keeps_the_message_of_the_commit_below(repo, jazygit):
    three_commits(repo)
    ui = jazygit()

    ui.commits.fixup("Add two")

    assert repo.subjects() == ["Add three", "Add one"]
    assert repo.message("HEAD~") == "Add one"
    assert repo("ls-tree", "-r", "--name-only", "HEAD~").splitlines() == ["one.txt", "two.txt"]


def test_fixup_can_keep_the_message_of_the_selected_commit(repo, jazygit):
    three_commits(repo)
    ui = jazygit()

    ui.commits.fixup("Add three", keep_message="selected")

    assert repo.subjects() == ["Add three", "Add one"]
    assert repo.message() == "Add three"
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt", "two.txt"]


def test_drop_a_commit_keeps_the_uncommitted_changes(repo, jazygit):
    three_commits(repo)
    repo.write("one.txt", "one, still being worked on\n")
    ui = jazygit()

    ui.commits.drop("Add two")

    assert repo.subjects() == ["Add three", "Add one"]
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["one.txt", "three.txt"]
    assert not (repo.path / "two.txt").exists()
    assert repo("status", "--porcelain") == "M one.txt"
    assert (repo.path / "one.txt").read_text() == "one, still being worked on\n"
