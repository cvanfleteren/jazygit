"""Staging and discarding the changed files, one file, a directory or all of them."""

ALL = "/"


def test_stage_a_file_leaves_the_others_unstaged(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("two.txt", "two\nchanged\n")
    repo.write("new.txt", "new\n")
    ui = jazygit()

    ui.files.stage("one.txt")

    assert repo.status() == {"one.txt": "M ", "two.txt": " M", "new.txt": "??"}


def test_stage_an_untracked_file_adds_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("new.txt", "new\n")
    ui = jazygit()

    ui.files.stage("new.txt")

    assert repo.status() == {"new.txt": "A "}


def test_stage_a_partly_staged_file_stages_the_rest(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nstaged\n")
    repo("add", "one.txt")
    repo.write("one.txt", "one\nstaged\nunstaged\n")
    ui = jazygit()

    ui.files.stage("one.txt")

    assert repo.status() == {"one.txt": "M "}
    assert repo("show", ":one.txt") == "one\nstaged\nunstaged"


def test_unstage_a_staged_file_keeps_its_changes(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("two.txt", "two\nchanged\n")
    repo("add", "one.txt", "two.txt")
    ui = jazygit()

    ui.files.unstage("one.txt")

    assert repo.status() == {"one.txt": " M", "two.txt": "M "}
    assert repo.read("one.txt") == "one\nchanged\n"


def test_unstage_a_new_file_leaves_it_untracked(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("new.txt", "new\n")
    repo("add", "new.txt")
    ui = jazygit()

    ui.files.unstage("new.txt")

    assert repo.status() == {"new.txt": "??"}


def test_stage_a_directory_stages_only_the_files_in_it(repo, jazygit):
    repo.commit("Add files", **{"docs/one.md": "one\n", "docs/two.md": "two\n", "top.txt": "top\n"})
    repo.write("docs/one.md", "one\nchanged\n")
    repo.write("docs/new.md", "new\n")
    repo.write("top.txt", "top\nchanged\n")
    ui = jazygit()

    ui.files.stage("docs")

    assert repo.status() == {"docs/one.md": "M ", "docs/new.md": "A ", "top.txt": " M"}


def test_stage_and_unstage_all_files(repo, jazygit):
    repo.commit("Add files", **{"docs/one.md": "one\n", "docs/two.md": "two\n", "top.txt": "top\n"})
    repo.write("docs/one.md", "one\nchanged\n")
    repo.write("new.txt", "new\n")
    (repo.path / "top.txt").unlink()
    ui = jazygit()

    ui.files.stage(ALL)

    assert repo.status() == {"docs/one.md": "M ", "new.txt": "A ", "top.txt": "D "}

    ui.files.unstage(ALL)

    assert repo.status() == {"docs/one.md": " M", "new.txt": "??", "top.txt": " D"}


def test_discard_a_changed_file_leaves_the_others_alone(repo, jazygit):
    repo.commit("Add files", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nchanged\n")
    repo.write("two.txt", "two\nchanged\n")
    ui = jazygit()

    ui.files.discard("one.txt")

    assert repo.read("one.txt") == "one\n"
    assert repo.status() == {"two.txt": " M"}


def test_discard_a_staged_and_unstaged_file_throws_away_both(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n", "two.txt": "two\n"})
    repo.write("one.txt", "one\nstaged\n")
    repo("add", "one.txt")
    repo.write("one.txt", "one\nstaged\nunstaged\n")
    repo.write("two.txt", "two\nchanged\n")
    ui = jazygit()

    ui.files.discard("one.txt")

    assert repo.read("one.txt") == "one\n"
    assert repo.status() == {"two.txt": " M"}


def test_discard_only_the_unstaged_changes_keeps_the_staged_ones(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nstaged\n")
    repo("add", "one.txt")
    repo.write("one.txt", "one\nstaged\nunstaged\n")
    ui = jazygit()

    ui.files.discard("one.txt", only_unstaged=True)

    assert repo.read("one.txt") == "one\nstaged\n"
    assert repo.status() == {"one.txt": "M "}


def test_discard_an_untracked_file_deletes_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("new.txt", "new\n")
    repo.write("other.txt", "other\n")
    ui = jazygit()

    ui.files.discard("new.txt")

    assert repo.read("new.txt") is None
    assert repo.status() == {"other.txt": "??"}


def test_discard_a_staged_new_file_deletes_it(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("new.txt", "new\n")
    repo("add", "new.txt")
    ui = jazygit()

    ui.files.discard("new.txt")

    assert repo.read("new.txt") is None
    assert repo.status() == {}


def test_discard_a_deleted_file_brings_it_back(repo, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n", "two.txt": "two\n"})
    (repo.path / "one.txt").unlink()
    repo.write("two.txt", "two\nchanged\n")
    ui = jazygit()

    ui.files.discard("one.txt")

    assert repo.read("one.txt") == "one\n"
    assert repo.status() == {"two.txt": " M"}


def test_discard_a_directory_leaves_the_files_outside_it_alone(repo, jazygit):
    repo.commit("Add files", **{"docs/one.md": "one\n", "docs/two.md": "two\n", "top.txt": "top\n"})
    repo.write("docs/one.md", "one\nchanged\n")
    repo.write("docs/new.md", "new\n")
    repo.write("top.txt", "top\nchanged\n")
    ui = jazygit()

    ui.files.discard("docs")

    assert repo.read("docs/one.md") == "one\n"
    assert repo.read("docs/new.md") is None
    assert repo.status() == {"top.txt": " M"}


def test_discard_all_changes(repo, jazygit):
    repo.commit("Add files", **{"docs/one.md": "one\n", "docs/two.md": "two\n", "top.txt": "top\n"})
    repo.write("docs/one.md", "one\nchanged\n")
    repo.write("docs/two.md", "two\nstaged\n")
    repo("add", "docs/two.md")
    repo.write("new.txt", "new\n")
    (repo.path / "top.txt").unlink()
    ui = jazygit()

    ui.files.discard(ALL)

    assert repo.status() == {}
    assert repo.read("top.txt") == "top\n"
    assert repo.read("new.txt") is None
