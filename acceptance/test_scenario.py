"""
A day's work in one session, from an empty repository: each step starts from where the one before left the
repository and the app, the way it goes when a person uses it.
"""

from steps import select_branch, select_commit, stage_and_commit, type_over


def test_a_feature_from_first_commit_to_tidy_history(repo, jazygit):
    repo.write("README.md", "# Parser\n")
    app = jazygit()

    # Start the history.
    stage_and_commit(app, repo, "Initial commit", **{"README.md": "# Parser\n"})
    assert repo.subjects() == ["Initial commit"]

    # Branch off for the feature.
    select_branch(app, repo, "main")
    app.press("n")
    app.wait_for("New branch from main")
    app.type("feature")
    app.press("enter")
    app.eventually(lambda: repo.current_branch() == "feature", "feature to be checked out")

    # Work on it, with a typo fixed in a commit of its own.
    stage_and_commit(app, repo, "Add parser", **{"parser.py": "def parse(text):\n    retrun text\n"})
    stage_and_commit(app, repo, "Fix typo in parser", **{"parser.py": "def parse(text):\n    return text\n"})
    stage_and_commit(app, repo, "Add tests", **{"test_parser.py": "assert parse('a') == 'a'\n"})
    assert repo.subjects() == ["Add tests", "Fix typo in parser", "Add parser", "Initial commit"]

    # Fold the typo fix into the commit it fixes.
    select_commit(app, repo, "Fix typo in parser")
    app.press("f")
    app.wait_for("Fixup and use this commit's message")
    app.press("f")
    app.eventually(lambda: repo.subjects() == ["Add tests", "Add parser", "Initial commit"], "the fix folded in")
    assert repo("show", f"{repo.find('Add parser')}:parser.py") == "def parse(text):\n    return text"

    # Say better what the tests are.
    select_commit(app, repo, "Add tests")
    app.press("r")
    app.wait_for("Commit summary")
    type_over(app, "Test the parser")
    app.press("enter")
    app.eventually(lambda: repo.subjects()[0] == "Test the parser", "the tests reworded")

    # A file forgotten in the last commit goes in after all.
    repo.write("conftest.py", "from parser import parse\n")
    app.focus("Files")
    app.wait_in("Files", "conftest.py")
    app.press("space")
    app.eventually(lambda: repo("diff", "--cached", "--name-only") == "conftest.py", "conftest.py to be staged")
    app.press("A")
    app.wait_for("Amend last commit")
    app.press("enter")
    app.eventually(lambda: "conftest.py" in repo("ls-tree", "--name-only", "HEAD").splitlines(), "the amended commit")
    assert repo.subjects() == ["Test the parser", "Add parser", "Initial commit"]
    assert repo("status", "--porcelain") == ""

    # Debugging that should never have been committed.
    stage_and_commit(app, repo, "Debug logging", **{"debug.log": "lots\n"})
    select_commit(app, repo, "Debug logging")
    app.press("d")
    app.wait_for("Drop commit")
    app.press("enter")
    app.eventually(lambda: repo.subjects() == ["Test the parser", "Add parser", "Initial commit"], "the drop")
    assert not (repo.path / "debug.log").exists()

    # Two work-in-progress commits become one.
    stage_and_commit(app, repo, "WIP printer", **{"printer.py": "def show(tree):\n"})
    stage_and_commit(app, repo, "WIP printer, more", **{"printer.py": "def show(tree):\n    print(tree)\n"})
    select_commit(app, repo, "WIP printer, more")
    app.press("s")
    app.wait_for("Squash commit")
    app.press("enter")
    app.eventually(lambda: repo.subjects()[:2] == ["WIP printer", "Test the parser"], "the squash")
    select_commit(app, repo, "WIP printer")
    app.press("r")
    app.wait_for("Commit summary")
    type_over(app, "Add printer")
    app.press("enter")
    app.eventually(lambda: repo.subjects() == ["Add printer", "Test the parser", "Add parser", "Initial commit"],
                   "the squashed commit reworded")
    assert repo("show", "HEAD:printer.py") == "def show(tree):\n    print(tree)"

    # The branch gets the name it should have had.
    select_branch(app, repo, "feature")
    app.press("R")
    app.wait_for("Rename branch feature")
    type_over(app, "parser")
    app.press("enter")
    app.eventually(lambda: repo.branches() == ["main", "parser"], "feature renamed to parser")
    assert repo.current_branch() == "parser"

    # Back on main, a fix is committed there by mistake, and moved to a branch of its own.
    select_branch(app, repo, "main")
    app.press("space")
    app.eventually(lambda: repo.current_branch() == "main", "main to be checked out")
    app.wait_in("Branches", "* main")
    stage_and_commit(app, repo, "Fix README title", **{"README.md": "# The parser\n"})
    stage_and_commit(app, repo, "Mention the printer", **{"README.md": "# The parser\n\nAnd a printer.\n"})
    select_commit(app, repo, "Fix README title")
    app.press("N")
    app.wait_for("Move 2 commits of main to")
    app.wait_for("New branch…")
    app.press("enter")
    app.wait_for("Move 2 commits of main to a new branch")
    app.type("readme")
    app.press("enter")
    app.eventually(lambda: repo.current_branch() == "readme", "readme to be checked out")
    assert repo.subjects() == ["Mention the printer", "Fix README title", "Initial commit"]
    assert repo.subjects("main") == ["Initial commit"]

    # A spike from the parser alone, before the tests, that turns out to go nowhere.
    select_branch(app, repo, "parser")
    app.press("space")
    app.eventually(lambda: repo.current_branch() == "parser", "parser to be checked out")
    select_commit(app, repo, "Add parser")
    app.press("n")
    app.wait_for(f"New branch from {repo.short(repo.find('Add parser'))}")
    app.type("spike")
    app.press("enter")
    app.eventually(lambda: repo.current_branch() == "spike", "spike to be checked out")
    assert repo.subjects() == ["Add parser", "Initial commit"]
    stage_and_commit(app, repo, "Try a regex", **{"parser.py": "import re\n"})

    select_branch(app, repo, "parser")
    app.press("space")
    app.eventually(lambda: repo.current_branch() == "parser", "parser to be checked out again")
    select_branch(app, repo, "spike")
    app.press("d")
    app.wait_for("Delete branch spike")
    app.press("d")
    app.eventually(lambda: repo.branches() == ["main", "parser", "readme"], "spike to be deleted")

    # What is left: three branches, each with only what belongs on it.
    assert repo.subjects("main") == ["Initial commit"]
    assert repo.subjects("parser") == ["Add printer", "Test the parser", "Add parser", "Initial commit"]
    assert repo.subjects("readme") == ["Mention the printer", "Fix README title", "Initial commit"]
    assert repo("ls-tree", "-r", "--name-only", "parser").splitlines() == [
        "README.md", "conftest.py", "parser.py", "printer.py", "test_parser.py"]
    assert repo("status", "--porcelain") == ""
