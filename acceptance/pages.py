"""
Page objects for jazygit: what a person does with it, said the way they would say it ("reword Add two"), with
the keys and dialogs that takes kept here, so that a change to them is a change to this file only.

The panes are objects of their own, with the operations they have keys for: ui.files.commit(...),
ui.branches.rename(...), ui.commits.squash(...).

Every operation waits until the app shows the repository as git has it before it presses a key, so that the key
acts on what the test set up; and it returns once the repository changed and the app shows that, so that the next
operation starts from there. The tests assert what the operation should have done, with git.
"""

import re

from harness import App, Git

SHA = re.compile(r"\b[0-9a-f]{7}\b")


class Jazygit:
    """The app running on repo."""

    def __init__(self, app: App, repo: Git):
        self.app = app
        self.repo = repo
        self.files = FilesPane(self)
        self.branches = BranchesPane(self)
        self.commits = CommitsPane(self)
        self.stash = StashPane(self)

    def caught_up(self):
        """Waits until no git operation runs and the panes show the repository as git has it: the commits of the
        checked out branch, the local branches with the checked out one marked, and the changed files."""
        self.app.eventually(lambda: not self._busy() and self._shows_repo(), "the app to show the repository")

    def _busy(self) -> bool:
        git = self.repo.path / ".git"
        return any((git / name).exists() for name in ("rebase-merge", "rebase-apply", "index.lock"))

    def _shows_repo(self) -> bool:
        return self.commits.shows_history() and self.branches.shows_branches() and self.files.shows_changes()

    def changing_repo(self, description: str, keys):
        """Runs keys, a function that presses them, and waits until they changed the repository, and the app
        shows how."""
        self.caught_up()
        before = self._state()
        keys()
        # The app's own wait: when it dies handling the keys, this fails at once, with what it last wrote.
        self.app.eventually(lambda: not self._busy() and self._state() != before, description)
        self.caught_up()

    def _state(self):
        return (self.repo("for-each-ref", "--format=%(refname) %(objectname)", "refs/heads"),
                self.repo.current_branch(),
                self.repo.raw("status", "--porcelain", "-z"))

    def confirm(self, title: str, key="enter"):
        """Waits for the dialog with this title, and answers it with key."""
        self.app.wait_for(title)
        self.app.press(key)

    def enter_text(self, title: str, text: str):
        """Waits for the field with this title, replaces its text with text, and confirms it."""
        self.app.wait_for(title)
        self.app.press("ctrl+e", "ctrl+u")
        self.app.type(text)
        self.app.press("enter")


class FilesPane:
    """The changed files, and committing them."""

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def shows_changes(self) -> bool:
        shown = self.app.pane_text("Files")
        names = [path.split("/")[-1] for path in self.repo.changed_paths()]
        return all(name in shown for name in names) and (names or not shown.strip())

    def focus(self):
        self.ui.caught_up()
        self.app.focus("Files")

    def stage(self, path: str):
        """Stages the changes to the file at path."""
        self.focus()
        self.app.move_cursor("Files", path.split("/")[-1])
        self.ui.changing_repo(f"{path} to be staged", lambda: self.app.press("space"))

    def commit(self, summary: str, description: str = None):
        """Commits the staged changes. With nothing staged, it stages all files first, as the app offers to."""
        self.focus()
        nothing_staged = self.repo("diff", "--cached", "--name-only") == ""

        def keys():
            self.app.press("c")
            if nothing_staged:
                self.ui.confirm("Stage all files?")
            self.app.wait_for("Commit summary")
            self.app.type(summary)
            if description is None:
                self.app.press("enter")
            else:
                self.app.press("tab")
                self.app.type(description)
                self.app.press("ctrl+s")

        self.ui.changing_repo(f"the commit {summary!r}", keys)

    def amend_last_commit(self):
        """Adds the staged changes to the last commit."""
        self.focus()
        self.ui.changing_repo("the last commit amended", lambda: (
            self.app.press("A"), self.ui.confirm("Amend last commit")))


