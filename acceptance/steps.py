"""
The moves a person makes in jazygit that take more than one key: getting to a commit or a branch, and the common
operations on them. They know the screen only as far as a person reads it: the pane with the focus, and the title
of the pane on the right, which names what is selected.
"""

from harness import App, Git


def select_commit(app: App, repo: Git, subject: str):
    """Focuses the commits pane and moves to the commit of the checked out branch with this subject."""
    title = f"Commit: {repo.short(repo.find(subject))}"
    app.focus("Commits")
    count = len(repo.subjects())
    if title not in app.text():
        app.press("down")
    try:
        app.press_until("down", title, limit=count)
    except AssertionError:
        app.press_until("up", title, limit=count)


def select_branch(app: App, repo: Git, branch: str):
    """Focuses the branches pane and moves to the local branch with this name."""
    # The border right after the name, so that "main" is not taken for "main-old".
    title = f"Log: {branch}─"
    app.focus("Branches")
    count = len(repo.branches())
    try:
        app.press_until("down", title, limit=count)
    except AssertionError:
        app.press_until("up", title, limit=count)


def type_over(app: App, text: str):
    """Replaces the text of the field the cursor is in."""
    app.press("ctrl+e", "ctrl+u")
    app.type(text)


def stage_and_commit(app: App, repo: Git, summary: str, **files: str):
    """Writes files, stages all changes and commits them from the files pane."""
    for name, content in files.items():
        repo.write(name, content)
    before = len(repo.subjects()) if repo.has_head() else 0
    app.focus("Files")
    for name in files:
        app.wait_in("Files", name)
    app.press("c")
    # With nothing staged, it offers to stage all files first.
    app.wait_for("Stage all files?")
    app.press("enter")
    app.wait_for("Commit summary")
    app.type(summary)
    app.press("enter")
    app.eventually(lambda: repo.has_head() and len(repo.subjects()) == before + 1, f"commit {summary!r}")
    app.wait_in("Commits", summary)
