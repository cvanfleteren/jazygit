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
from pathlib import Path

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
        self.worktrees = WorktreesPane(self)

    def in_worktree(self, path: Path) -> "Jazygit":
        """The app as it shows the worktree at path, once it switched to it."""
        return Jazygit(self.app, Git(path, self.repo.env))

    # How the status line names each operation in progress, by what Git.in_progress calls it.
    IN_PROGRESS = {"merge": "MERGING", "rebase": "REBASING", "cherry-pick": "CHERRY-PICKING", "revert": "REVERTING"}

    def caught_up(self):
        """Waits until no git operation runs and the panes show the repository as git has it: the operation it is in
        the middle of, the commits of the checked out branch, the local branches with the checked out one marked,
        and the changed files."""
        self.app.eventually(lambda: not self._busy() and self._shows_repo(), "the app to show the repository")

    def _busy(self) -> bool:
        """Whether git is running: it holds the index lock, or a rebase goes on. One that stopped, on conflicts, waits
        for the user, with the commit it stopped at in REBASE_HEAD; git takes that away once it goes on."""
        git_dir = self.repo.git_dir
        rebasing = any((git_dir / name).exists() for name in ("rebase-merge", "rebase-apply"))
        return (git_dir / "index.lock").exists() or (rebasing and not (git_dir / "REBASE_HEAD").exists())

    def _shows_repo(self) -> bool:
        return (self._shows_in_progress() and self.commits.shows_history() and self.branches.shows_branches()
                and self.files.shows_changes())

    def _shows_in_progress(self) -> bool:
        """Whether the status line names the operation in progress, and no other. Its conflicts can be cut off, past
        the width of the pane: the files pane shows them."""
        shown = [word for word in self.IN_PROGRESS.values() if word in self.app.pane_text("Repo").split()]
        in_progress = self.repo.in_progress()
        return shown == ([self.IN_PROGRESS[in_progress]] if in_progress else [])

    def continue_in_progress(self):
        """Continues the merge, rebase, cherry-pick or revert that stopped, once its conflicts are resolved; a rebase
        can stop again, on the conflicts of a later commit."""
        self.changing_repo("the operation in progress continued", lambda: (
            self.app.press("M"), self.confirm("What to do with the", key="c")), self._done_or_stopped)

    def abort_in_progress(self):
        """Aborts the operation in progress, which the app asks to confirm."""
        kind = self.repo.in_progress()
        self.changing_repo(f"the {kind} aborted", lambda: (
            self.app.press("M"), self.confirm("What to do with the", key="a"), self.confirm(f"Abort the {kind}")),
                           lambda: self.repo.in_progress() is None)

    def skip_in_progress(self):
        """Skips the commit the rebase, cherry-pick or revert stopped at, which the app asks to confirm."""
        kind = self.repo.in_progress()
        self.changing_repo(f"a commit of the {kind} skipped", lambda: (
            self.app.press("M"), self.confirm("What to do with the", key="s"),
            self.confirm(f"Skip a commit of the {kind}")), self._done_or_stopped)

    def try_to_continue(self):
        """Asks to continue the operation in progress, for when the app is to refuse it: it does not wait for the
        repository to change."""
        self.caught_up()
        self.app.press("M")
        self.confirm("What to do with the", key="c")

    def try_to_skip(self):
        """Asks to skip a commit of the operation in progress, for when the app is to refuse it: the dialog stays
        open."""
        self.caught_up()
        self.app.press("M")
        self.confirm("What to do with the", key="s")

    def _done_or_stopped(self) -> bool:
        """Whether the operation in progress is done, or stopped again on conflicts."""
        return self.repo.in_progress() is None or bool(self.repo.conflicts())

    def changing_repo(self, description: str, keys, done=None):
        """Runs keys, a function that presses them, and waits until they changed the repository, and the app
        shows how. An operation that runs several git commands changes it after each: done, a function that tells
        whether the repository is as the last of them leaves it, keeps the wait going until then."""
        self.caught_up()
        before = self._state()
        keys()
        failures = []
        try:
            # The app's own wait: when it dies handling the keys, this fails at once, with what it last wrote.
            self.app.eventually(lambda: not self._busy() and self._changed_from(before, failures)
                                and (done is None or done()), description)
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
        # The remote branches too, which a push moves, and the upstreams, which a push can set; the stashes, which
        # dropping one changes and nothing else; the worktrees, which adding, removing, locking or pruning one
        # changes, the branches staying as they were; and the operation in progress with the commit it stopped at,
        # which skipping one that stops again on the same file changes, HEAD and the files staying as they were.
        return (self.repo("for-each-ref", "--format=%(refname) %(objectname) %(upstream)", "refs/heads",
                          "refs/remotes"),
                self.repo.current_branch(),
                self.repo.raw("status", "--porcelain", "-z"),
                self.repo("stash", "list", "--format=%H"),
                self.repo.worktree_list(),
                self.repo.in_progress(),
                self.repo.stopped_at())

    def switching(self, path: Path, keys) -> "Jazygit":
        """Runs keys, a function that presses them, and waits until the app shows the worktree at path instead of
        this one; returns the app as it shows that one, for the operations that follow."""
        self.caught_up()
        keys()
        shown = self.in_worktree(path)
        shown.caught_up()
        return shown

    def adding_worktree(self, description: str, ask, location: Path = None, switch: bool = False) -> "Jazygit":
        """Runs ask, a function that presses the keys up to the question where the new worktree goes, and answers
        it: location, or where the app offers to put it when not given. Once it is added, the app offers to switch
        to it, which it does when switch. Returns the app as it shows the worktree it shows then."""
        before = set(self.repo.worktrees())

        def keys():
            ask()
            if location is None:
                self.confirm("New worktree for")
            else:
                self.enter_text("New worktree for", str(location))
            self.confirm(" added", key="enter" if switch else "escape")

        if not switch:
            self.changing_repo(description, keys)
            return self
        self.caught_up()
        keys()
        added = self.app.eventually(lambda: set(self.repo.worktrees()) - before, description)
        shown = self.in_worktree(added.pop())
        shown.caught_up()
        return shown

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

    def edit(self, path: str):
        """Opens the file at path in git's editor, which the app hands the terminal over to until it is closed."""
        self.select(path)
        self.app.press("e")

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
        commits = self._commits()

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

        # Staging first changes the repository already, before the commit even starts: it is done once it made one.
        self.ui.changing_repo(f"the commit {summary!r}", keys, lambda: self._commits() == commits + 1)

    def _commits(self) -> int:
        """How many commits HEAD has: none before the first."""
        return int(self.repo("rev-list", "--count", "HEAD")) if self.repo.has_head() else 0

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
        if tab == "Worktrees":
            return self.ui.worktrees.shows_worktrees()
        if tab != "Branches":
            return False
        rows = self.app.pane_text("Branches").splitlines()
        current = self.repo.current_branch()
        return (all(self._shows_sync(rows, branch) for branch in self.repo.branches())
                and (not current or not self.repo.has_head() or current in self.app.bold_in("Branches")))

    def _shows_sync(self, rows: list, branch: str) -> bool:
        """Whether a row shows branch, with the commits it has to push and to pull, and whether its upstream is
        gone, as git has them."""
        row = self._words(rows, branch)
        expected = [{"ahead": "↑", "behind": "↓"}[side] + count if count else side
                    for part in self.repo.sync(branch).split(", ") if part
                    for side, _, count in [part.partition(" ")]]
        return row is not None and [word for word in row if word[0] in "↑↓" or word == "gone"] == expected

    @staticmethod
    def _words(rows: list, branch: str):
        """The words of the row of branch: its name on the first line, what is known of it on the second; None when
        no row shows it."""
        at = next((index for index, row in enumerate(rows) if branch in row.split()), None)
        return None if at is None else " ".join(rows[at:at + 2]).split()

    def ahead(self, branch: str):
        """How many commits the row of the local branch says it has that its base does not, as `⇡N`; None when it
        says nothing of it."""
        return self._count(branch, "⇡")

    def behind(self, branch: str):
        """How many commits of its base the row of the local branch says it does not have, as `⇣N`; None when it
        says nothing of it."""
        return self._count(branch, "⇣")

    def _count(self, branch: str, arrow: str):
        words = self._words(self.app.pane_text("Branches").splitlines(), branch) or []
        return next((int(word[1:]) for word in words if word.startswith(arrow)), None)

    def shows_gone(self, branch: str) -> bool:
        """Whether the row of the local branch says its upstream is gone."""
        return "gone" in (self._words(self.app.pane_text("Branches").splitlines(), branch) or [])

    def show_tab(self, tab: str):
        """Switches the focused pane to the list tab: "Branches", "Remotes" or "Worktrees"."""
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

    def try_to_checkout(self, branch: str):
        """Asks to check out the branch, for when the app is to ask first, or git to refuse it: it does not wait
        for the repository to change."""
        self.select(branch)
        self.app.press("space")

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

    def new_from_remote(self, remote: str, name: str):
        """Creates the branch name from the remote branch, such as "origin/develop", and checks it out."""
        self.select_remote(remote)
        self.ui.changing_repo(f"the branch {name}", lambda: (
            self.app.press("n"), self.ui.enter_text(f"New branch from {remote}", name)))

    def rename(self, branch: str, name: str):
        self.select(branch)
        self.ui.changing_repo(f"{branch} renamed to {name}", lambda: (
            self.app.press("R"), self.ui.enter_text(f"Rename branch {branch}", name)))

    def try_to_rename(self, branch: str, name: str):
        """Asks to rename the branch to name, for when the app is to refuse it: it does not wait for the repository
        to change."""
        self.select(branch)
        self.app.press("R")
        self.ui.enter_text(f"Rename branch {branch}", name)

    def try_to_delete(self, branch: str):
        """Asks to delete the local branch, for when the app is to ask more first: it does not wait for the
        repository to change."""
        self.select(branch)
        self.app.press("d")
        self.ui.confirm(f"Delete branch {branch}", key="d")

    def delete(self, branch: str, where: str = "local"):
        """Deletes the branch, merged or not: the "local" one, the one it tracks on the "remote", or "both"; or,
        when another worktree has it checked out, the local one together with that "worktree", which is unlocked
        first, as the app asks, when it is locked."""
        key = {"local": "d", "remote": "r", "both": "b", "worktree": "w"}[where]
        self.select(branch)
        locked = where == "worktree" and any(
            self.repo.worktrees()[path] == branch for path in self.repo.locked_worktrees())
        upstream = self.repo.upstream(branch)
        # The app asks to delete anyway when the local branch has commits that no other ref keeps. With "both" it
        # deletes the remote branch first, so that one does not keep them.
        loses_commits = where != "remote" and self.repo.unique_commits(
            branch, without=upstream if where == "both" else "") > 0

        def keys():
            self.app.press("d")
            self.ui.confirm(f"Delete branch {branch}", key=key)
            if locked:
                self.ui.confirm("Unlock worktree")
            if loses_commits:
                self.ui.confirm("anyway?")

        # Deleting both, or with the worktree, takes a git command for each.
        def done():
            return ((where == "remote" or branch not in self.repo.branches())
                    and (where not in ("remote", "both") or upstream not in self.repo.remote_branches())
                    and (where != "worktree" or branch not in self.repo.worktrees().values()))

        self.ui.changing_repo(f"{branch} to be deleted", keys, done)

    def nuke(self, branch: str):
        """Nukes the branch, as the one dialog the app asks confirms: deletes it with force, the worktree that has
        it checked out whatever that has, and its branch of the same name on origin."""
        self.select(branch)
        remote = f"origin/{branch}"
        upstream = self.repo.upstream(branch)
        deletes_remote = upstream == remote or (upstream == "" and remote in self.repo.remote_branches())

        # Each of the three is a git command of its own.
        def done():
            return (branch not in self.repo.branches()
                    and branch not in self.repo.worktrees().values()
                    and (not deletes_remote or remote not in self.repo.remote_branches()))

        self.ui.changing_repo(f"{branch} to be nuked", lambda: (
            self.app.press("D"), self.ui.confirm(f"Nuke branch {branch}")), done)

    def try_to_nuke(self, branch: str):
        """Asks to nuke the branch, for when the app is to refuse it, or to delete only part of it: it does not
        wait for the repository to change."""
        self.select(branch)
        self.app.press("D")

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

    def try_to_force_push(self, branch: str):
        """Asks to force push the branch, for when git is to refuse it: it does not wait for the repository to
        change."""
        self.select(branch)
        self.app.press("P")
        self.ui.confirm(f"Force push {branch}")

    def try_to_push(self, branch: str):
        """Asks to push the branch, for when the app is to refuse it: it does not wait for the repository to
        change."""
        self.select(branch)
        self.app.press("P")

    def push_refused_by_push_default(self, branch: str, upstream: str = None):
        """Pushes the branch whose upstream has another name, which push.default=simple pushes nowhere: the app
        asks where to push it instead, which it then tracks; to the branch of the same name, as it offers, when
        upstream, "<remote> <branch>", is not given."""
        self.select(branch)

        def keys():
            self.app.press("P")
            if upstream is None:
                self.ui.confirm("push.default=")
            else:
                self.ui.enter_text("push.default=", upstream)

        self.ui.changing_repo(f"{branch} to be pushed", keys)

    def pull(self, branch: str):
        """Pulls the checked out branch from its upstream, or fast-forwards another one to its upstream."""
        self.select(branch)
        self.ui.changing_repo(f"{branch} to be pulled", lambda: self.app.press("p"))

    def try_to_pull(self, branch: str):
        """Asks to pull the branch, for when the app is to refuse it: it does not wait for the repository to
        change."""
        self.select(branch)
        self.app.press("p")

    def merge(self, branch: str, mode: str = "fast-forward"):
        """Merges the branch into the checked out one: a regular merge that fast-forwards when it can
        ("fast-forward"), or makes a "merge commit" anyway, or a squash merge, left uncommitted ("squash") or
        committed ("squash and commit")."""
        into = self.repo.current_branch()
        head = self.repo("rev-parse", "HEAD")
        self.select(branch)

        # A squash merge that is committed takes a git command for the commit, after the one that staged the
        # changes; one that stops on conflicts commits nothing.
        def done():
            return mode != "squash and commit" or self.repo("rev-parse", "HEAD") != head or bool(self.repo.conflicts())

        self.ui.changing_repo(f"{branch} merged into {into}", lambda: self._ask_merge(branch, into, mode), done)

    def try_to_merge(self, branch: str, mode: str = "fast-forward"):
        """Asks to merge the branch into the checked out one as merge does, for when nothing is to change: it does
        not wait for the repository to change."""
        into = self.repo.current_branch()
        self.select(branch)
        self._ask_merge(branch, into, mode)

    def _ask_merge(self, branch: str, into: str, mode: str):
        key = {"fast-forward": "m", "merge commit": "n", "squash": "s", "squash and commit": "S"}[mode]
        self.app.press("m")
        self.ui.confirm(f"Merge '{branch}' into '{into}'", key=key)

    def rebase_onto(self, branch: str):
        """Rebases the checked out branch onto the branch."""
        checked_out = self.repo.current_branch()
        self.select(branch)
        self.ui.changing_repo(f"{checked_out} rebased onto {branch}", lambda: (
            self.app.press("r"), self.ui.confirm(f"Rebase '{checked_out}'", key="s")))

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

    def new_worktree(self, branch: str, name: str = None, location: Path = None, switch: bool = False) -> Jazygit:
        """Checks the branch, one not checked out anywhere, out in a new worktree at location, or where the app
        offers to put it; switches to it when switch. Returns the app as it shows the worktree it shows then. For
        the checked out branch, which git checks out in one worktree only, the app asks for a new branch made from
        it instead, called name."""
        self.select(branch)

        def ask():
            self.app.press("w")
            if name is not None:
                self.ui.enter_text("in a new worktree", name)

        return self.ui.adding_worktree(f"a worktree for {branch}", ask, location, switch)

    def new_branch_worktree(self, start: str, name: str, location: Path = None, switch: bool = False) -> Jazygit:
        """Creates the branch name from the branch start, and checks it out in a new worktree, as new_worktree
        does."""
        self.select(start)
        return self.ui.adding_worktree(f"a worktree for the new branch {name}", lambda: (
            self.app.press("W"), self.ui.enter_text("in a new worktree", name)), location, switch)

    def new_branch_worktree_from_remote(self, remote: str, name: str = None, location: Path = None,
                                        switch: bool = False) -> Jazygit:
        """Creates a branch that follows the remote branch, such as "origin/feature", and checks it out in a new
        worktree, as new_worktree does. It is called name, or as on the remote when not given."""
        self.select_remote(remote)

        def ask():
            self.app.press("W")
            if name is None:
                self.ui.confirm("in a new worktree")
            else:
                self.ui.enter_text("in a new worktree", name)

        return self.ui.adding_worktree(f"a worktree following {remote}", ask, location, switch)

    def checkout_in_its_worktree(self, branch: str) -> Jazygit:
        """Checks out the branch that another worktree has checked out, which switches to that worktree, as the
        app offers to. Returns the app as it shows that worktree."""
        self.select(branch)
        path = next(path for path, checked_out in self.repo.worktrees().items() if checked_out == branch)
        return self.ui.switching(path, lambda: (self.app.press("space"), self.ui.confirm(f"Checkout {branch}")))


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
        # The pane may be too short for all of them, but shows some as soon as there are any. In any order: the commits
        # all have the same date, and the app draws the parents of a merge in an order of its own.
        return sorted(shown) == sorted(history[:len(shown)]) and bool(shown) == bool(history)

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

    def revert(self, subject: str):
        """Adds a commit that undoes the commit with this subject."""
        self.select(subject)
        self.ui.changing_repo(f"{subject!r} reverted", lambda: (
            self.app.press("t"), self.ui.confirm("Revert commit")))

    def copy(self, subject: str):
        """Copies the commit with this subject, to cherry-pick it onto another branch."""
        self.select(subject)
        self.app.press("C")
        self.app.wait_for(f"Copied {self.repo.short(self.repo.find(subject))} to cherry-pick")

    def try_to_copy(self, subject: str):
        """Asks to copy the commit with this subject, for when the app is to refuse it: it does not wait for the
        copy to show."""
        self.select(subject)
        self.app.press("C")

    def let_go_of_copy(self):
        """Lets go of the commit copied to cherry-pick."""
        self.ui.caught_up()
        self.app.press("escape")
        self.app.wait_gone("to cherry-pick")

    def cherry_pick(self):
        """Cherry-picks the copied commit onto the checked out branch."""
        self.ui.caught_up()
        self.app.focus("Commits")
        self.ui.changing_repo("the copied commit cherry-picked", lambda: (
            self.app.press("V"), self.ui.confirm("Cherry-pick commit")))

    def try_to_cherry_pick(self):
        """Asks to cherry-pick the copied commit, for when git is to refuse it: it does not wait for the repository
        to change."""
        self.ui.caught_up()
        self.app.focus("Commits")
        self.app.press("V")
        self.ui.confirm("Cherry-pick commit")

    def new_branch(self, subject: str, name: str):
        """Creates the branch name at the commit with this subject, and checks it out."""
        self.select(subject)
        self.ui.changing_repo(f"the branch {name}", lambda: (
            self.app.press("n"),
            self.ui.enter_text(f"New branch from {self.repo.short(self.repo.find(subject))}", name)))

    def move_to_new_branch(self, subject: str, name: str, start: str = None):
        """Moves the commit with this subject and the ones after it to the new branch name, and checks it out. The
        new branch starts where the commits are, or, copying them, at the branch start, as the app offers when there
        is one to offer."""
        self.select(subject)
        self.ui.changing_repo(f"the commits moved to {name}", lambda: self._move_to_new_branch(name, start))

    def try_to_move_to_new_branch(self, subject: str, name: str, start: str = None):
        """Asks to move the commits to a new branch as move_to_new_branch does, for when git is to fail: it does not
        wait for the repository to change."""
        self.select(subject)
        self._move_to_new_branch(name, start)

    def _move_to_new_branch(self, name: str, start: str):
        self.app.press("N")
        self.app.wait_for("New branch…")
        self.app.press("enter")
        self.ui.enter_text("to a new branch", name)
        if start is not None:
            self.app.wait_for(f"Start {name} from")
            self._pick(start, "where the commits are now")

    def move_to_branch(self, subject: str, branch: str):
        """Moves the commit with this subject and the ones after it onto branch, and checks it out."""
        self.select(subject)
        self.ui.changing_repo(f"the commits moved onto {branch}", lambda: self._move_to_branch(branch))

    def try_to_move_to_branch(self, subject: str, branch: str):
        """Asks to move the commits onto branch as move_to_branch does, for when git is to fail: it does not wait
        for the repository to change."""
        self.select(subject)
        self._move_to_branch(branch)

    def _move_to_branch(self, branch: str):
        self.app.press("N")
        self.app.wait_for("New branch…")
        self._pick(branch, "New branch…")

    def _pick(self, option: str, gone: str):
        """Picks the option of the list dialog that shows, by filtering it down to that one: once gone, text of
        another option, leaves the screen."""
        self.app.press("/")
        self.app.type(option)
        self.app.wait_gone(gone)
        self.app.press("enter")


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


