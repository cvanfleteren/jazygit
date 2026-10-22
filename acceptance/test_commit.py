
def test_commit_a_single_file_in_an_empty_repository(repo, jazygit):
    repo.write("foo.txt", "foo\n")

    # The keys spelled out, as the page objects in pages.py press them for the other tests.
    app = jazygit().app
    app.wait_in("Files", "foo.txt")

    app.press("space")
    app.eventually(lambda: repo("diff", "--cached", "--name-only") == "foo.txt", "foo.txt to be staged")
    # The app commits what it last read as staged; until it read the files again, it would offer to stage them.
    app.wait_in("Files", "A  foo.txt")

    app.press("c")
    app.wait_for("Commit summary")
    app.type("Add foo")
    app.press("enter")

    app.eventually(repo.has_head, "a commit")
    assert repo("log", "--format=%s") == "Add foo"
    assert repo("ls-tree", "-r", "--name-only", "HEAD") == "foo.txt"
    assert repo("show", "HEAD:foo.txt") == "foo"
    assert repo("status", "--porcelain") == ""

