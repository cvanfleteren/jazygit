"""
A day's work in one session, from an empty repository: each step starts from where the one before left the
repository and the app, the way it goes when a person uses it.
"""

import pytest

# By far the longest tests: they go first, so they are not what the run waits on at the end.
pytestmark = pytest.mark.slow


def test_a_feature_from_first_commit_to_tidy_history(repo, jazygit):
    repo.write("README.md", "# Parser\n")
    ui = jazygit()

    # Start the history, and branch off for the feature.
    ui.files.commit("Initial commit")
    ui.branches.new("feature")
    assert repo.current_branch() == "feature"

    # Work on it, with a typo fixed in a commit of its own.
    repo.write("parser.py", "def parse(text):\n    retrun text\n")
    ui.files.commit("Add parser")
    repo.write("parser.py", "def parse(text):\n    return text\n")
    ui.files.commit("Fix typo in parser")
    repo.write("test_parser.py", "assert parse('a') == 'a'\n")
    ui.files.commit("Add tests")
    assert repo.subjects() == ["Add tests", "Fix typo in parser", "Add parser", "Initial commit"]

    # Fold the typo fix into the commit it fixes, and say better what the tests are.
    ui.commits.fixup("Fix typo in parser")
    assert repo.subjects() == ["Add tests", "Add parser", "Initial commit"]
    assert repo("show", f"{repo.find('Add parser')}:parser.py") == "def parse(text):\n    return text"
    ui.commits.reword("Add tests", "Test the parser")

    # A file forgotten in the last commit goes in after all.
    repo.write("conftest.py", "from parser import parse\n")
    ui.files.stage("conftest.py")
    ui.files.amend_last_commit()
    assert repo.subjects() == ["Test the parser", "Add parser", "Initial commit"]
    assert "conftest.py" in repo("ls-tree", "--name-only", "HEAD").splitlines()

    # Debugging that should never have been committed.
    repo.write("debug.log", "lots\n")
    ui.files.commit("Debug logging")
    ui.commits.drop("Debug logging")
    assert repo.subjects() == ["Test the parser", "Add parser", "Initial commit"]
    assert not (repo.path / "debug.log").exists()

    # Two work-in-progress commits become one.
    repo.write("printer.py", "def show(tree):\n")
    ui.files.commit("WIP printer")
    repo.write("printer.py", "def show(tree):\n    print(tree)\n")
    ui.files.commit("WIP printer, more")
    ui.commits.squash("WIP printer, more")
    ui.commits.reword("WIP printer", "Add printer")
    assert repo.subjects() == ["Add printer", "Test the parser", "Add parser", "Initial commit"]
    assert repo("show", "HEAD:printer.py") == "def show(tree):\n    print(tree)"

    # The branch gets the name it should have had.
    ui.branches.rename("feature", "parser")
    assert repo.branches() == ["main", "parser"]
    assert repo.current_branch() == "parser"

    # Back on main, a fix is committed there by mistake, and moved to a branch of its own.
    ui.branches.checkout("main")
    repo.write("README.md", "# The parser\n")
    ui.files.commit("Fix README title")
    repo.write("README.md", "# The parser\n\nAnd a printer.\n")
    ui.files.commit("Mention the printer")
    ui.commits.move_to_new_branch("Fix README title", "readme")
    assert repo.current_branch() == "readme"
    assert repo.subjects() == ["Mention the printer", "Fix README title", "Initial commit"]
    assert repo.subjects("main") == ["Initial commit"]

    # A spike from the parser alone, before the tests, that turns out to go nowhere.
    ui.branches.checkout("parser")
    ui.commits.new_branch("Add parser", "spike")
    assert repo.subjects() == ["Add parser", "Initial commit"]
    repo.write("parser.py", "import re\n")
    ui.files.commit("Try a regex")
    ui.branches.checkout("parser")
    ui.branches.delete("spike")

    # What is left: three branches, each with only what belongs on it.
    assert repo.branches() == ["main", "parser", "readme"]
    assert repo.subjects("main") == ["Initial commit"]
    assert repo.subjects("parser") == ["Add printer", "Test the parser", "Add parser", "Initial commit"]
    assert repo.subjects("readme") == ["Mention the printer", "Fix README title", "Initial commit"]
    assert repo("ls-tree", "-r", "--name-only", "parser").splitlines() == [
        "README.md", "conftest.py", "parser.py", "printer.py", "test_parser.py"]
    assert repo("status", "--porcelain") == ""


def test_a_feature_worked_on_with_a_teammate_through_origin(repo, origin, clone, jazygit):
    repo.write("README.md", "# Parser\n")
    ui = jazygit()

    # Start the history and publish it, then branch off for the feature and publish that too.
    ui.files.commit("Initial commit")
    ui.branches.push("main")
    ui.branches.new("feature")
    repo.write("parser.py", "def parse(text):\n    retrun text\n")
    ui.files.commit("Add parser")
    ui.branches.push("feature")
    assert origin.subjects("feature") == ["Add parser", "Initial commit"]
    assert repo.upstream("feature") == "origin/feature"

    # A teammate adds tests to it; once fetched, as the app does every minute, they are pulled in.
    teammate = clone()
    teammate("switch", "feature")
    teammate.commit("Add tests", **{"test_parser.py": "assert parse('a') == 'a'\n"})
    teammate("push")
    repo("fetch")
    ui.branches.pull("feature")
    assert repo.subjects() == ["Add tests", "Add parser", "Initial commit"]

    # The typo is fixed in the commit that made it, which rewrites what was pushed: it takes a force push.
    repo.write("parser.py", "def parse(text):\n    return text\n")
    ui.files.commit("Fix typo")
    ui.commits.fixup("Fix typo")
    ui.commits.reword("Add parser", "Add the parser")
    assert repo.sync("feature") == "ahead 2, behind 2"
    ui.branches.force_push("feature")
    assert origin.subjects("feature") == ["Add tests", "Add the parser", "Initial commit"]
    assert origin("show", "feature:parser.py") == "def parse(text):\n    return text"

    # The teammate starts on the docs, and asks for a hand with them.
    teammate("switch", "main")
    teammate("switch", "--create", "docs")
    teammate.commit("Document the parser", **{"README.md": "# Parser\n\nParses text.\n"})
    teammate("push", "--set-upstream", "origin", "docs")
    repo("fetch")
    ui.branches.checkout_remote("origin/docs")
    assert repo.current_branch() == "docs"
    repo.write("README.md", "# Parser\n\nParses text, and returns it.\n")
    ui.files.commit("Say what it returns")
    ui.branches.push("docs")
    assert origin.subjects("docs") == ["Say what it returns", "Document the parser", "Initial commit"]

    # Main moves on meanwhile; it is brought up to date without leaving the docs.
    teammate("switch", "main")
    teammate.commit("Add a licence", **{"LICENSE": "MIT\n"})
    teammate("push")
    repo("fetch")
    ui.branches.pull("main")
    assert repo.subjects("main") == ["Add a licence", "Initial commit"]
    assert repo.current_branch() == "docs"

    # The feature is merged elsewhere, and its branch is deleted here and on origin.
    ui.branches.delete("feature", where="both")

    assert repo.branches() == ["docs", "main"]
    assert origin.branches() == ["docs", "main"]
    assert [repo.sync(branch) for branch in repo.branches()] == ["", ""]
    assert repo("status", "--porcelain") == ""
