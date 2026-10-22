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
import subprocess

from harness import App, AppExited, Git

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
        failures = []
        try:
            # The app's own wait: when it dies handling the keys, this fails at once, with what it last wrote.
            self.app.eventually(lambda: not self._busy() and self._changed_from(before, failures), description)
        except AppExited:
            raise
        except AssertionError as e:
            if not failures:
                raise
            # A read that kept failing is likely why it never settled, so the timeout says what git said.
            failed = failures[-1]
            raise AssertionError(
                f"{e}\n--- last git error ---\n{' '.join(failed.cmd)}: {failed.stderr.strip()}") from None
        self.caught_up()

    def _changed_from(self, before, failures: list) -> bool:
        """Whether the repository changed since it was before. The app can still be changing it: until it shows
        that it is busy, the keys are only just pressed. A read of git that fails meanwhile, as git stash list does
        when the stash it lists is popped under it ("bad revision 'refs/stash'"), means it has not settled yet; it
        goes in failures, for the timeout to tell."""
        try:
            return self._state() != before
        except subprocess.CalledProcessError as e:
            failures.append(e)
            return False

    def _state(self):
        # The remote branches too, which a push moves, and the upstreams, which a push can set; and the stashes,
        # which dropping one changes and nothing else.
        return (self.repo("for-each-ref", "--format=%(refname) %(objectname) %(upstream)", "refs/heads",
                          "refs/remotes"),
                self.repo.current_branch(),
                self.repo.raw("status", "--porcelain", "-z"),
                self.repo("stash", "list", "--format=%H"))

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
    """The changed files: staging, discarding, stashing and committing them. A file or directory is given by its
    path; ALL is the row above them all, that holds every change."""

    ALL = "/"

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def shows_changes(self) -> bool:
        rows = self.app.pane_text("Files").splitlines()
        status = self.repo.status()
        return (all(self._shows(rows, path, code) for path, code in status.items())
                and bool(status or not "".join(rows).strip()))

    @staticmethod
    def _shows(rows: list, path: str, code: str) -> bool:
        """Whether a row shows the file at path, staged and unstaged as git has it: the two columns before its
        name are blank or not where git's are."""
        name = path.split("/")[-1]
        columns = [row[:-len(name) - 1][-2:] for row in rows if row.endswith(" " + name)]
        # A rename shows the path it came from between the columns and its name.
        return any(shown == "->" or (shown[:1] != " ", shown[1:] != " ") == (code[0] != " ", code[1] != " ")
                   for shown in columns)

    def focus(self):
        self.ui.caught_up()
        self.app.focus("Files")

    def select(self, path: str):
        """Focuses the pane and moves to the file or directory at path, or to ALL."""
        self.focus()
        self.app.move_cursor("Files", path.split("/")[-1] if path != self.ALL else path)

    def stage(self, path: str):
        """Stages the changes to the file or directory at path, or to ALL files, when some are not staged."""
        self.select(path)
        self.ui.changing_repo(f"{path} to be staged", lambda: self.app.press("space"))

    def unstage(self, path: str):
        """Unstages the file or directory at path, or ALL files, when all its changes are staged."""
        self.select(path)
        self.ui.changing_repo(f"{path} to be unstaged", lambda: self.app.press("space"))

    def discard(self, path: str, only_unstaged: bool = False):
        """Throws away the changes to the file or directory at path, or to ALL files: all of them, or only the
        unstaged ones."""
        self.select(path)
        self.ui.changing_repo(f"the changes to {path} discarded", lambda: (
            self.app.press("d"),
            self.ui.confirm("Discard all changes", key="u" if only_unstaged else "x")))

    def stash(self, name: str = None):
        """Stashes all changes, staged, unstaged and untracked, under name, or the name git gives them."""
        self.focus()

        def keys():
            self.app.press("s")
            self.app.wait_for("name (optional)")
            if name is not None:
                self.app.type(name)
            self.app.press("enter")

        self.ui.changing_repo("the changes to be stashed", keys)

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
    """The local branches, and on the tab next to them the remote ones: checking out, pushing and pulling them."""

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def tab(self) -> str:
        """The list the pane shows: "Branches" or "Remotes", or "Worktrees"."""
        return self.app.underlined_in_title("Branches")

    def shows_branches(self) -> bool:
        tab = self.tab()
        if tab == "Remotes":
            shown = self.app.pane_text("Branches").split()
            return all(branch in shown for branch in self.repo.remote_branches())
        if tab != "Branches":
            return False
        rows = self.app.pane_text("Branches").splitlines()
        current = self.repo.current_branch()
        return (all(self._shows_sync(rows, branch) for branch in self.repo.branches())
                and (not current or not self.repo.has_head() or f"* {current}" in self.app.pane_text("Branches")))

    def _shows_sync(self, rows: list, branch: str) -> bool:
        """Whether a row shows branch, with the commits it has to push and to pull, and whether its upstream is
        gone, as git has them."""
        row = next((row.split() for row in rows if branch in row.split()), None)
        expected = [{"ahead": "↑", "behind": "↓"}[side] + count if count else side
                    for part in self.repo.sync(branch).split(", ") if part
                    for side, _, count in [part.partition(" ")]]
        return row is not None and [word for word in row if word[0] in "↑↓" or word == "gone"] == expected

    def show_tab(self, tab: str):
        """Switches the focused pane to the list tab: "Branches" or "Remotes"."""
        for _ in range(3):
            shown = self.app.eventually(self.tab, "the list the branches pane shows")
            if shown == tab:
                return
            self.app.press("]")
            self.app.eventually(lambda: self.tab() != shown, f"the branches pane to show another list than {shown}")
        raise AssertionError(f"no list {tab} in the branches pane\n--- screen ---\n{self.app.text()}")

    def select(self, branch: str):
        """Focuses the pane and moves to the local branch."""
        self.ui.caught_up()
        self.app.focus("Branches")
        self.show_tab("Branches")
        self.ui.caught_up()
        self.app.move_cursor("Branches", branch)
        # Its log shows once the app has taken it as the selected branch, which the keys then act on.
        self.wait_for_log(branch)

    def select_remote(self, branch: str):
        """Focuses the pane and moves to the remote branch, such as "origin/main", on the list of remote ones."""
        self.ui.caught_up()
        self.app.focus("Branches")
        self.show_tab("Remotes")
        self.ui.caught_up()
        self.app.move_cursor("Branches", branch)
        self.wait_for_log(branch)

    def wait_for_log(self, branch: str):
        """Waits until the log of {branch} shows. Right after the name comes the border, or how many of its commits
        are not on its base, so that "main" is not taken for "main-old"."""
        self.app.eventually(
            lambda: f"Log: {branch}─" in self.app.text() or f"Log: {branch} ·" in self.app.text(),
            f"the log of {branch!r} on screen")

    def checkout(self, branch: str):
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be checked out", lambda: self.app.press("space"))

    def checkout_bringing_changes(self, branch: str):
        """Checks out the branch when it would overwrite the uncommitted changes, which the app stashes and pops
        on it, as it offers to."""
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be checked out", lambda: (
            self.app.press("space"), self.ui.confirm(f"Checkout {branch}")))

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

    def delete(self, branch: str, where: str = "local"):
        """Deletes the branch, merged or not: the "local" one, the one it tracks on the "remote", or "both"."""
        key = {"local": "d", "remote": "r", "both": "b"}[where]
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be deleted", lambda: (
            self.app.press("d"), self.ui.confirm(f"Delete branch {branch}", key=key)))

    def push(self, branch: str, upstream: str = None):
        """Pushes the branch. One without an upstream is pushed to upstream, "<remote> <branch>", which it then
        tracks; to the branch of the same name on origin, as the app offers, when not given."""
        self.select(branch)
        has_upstream = self.repo.upstream(branch) != ""

        def keys():
            self.app.press("P")
            if has_upstream:
                return
            if upstream is None:
                self.ui.confirm("Enter upstream as")
            else:
                self.ui.enter_text("Enter upstream as", upstream)

        self.ui.changing_repo(f"{branch} to be pushed", keys)

    def force_push(self, branch: str):
        """Pushes the branch that diverged from its upstream, overwriting it, as the app asks to."""
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be force pushed", lambda: (
            self.app.press("P"), self.ui.confirm(f"Force push {branch}")))

    def pull(self, branch: str):
        """Pulls the checked out branch from its upstream, or fast-forwards another one to its upstream."""
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be pulled", lambda: self.app.press("p"))

    def try_to_pull(self, branch: str):
        """Asks to pull the branch, for when the app is to refuse it: it does not wait for the repository to
        change."""
        self.select(branch)
        self.app.press("p")

    def reset_onto_upstream(self, mode: str = "hard"):
        """Resets the checked out branch onto its upstream, keeping the changes since "soft" (staged), "mixed"
        (unstaged) or not at all ("hard")."""
        self.select(self.repo.current_branch())
        key = {"soft": "s", "mixed": "m", "hard": "h"}[mode]
        self.ui.changing_repo(f"{self.repo.current_branch()} reset onto its upstream", lambda: (
            self.app.press("u"),
            self.ui.confirm("Upstream options for", key="g"),
            self.ui.confirm("Reset to", key=key)))

    def checkout_remote(self, branch: str, name: str = None):
        """Checks out the local branch that follows the remote branch, such as "origin/feature", creating it when
        there is none. When the name it has on the remote is taken, the new branch is called name."""
        self.select_remote(branch)

        def keys():
            self.app.press("space")
            if name is not None:
                self.ui.enter_text("New branch from", name)

        self.ui.changing_repo(f"{branch} to be checked out", keys)

    def delete_remote(self, branch: str):
        """Deletes the remote branch, such as "origin/feature", on its remote."""
        self.select_remote(branch)
        self.ui.changing_repo(f"{branch} to be deleted", lambda: (
            self.app.press("d"), self.ui.confirm(f"Delete {branch}")))

    def try_to_delete_remote(self, branch: str):
        """Asks to delete the remote branch, for when the app is to refuse it."""
        self.select_remote(branch)
        self.app.press("d")


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

    def reset(self, subject: str, mode: str = "mixed"):
        """Resets the checked out branch to the commit with this subject, keeping the changes since "soft"
        (staged), "mixed" (unstaged) or not at all ("hard"), which the app has confirmed when it discards
        changes."""
        key = {"soft": "s", "mixed": "m", "hard": "h"}[mode]
        self.select(subject)
        discards = mode == "hard" and any(code != "??" for code in self.repo.status().values())

        def keys():
            self.app.press("g")
            self.ui.confirm("Reset to", key=key)
            if discards:
                self.ui.confirm("Hard reset to")

        self.ui.changing_repo(f"the branch reset to {subject!r}", keys)

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

    def pop(self, message: str):
        """Brings back the changes of the stash with this message, staged and unstaged as they were where they
        still apply, and removes it."""
        self.select(message)
        self.ui.changing_repo(f"the stash {message!r} popped", lambda: self.app.press("g"))

    def try_to_pop(self, message: str):
        """Asks to pop the stash, for when git is to refuse it: it does not wait for the repository to change."""
        self.select(message)
        self.app.press("g")

    def drop(self, message: str):
        """Deletes the stash with this message without applying it."""
        self.select(message)
        self.ui.changing_repo(f"the stash {message!r} dropped", lambda: (
            self.app.press("d"), self.ui.confirm("Stash drop")))
