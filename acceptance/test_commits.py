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


def test_commit_files_with_spaces_and_accents_in_their_names(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("my notes.txt", "notes\n")
    repo.write("café.txt", "coffee\n")
    ui = jazygit()

    ui.files.stage("my notes.txt")
    ui.files.commit("Add notes")
    ui.files.commit("Add café")

    assert repo.subjects() == ["Add café", "Add notes", "Add one"]
    assert repo("show", "--format=", "--name-only", "-z", "HEAD~").strip("\0") == "my notes.txt"
    assert repo("show", "--format=", "--name-only", "-z", "HEAD").strip("\0") == "café.txt"
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


def test_amend_the_last_commit_with_nothing_staged_adds_all_changes(repo, jazygit):
    three_commits(repo)
    repo.write("three.txt", "three\nand a bit\n")
    repo.write("new.txt", "new\n")
    ui = jazygit()

    ui.files.amend_last_commit()

    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo("show", "HEAD:three.txt") == "three\nand a bit"
    assert repo("show", "HEAD:new.txt") == "new"
    assert repo.status() == {}


def test_amend_an_older_commit_with_nothing_staged_adds_all_changes(repo, jazygit):
    three_commits(repo)
    repo.write("one.txt", "one, amended\n")
    ui = jazygit()

    ui.commits.amend("Add one")

    assert repo.subjects() == ["Add three", "Add two", "Add one"]
    assert repo("show", f"{repo.find('Add one')}:one.txt") == "one, amended"
    assert repo.status() == {}


def test_reword_keeps_the_body_the_author_and_the_date_of_the_commit(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("two.txt", "two\n")
    repo("add", "two.txt")
    repo("commit", "--author", "Someone Else <else@example.com>", "--date", "2025-06-15T08:30:00Z",
         "--message", "Add two\n\nBecause one is not enough.")
    repo.commit("Add three", **{"three.txt": "three\n"})
    ui = jazygit()

    ui.commits.reword("Add two", "Add the second file")

    reworded = repo.find("Add the second file")
    assert repo.message(reworded) == "Add the second file\n\nBecause one is not enough."
    assert repo("log", "-1", "--format=%an <%ae> %aI", reworded) == (
        "Someone Else <else@example.com> 2025-06-15T08:30:00Z")


def test_reword_keeps_the_uncommitted_changes(repo, jazygit):
    three_commits(repo)
    repo.write("one.txt", "one, being worked on\n")
    repo.write("two.txt", "two, staged\n")
    repo("add", "two.txt")
    ui = jazygit()

    ui.commits.reword("Add one", "Add the first file")

    assert repo.subjects() == ["Add three", "Add two", "Add the first file"]
    assert repo.read("one.txt") == "one, being worked on\n"
    assert repo.read("two.txt") == "two, staged\n"
    assert repo.stashes() == []


def with_a_merge(repo):
    """Main with a merge of feature in its history, and a commit on top: Add four, the merge, Add three on main
    and Add two on feature, Add one."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo("switch", "main")
    repo.commit("Add three", **{"three.txt": "three\n"})
    repo("merge", "--no-ff", "--no-edit", "feature")
    repo.commit("Add four", **{"four.txt": "four\n"})


def merges(repo) -> list:
    """The subjects of the merge commits of HEAD, and how many parents each has."""
    return [(subject, repo.parents(sha)) for sha, subject in
            (line.split(" ", 1) for line in repo("log", "--merges", "--format=%H %s").splitlines())]


def test_reword_a_commit_below_a_merge_keeps_the_merge(repo, jazygit):
    with_a_merge(repo)
    ui = jazygit()

    ui.commits.reword("Add three", "Add the third file")

    assert merges(repo) == [("Merge branch 'feature'", 2)]
    assert repo.subjects("HEAD~1^1") == ["Add the third file", "Add one"]
    assert repo("rev-parse", "HEAD~1^2") == repo("rev-parse", "feature")
    assert repo.subjects()[0] == "Add four"


def test_drop_a_commit_below_a_merge_keeps_the_merge(repo, jazygit):
    with_a_merge(repo)
    ui = jazygit()

    ui.commits.drop("Add three")

    assert merges(repo) == [("Merge branch 'feature'", 2)]
    assert repo.subjects("HEAD~1^1") == ["Add one"]
    assert repo("ls-tree", "-r", "--name-only", "HEAD").splitlines() == ["four.txt", "one.txt", "two.txt"]


def test_amend_a_commit_below_a_merge_keeps_the_merge(repo, jazygit):
    with_a_merge(repo)
    repo.write("three.txt", "three, amended\n")
    repo("add", "three.txt")
    ui = jazygit()

    ui.commits.amend("Add three")

    assert merges(repo) == [("Merge branch 'feature'", 2)]
    assert repo("show", "HEAD~1^1:three.txt") == "three, amended"
    assert repo("show", "HEAD:three.txt") == "three, amended"
    assert repo.status() == {}


def test_amend_a_merge_that_is_the_last_commit(repo, jazygit):
    with_a_merge(repo)
    repo("reset", "--hard", "HEAD~1")
    repo.write("three.txt", "three, amended\n")
    repo("add", "three.txt")
    ui = jazygit()

    ui.files.amend_last_commit()

    assert merges(repo) == [("Merge branch 'feature'", 2)]
    assert repo("show", "HEAD:three.txt") == "three, amended"


def test_a_merge_that_is_not_the_last_commit_is_not_amended(repo, jazygit):
    with_a_merge(repo)
    head = repo("rev-parse", "HEAD")
    repo.write("three.txt", "three, amended\n")
    repo("add", "three.txt")
    ui = jazygit()

    ui.commits.try_to_amend("Merge branch 'feature'")

    ui.app.wait_for("A merge commit cannot be amended")
    assert repo("rev-parse", "HEAD") == head
    assert repo.status() == {"three.txt": "M "}


def asks_nothing(ui, key: str, title: str):
    """Presses key on the selected commit, and checks the app asks nothing: keys are handled in turn, so once the
    focus moved on to another pane, which an open dialog would keep, the dialog called title would show by then."""
    ui.app.press(key)
    ui.app.focus("Branches")
    assert title not in ui.app.text()


def test_a_merge_commit_is_not_squashed_or_dropped(repo, jazygit):
    with_a_merge(repo)
    head = repo("rev-parse", "HEAD")
    ui = jazygit()

    ui.commits.select("Merge branch 'feature'")
    asks_nothing(ui, "s", "Squash commit")
    ui.commits.select("Merge branch 'feature'")
    asks_nothing(ui, "d", "Drop commit")

    ui.caught_up()
    assert repo("rev-parse", "HEAD") == head


def test_the_first_commit_is_not_squashed(repo, jazygit):
    three_commits(repo)
    head = repo("rev-parse", "HEAD")
    ui = jazygit()

    ui.commits.select("Add one")
    asks_nothing(ui, "s", "Squash commit")

    ui.caught_up()
    assert repo("rev-parse", "HEAD") == head


def test_the_only_commit_is_not_dropped(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    head = repo("rev-parse", "HEAD")
    ui = jazygit()

    ui.commits.select("Add one")
    asks_nothing(ui, "d", "Drop commit")

    ui.caught_up()
    assert repo("rev-parse", "HEAD") == head