class BranchesPane:
    """The local branches."""

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def shows_branches(self) -> bool:
        shown = self.app.pane_text("Branches").split()
        current = self.repo.current_branch()
        return (all(branch in shown for branch in self.repo.branches())
                and (not current or not self.repo.has_head() or f"* {current}" in self.app.pane_text("Branches")))

    def select(self, branch: str):
        """Focuses the pane and moves to branch."""
        self.ui.caught_up()
        self.app.focus("Branches")
        self.app.move_cursor("Branches", branch)
        # Its log shows once the app has taken it as the selected branch, which the keys then act on. The border
        # right after the name, so that "main" is not taken for "main-old".
        self.app.wait_for(f"Log: {branch}─")

    def checkout(self, branch: str):
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be checked out", lambda: self.app.press("space"))

    def new(self, name: str, start: str = None):
        """Creates the branch name from start, the checked out branch if not given, and checks it out."""
        start = start or self.repo.current_branch()
        self.select(start)
        self.ui.changing_repo(f"the branch {name}", lambda: (
            self.app.press("n"), self.ui.enter_text(f"New branch from {start}", name)))

    def rename(self, branch: str, name: str):
        self.select(branch)
        self.ui.changing_repo(f"{branch} renamed to {name}", lambda: (
            self.app.press("R"), self.ui.enter_text(f"Rename branch {branch}", name)))

    def delete(self, branch: str):
        """Deletes the local branch, merged or not."""
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be deleted", lambda: (
            self.app.press("d"), self.ui.confirm(f"Delete branch {branch}", key="d")))


class CommitsPane:
    """The commits of the checked out branch, and rewriting them."""

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def shows_history(self) -> bool:
        shown = [match.group() for line in self.app.pane_text("Commits").splitlines()
                 if (match := SHA.search(line))]
        history = self.repo("log", "--format=%h", "--abbrev=7").splitlines() if self.repo.has_head() else []
        # The pane may be too short for all of them, but shows some as soon as there are any.
        return shown == history[:len(shown)] and bool(shown) == bool(history)

    def select(self, subject: str):
        """Focuses the pane and moves to the commit with this subject."""
        self.ui.caught_up()
        self.app.focus("Commits")
        short = self.repo.short(self.repo.find(subject))
        self.app.move_cursor("Commits", short)
        # Its diff shows once the app has taken it as the selected commit, which the keys then act on.
        self.app.wait_for(f"Commit: {short}")

    def amend(self, subject: str):
        """Adds the staged changes to the commit with this subject."""
        self.select(subject)
        self.ui.changing_repo(f"{subject!r} amended", lambda: (
            self.app.press("A"), self.ui.confirm("Amend commit")))

    def reword(self, subject: str, summary: str):
        """Replaces the summary of the commit with this subject."""
        self.select(subject)
        self.ui.changing_repo(f"{subject!r} reworded", lambda: (
            self.app.press("r"), self.ui.enter_text("Commit summary", summary)))

    def squash(self, subject: str):
        """Squashes the commit with this subject into the one below, keeping both messages."""
        self.select(subject)
        self.ui.changing_repo(f"{subject!r} squashed", lambda: (
            self.app.press("s"), self.ui.confirm("Squash commit")))

    def fixup(self, subject: str, keep_message: str = "below"):
        """Fixes up the commit with this subject into the one below, keeping the message of the commit "below"
        or of the "selected" one."""
        key = {"below": "f", "selected": "C"}[keep_message]
        self.select(subject)
        self.ui.changing_repo(f"{subject!r} fixed up", lambda: (
            self.app.press("f"), self.ui.confirm("Fixup and use this commit's message", key=key)))

    def drop(self, subject: str):
        self.select(subject)
        self.ui.changing_repo(f"{subject!r} dropped", lambda: (
            self.app.press("d"), self.ui.confirm("Drop commit")))

    def new_branch(self, subject: str, name: str):
        """Creates the branch name at the commit with this subject, and checks it out."""
        self.select(subject)
        self.ui.changing_repo(f"the branch {name}", lambda: (
            self.app.press("n"),
            self.ui.enter_text(f"New branch from {self.repo.short(self.repo.find(subject))}", name)))

    def move_to_new_branch(self, subject: str, name: str):
        """Moves the commit with this subject and the ones after it to the new branch name, and checks it out."""
        self.select(subject)

        def keys():
            self.app.press("N")
            self.app.wait_for("New branch…")
            self.app.press("enter")
            self.ui.enter_text("to a new branch", name)

        self.ui.changing_repo(f"the commits moved to {name}", keys)

    def move_to_branch(self, subject: str, branch: str):
        """Moves the commit with this subject and the ones after it onto branch, and checks it out."""
        self.select(subject)

        def keys():
            self.app.press("N")
            self.app.wait_for("New branch…")
            self.app.press("/")
            self.app.type(branch)
            self.app.wait_gone("New branch…")
            self.app.press("enter")

        self.ui.changing_repo(f"the commits moved onto {branch}", keys)


class StashPane:
    """The stashes, and what each one holds."""

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def select(self, message: str):
        """Focuses the pane and moves to the stash stashed with this message, a single word."""
        self.ui.caught_up()
        self.app.focus("Stash")
        self.app.move_cursor("Stash", message)
        ref = next(line.split(" ")[0] for line in self.repo("stash", "list", "--format=%gd %gs").splitlines()
                   if line.endswith(": " + message))
        # What it stashed shows once the app has taken it as the selected stash.
        self.app.wait_for(f"{ref}: {message}")