class WorktreesPane:
    """The worktrees, on the tab of the branches pane after the remote branches: adding, switching to, removing,
    locking and pruning them. A worktree is given by the name of its directory, as the pane shows it."""

    def __init__(self, ui: Jazygit):
        self.ui = ui
        self.app = ui.app
        self.repo = ui.repo

    def shows_worktrees(self) -> bool:
        """Whether the pane shows each worktree git has and no other, the one shown in bold, and which are locked
        and whose directory is gone."""
        rows = [row for row in self.app.pane_text("Branches").splitlines() if row.strip()]
        worktrees = self.repo.worktree_list()
        return len(rows) == len(worktrees) and all(self._shows(rows, worktree) for worktree in worktrees)

    def _shows(self, rows: list, worktree: dict) -> bool:
        path = Path(worktree["worktree"])
        row = next((row for row in rows if row.startswith(path.name + " ")), None)
        return (row is not None
                and (path.name in self.app.bold_in("Branches")) == (path == self.repo.path)
                and (" locked" in row) == ("locked" in worktree)
                and (" missing" in row) == ("prunable" in worktree))

    def path(self, name: str) -> Path:
        """The path of the worktree whose directory is called name."""
        return next(path for path in self.repo.worktrees() if path.name == name)

    def show(self):
        """Focuses the branches pane, on the worktrees."""
        self.ui.caught_up()
        self.app.focus("Branches")
        self.ui.branches.show_tab("Worktrees")
        self.ui.caught_up()

    def select(self, name: str):
        """Focuses the pane and moves to the worktree whose directory is called name: the keys act on the row the
        cursor is drawn on."""
        self.show()
        self.app.move_cursor("Branches", name)

    def new(self, start: str, branch: str = "", location: Path = None, switch: bool = False) -> Jazygit:
        """Adds a worktree starting at start, a branch, remote branch or commit, on the new branch called branch;
        left blank, on the local branch start itself when it can be checked out there, or else on no branch. It
        goes at location, or where the app offers to put it, and is switched to when switch. Returns the app as it
        shows the worktree it shows then."""
        self.show()
        return self.ui.adding_worktree(f"a worktree from {start}", lambda: (
            self.app.press("n"),
            self.ui.enter_text("New worktree from", start),
            self.ui.enter_text("in a new worktree", branch)), location, switch)

    def switch_to(self, name: str) -> Jazygit:
        """Switches to the worktree; returns the app as it shows that one."""
        self.select(name)
        return self.ui.switching(self.path(name), lambda: self.app.press("space"))

    def remove(self, name: str, branch: str = "keep", anyway: bool = False):
        """Removes the worktree, and its directory, keeping its branch or, with "delete", deleting it too. One
        with changes, which git refuses to remove, is removed anyway, as the app offers to, when anyway."""
        key = {"keep": "r", "delete": "b"}[branch]
        self.select(name)
        path = self.path(name)
        checked_out = self.repo.worktrees()[path]
        loses_commits = branch == "delete" and self.repo.unique_commits(checked_out) > 0

        def keys():
            self.app.press("d")
            self.ui.confirm(f"Remove worktree {name}", key=key)
            if anyway:
                # Titled as the question before, with a message that wraps where the path makes it: by its keys.
                self.ui.confirm("(enter) confirm")
            if loses_commits:
                self.ui.confirm("anyway?")

        # Deleting its branch too is a git command of its own, after the removal.
        def done():
            return (path not in self.repo.worktrees()
                    and (branch == "keep" or checked_out not in self.repo.branches()))

        self.ui.changing_repo(f"the worktree {name} removed", keys, done)

    def try_to_remove(self, name: str):
        """Asks to remove the worktree, for when the app is to refuse it: it does not wait for the repository to
        change."""
        self.select(name)
        self.app.press("d")

    def lock(self, name: str):
        """Locks the worktree, or unlocks it when it is locked."""
        self.select(name)
        self.ui.changing_repo(f"the worktree {name} locked or unlocked", lambda: self.app.press("L"))

    def prune(self):
        """Forgets the worktrees whose directory is gone."""
        self.show()
        self.ui.changing_repo("the missing worktrees pruned", lambda: self.app.press("p"))
