"""
Drives the built jazygit jar in a pseudo terminal, and reads its screen and the repository it works on.

The tests only know the jar, the keys and git: nothing here imports or reads the application's code.
"""

import os
import re
import subprocess
import time
from functools import cached_property
from pathlib import Path

import pexpect
import pyte

COLUMNS = 160
LINES = 45
TIMEOUT = 15.0
# How much of the app's raw output to keep, for when it dies: a stack trace can leave no trace on the screen.
OUTPUT_TAIL = 8192
ESCAPE_SEQUENCE = re.compile(rb"\x1b(\[[0-?]*[ -/]*[@-~]|\][^\x07\x1b]*(\x07|\x1b\\)|[@-Z\\-_])")

# What a terminal sends for the keys that are not plain characters.
KEYS = {
    "enter": "\r",
    "tab": "\t",
    "space": " ",
    "escape": "\x1b",
    "backspace": "\x7f",
    "up": "\x1b[A",
    "down": "\x1b[B",
    "right": "\x1b[C",
    "left": "\x1b[D",
    "shift+tab": "\x1b[Z",
}


def key(name):
    """The bytes for a key name such as "enter", "ctrl+s" or a single character."""
    if name in KEYS:
        return KEYS[name]
    if name.startswith("ctrl+") and len(name) == 6:
        return chr(ord(name[-1].lower()) - ord("a") + 1)
    if len(name) == 1:
        return name
    raise ValueError(f"unknown key: {name}")


def eventually(check, description, timeout=TIMEOUT):
    """Waits until check() returns something truthy, and returns it; fails with description after timeout."""
    deadline = time.monotonic() + timeout
    while True:
        result = check()
        if result:
            return result
        if time.monotonic() > deadline:
            raise AssertionError(f"timed out after {timeout}s waiting for {description}")
        time.sleep(0.05)


class Git:
    """Runs git in one repository, with the isolated environment the app gets too."""

    def __init__(self, path: Path, env: dict):
        self.path = path
        # The tests read the repository while the app changes it. A plain git status takes the index lock to write
        # back what it refreshed, and a commit of the app that starts meanwhile fails on it: like the app's reads,
        # these take no lock they can do without.
        self.env = {**env, "GIT_OPTIONAL_LOCKS": "0"}

    def __call__(self, *args) -> str:
        return self.raw(*args).strip()

    def raw(self, *args) -> str:
        """The output of git as it is, for when leading spaces or NUL separators matter."""
        return subprocess.run(
            ["git", *args], cwd=self.path, env=self.env, check=True, capture_output=True, text=True
        ).stdout

    def changed_paths(self) -> list:
        """The paths with changes, staged, unstaged or untracked, as they are: not quoted, renames by their new
        path."""
        return list(self.status())

    def status(self) -> dict:
        """The changed paths, as changed_paths has them, with their two letter status as git status --short
        shows it: the staged change, then the unstaged one, such as "M " (staged), " M" (unstaged), "MM" (both)
        or "??" (untracked)."""
        entries = self.raw("status", "--porcelain", "-z", "--untracked-files=all").split("\0")

        def codes(rest):
            # A rename or copy is followed by an entry with the path it came from.
            if not rest or not rest[0]:
                return []
            entry = rest[0]
            skip = 2 if entry[0] in "RC" else 1
            return [(entry[3:], entry[:2])] + codes(rest[skip:])

        return dict(codes(entries))

    def has_head(self) -> bool:
        return subprocess.run(["git", "rev-parse", "--verify", "-q", "HEAD"], cwd=self.path, env=self.env,
                              capture_output=True).returncode == 0

    def is_repository(self) -> bool:
        """Whether git reads the folder as a repository of its own. git init makes .git before what goes in it, such
        as HEAD, so for a moment the folder has a .git that git does not read as a repository yet."""
        git_dir = subprocess.run(["git", "rev-parse", "--absolute-git-dir"], cwd=self.path, env=self.env,
                                 capture_output=True, text=True)
        return git_dir.returncode == 0 and Path(git_dir.stdout.strip()) == self.path.resolve() / ".git"

    def write(self, name: str, content: str):
        file = self.path / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)

    def read(self, name: str):
        """The content of the file in the working tree; None when there is no such file."""
        file = self.path / name
        return file.read_text() if file.exists() else None

    def commit(self, message: str, **files: str):
        """Writes files, by name, and commits them with all other changes."""
        for name, content in files.items():
            self.write(name, content)
        self("add", "--all")
        self("commit", "--message", message)

    def subjects(self, rev="HEAD") -> list:
        """The subjects of the commits of rev, the newest first."""
        return self("log", "--format=%s", rev).splitlines()

    def message(self, rev="HEAD") -> str:
        return self("log", "-1", "--format=%B", rev)

    def branches(self) -> list:
        return sorted(self("for-each-ref", "--format=%(refname:short)", "refs/heads").splitlines())

    def current_branch(self) -> str:
        return self("branch", "--show-current")

    def upstream(self, branch: str) -> str:
        """The remote branch that branch tracks, such as "origin/main"; empty when it tracks none."""
        return self("for-each-ref", "--format=%(upstream:short)", f"refs/heads/{branch}")

    def unique_commits(self, branch: str, without: str = "") -> int:
        """How many commits only this local branch has: on no other branch, remote-tracking branch or tag.
        The remote-tracking branch without is left out too, as when it is deleted first."""
        return int(self("rev-list", "--count", branch, "--not", f"--exclude={branch}", "--branches",
                        *([f"--exclude={without}"] if without else []), "--remotes", "--tags"))

    def sync(self, branch: str) -> str:
        """How branch stands to its upstream as this repository last fetched it, as git says it: "ahead 1,
        behind 2", "gone", or empty when it is in sync or tracks nothing."""
        return self("for-each-ref", "--format=%(upstream:track,nobracket)", f"refs/heads/{branch}")

    def remote_branches(self) -> list:
        """The remote branches as this repository last fetched them, such as "origin/main"; not origin/HEAD,
        which only names one of them."""
        return sorted(line for line in self(
            "for-each-ref", "--format=%(if)%(symref)%(then)%(else)%(refname:short)%(end)", "refs/remotes")
                      .splitlines() if line)

    def stashes(self) -> list:
        """The messages of the stashes, the newest first: the name they were stashed under, or what git names
        one stashed without, "WIP on <branch>: <commit>"."""
        return [subject.split(": ", 1)[1] if subject.startswith("On ") else subject
                for subject in self("stash", "list", "--format=%gs").splitlines()]

    @cached_property
    def git_dir(self) -> Path:
        """The git directory of this working tree: its .git, or for a linked worktree the directory git keeps for it
        in the main one's, where a rebase it runs and its index lock are."""
        return Path(self("rev-parse", "--absolute-git-dir"))

    def worktree_list(self) -> list:
        """The worktrees as git worktree list --porcelain has them: for each, its attributes by name, such as
        "worktree" (its path), "branch" (refs/heads/...), "detached", "locked" (the reason, or empty) or
        "prunable"."""
        return [dict(attribute.partition(" ")[::2] for attribute in block.split("\0") if attribute)
                for block in self.raw("worktree", "list", "--porcelain", "-z").split("\0\0") if block.strip("\0")]

    def worktrees(self) -> dict:
        """The worktrees, by their path, with the branch each has checked out; None for one on no branch."""
        return {Path(worktree["worktree"]): worktree["branch"].removeprefix("refs/heads/")
                if "branch" in worktree else None
                for worktree in self.worktree_list()}

    def locked_worktrees(self) -> dict:
        """The locked worktrees, by their path, with the reason they are locked; empty when none was given."""
        return {Path(worktree["worktree"]): worktree["locked"]
                for worktree in self.worktree_list() if "locked" in worktree}

    def in_progress(self):
        """The operation the repository is in the middle of, stopped or still running: "rebase", "merge",
        "cherry-pick" or "revert"; None when there is none. A rebase comes first, as the app has it: one that
        recreates a merge is merging too, and one that stops on a conflicting commit is a rebase all the same."""
        if any((self.git_dir / name).is_dir() for name in ("rebase-merge", "rebase-apply")):
            return "rebase"
        heads = {"MERGE_HEAD": "merge", "CHERRY_PICK_HEAD": "cherry-pick", "REVERT_HEAD": "revert"}
        return next((kind for name, kind in heads.items() if (self.git_dir / name).exists()), None)

    def stopped_at(self) -> str:
        """The commit the rebase, merge, cherry-pick or revert in progress stopped at, as git names it in REBASE_HEAD,
        MERGE_HEAD, CHERRY_PICK_HEAD or REVERT_HEAD; empty when none did."""
        def read(name):
            # Git can take it away while it goes on.
            try:
                return (self.git_dir / name).read_text().strip()
            except FileNotFoundError:
                return ""

        return " ".join(read(name) for name in ("REBASE_HEAD", "MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD"))

    def conflicts(self) -> list:
        """The paths that still have conflicts, until they are staged."""
        return sorted(self("diff", "--name-only", "--diff-filter=U").splitlines())

    def resolve(self, name: str, content: str):
        """Resolves the conflicts in the file name, by writing what it should hold and staging it."""
        self.write(name, content)
        self("add", "--", name)

    def parents(self, rev="HEAD") -> int:
        """How many parents the commit rev has: two for a merge commit."""
        return len(self("rev-list", "--parents", "-n", "1", rev).split()) - 1

    def short(self, rev: str) -> str:
        """The abbreviated hash the app shows for rev."""
        return self("rev-parse", "--short=7", rev)

    def find(self, subject: str, rev="HEAD") -> str:
        """The full hash of the commit of rev with exactly this subject."""
        matches = [line.split(" ", 1)[0] for line in self("log", "--format=%H %s", rev).splitlines()
                   if line.split(" ", 1)[1] == subject]
        assert len(matches) == 1, f"{len(matches)} commits with subject {subject!r} in {rev}"
        return matches[0]


class AppExited(AssertionError):
    """The app ended while a test still expected it to run."""


class App:
    """A running jazygit, with the screen it draws kept up to date by a terminal emulator."""

    def __init__(self, command: list, cwd: Path, env: dict):
        self.screen = pyte.Screen(COLUMNS, LINES)
        self.stream = pyte.ByteStream(self.screen)
        self.output = b""
        self.exited = False
        self.process = pexpect.spawn(
            command[0], command[1:], cwd=str(cwd), env=env, dimensions=(LINES, COLUMNS)
        )

    def pump(self):
        """Feeds whatever the app wrote since the last call to the emulator, and notes when it ended."""
        while not self.exited:
            try:
                data = self.process.read_nonblocking(65536, timeout=0)
            except pexpect.TIMEOUT:
                return
            except pexpect.EOF:
                self.process.close()
                self.exited = True
                return
            self.stream.feed(data)
            self.output = (self.output + data)[-OUTPUT_TAIL:]

    def check_running(self):
        """Fails with the exit status and the last of the app's output when it is no longer running."""
        self.pump()
        if self.exited:
            status = (f"signal {self.process.signalstatus}" if self.process.signalstatus is not None
                      else f"status {self.process.exitstatus}")
            output = ESCAPE_SEQUENCE.sub(b"", self.output).decode(errors="replace").replace("\r", "").strip()
            raise AppExited(f"jazygit exited with {status}\n--- last output ---\n{output}")

    def eventually(self, check, description, timeout=TIMEOUT):
        """Like the module's eventually, but fails at once when the app has died, and shows the screen when it
        times out."""
        def checked():
            self.check_running()
            return check()

        try:
            return eventually(checked, description, timeout)
        except AppExited:
            raise
        except AssertionError as e:
            raise AssertionError(f"{e}\n--- screen ---\n{self.text()}") from None

    def text(self) -> str:
        self.pump()
        return "\n".join(line.rstrip() for line in self.screen.display)

    def press(self, *names):
        for name in names:
            self.check_running()
            self.process.send(key(name))
            # Give each key its own read, the way a person's keystrokes arrive.
            time.sleep(0.05)

    def type(self, text: str):
        self.check_running()
        self.process.send(text)

    def wait_for(self, text: str, timeout=TIMEOUT):
        """Waits until text shows anywhere on the screen."""
        self.eventually(lambda: text in self.text(), f"{text!r} on screen", timeout)

    def colour_at(self, text: str, offset: int = 0):
        """The colour of the character offset into where text first shows on the screen."""
        self.pump()
        for y, line in enumerate(self.screen.display):
            x = line.find(text)
            if x >= 0:
                return self.screen.buffer[y][x + offset].fg
        raise AssertionError(f"{text!r} not on screen\n--- screen ---\n{self.text()}")

    def panes(self) -> dict:
        """The panes of the left column, by the first word of their title, with the colour of their border."""
        self.pump()
        return {line[1:].split("─")[0].split(" ")[0]: self.screen.buffer[y][0].fg
                for y, line in enumerate(self.screen.display) if line.startswith("╭")}

    def pane_rows(self, pane: str) -> list:
        """The rows inside the border of the pane of the left column, by the first word of its title: their text,
        and the background they are drawn on."""
        self.pump()
        lines = self.screen.display
        top = next((y for y, line in enumerate(lines) if line.startswith("╭" + pane)), None)
        # Not there, or not drawn whole yet: the first frames can come in pieces.
        if top is None or "╮" not in lines[top]:
            return []
        width = lines[top].index("╮")
        bottom = next((y for y in range(top + 1, len(lines)) if lines[y].startswith("╰")), len(lines))
        return [(lines[y][1:width].rstrip(), self.screen.buffer[y][2].bg) for y in range(top + 1, bottom)]

    def underlined_in_title(self, pane: str) -> str:
        """What the title of the pane of the left column, by the first word of its title, shows underlined: of a
        pane with several lists, such as "Branches │ Remotes │ Worktrees", the name of the one it shows."""
        self.pump()
        top = next((y for y, line in enumerate(self.screen.display) if line.startswith("╭" + pane)), None)
        if top is None:
            return ""
        row = self.screen.buffer[top]
        return "".join(row[x].data for x in range(self.screen.columns) if row[x].underscore).strip()

    def bold_in(self, pane: str) -> list:
        """The words the pane of the left column, by the first word of its title, shows in bold inside its
        border."""
        self.pump()
        lines = self.screen.display
        top = next((y for y, line in enumerate(lines) if line.startswith("╭" + pane)), None)
        if top is None or "╮" not in lines[top]:
            return []
        width = lines[top].index("╮")
        bottom = next((y for y in range(top + 1, len(lines)) if lines[y].startswith("╰")), len(lines))
        rows = [self.screen.buffer[y] for y in range(top + 1, bottom)]
        return " ".join("".join(row[x].data if row[x].bold else " " for x in range(1, width)) for row in rows).split()

    def pane_text(self, pane: str) -> str:
        """What the pane of the left column shows inside its border."""
        return "\n".join(text for text, _ in self.pane_rows(pane))

    def wait_in(self, pane: str, text: str, timeout=TIMEOUT):
        """Waits until text shows in the pane of the left column with this title: the same text can show
        elsewhere, like a file name in the command log, before the pane has it."""
        self.eventually(lambda: text in self.pane_text(pane), f"{text!r} in {pane}", timeout)

    def cursor_row(self, pane: str):
        """The index of the row of the pane the cursor is on, the one drawn on a background of its own; None when
        there isn't one."""
        rows = self.pane_rows(pane)
        backgrounds = [background for _, background in rows]
        odd = [index for index, background in enumerate(backgrounds) if backgrounds.count(background) == 1]
        if len(odd) == 1 and len(backgrounds) > 1:
            return odd[0]
        # A row of two lines, like a branch, has both on the background of the cursor: the first of a single run
        # of rows on another background than the rest.
        if len(set(backgrounds)) == 2:
            plain = max(set(backgrounds), key=backgrounds.count)
            run = [index for index, background in enumerate(backgrounds) if background != plain]
            if len(run) < backgrounds.count(plain) and run == list(range(run[0], run[-1] + 1)):
                return run[0]
        # With two rows, both backgrounds are odd ones; a focused pane also marks the cursor's row with a ">".
        marked = [index for index, (text, _) in enumerate(rows) if text.startswith(">")]
        return marked[0] if len(marked) == 1 else None

    def move_cursor(self, pane: str, word: str):
        """Moves the cursor of the focused pane, a key at a time, to the row with word in it, such as a short
        hash or a branch name. Each key waits for the cursor to move: the cursor is drawn as soon as the app has
        the key, unlike what it shows for the row, which can take a while to read."""
        def target():
            # A word of the row, or what it ends in, as with a file name with spaces in it.
            return next((index for index, (text, _) in enumerate(self.pane_rows(pane))
                         if word in text.split() or text.endswith(" " + word)), None)

        self.eventually(lambda: target() is not None and self.cursor_row(pane) is not None,
                        f"{word!r} and the cursor in {pane}")
        for _ in range(len(self.pane_rows(pane))):
            at = self.cursor_row(pane)
            if at == target():
                return
            self.press("down" if at < target() else "up")
            self.eventually(lambda: self.cursor_row(pane) not in (None, at), f"the cursor in {pane} to move")
        self.eventually(lambda: self.cursor_row(pane) == target(), f"the cursor on {word!r} in {pane}", timeout=0)

    def focused_pane(self):
        """The pane of the left column with the focus: the one whose border is drawn in a colour of its own."""
        colours = list(self.panes().values())
        odd = [name for name, colour in self.panes().items() if colours.count(colour) == 1]
        return odd[0] if len(odd) == 1 else None

    def focus(self, pane: str):
        """Tabs through the panes until pane, such as "Files", "Branches" or "Commits", has the focus."""
        focused = self.eventually(self.focused_pane, "a pane to have the focus")
        for _ in range(len(self.panes())):
            if focused == pane:
                return
            self.press("tab")
            previous = focused
            focused = self.eventually(lambda: (now := self.focused_pane()) != previous and now,
                                      f"the focus to move on from {previous}")
        raise AssertionError(f"no pane {pane} to focus\n--- screen ---\n{self.text()}")

    def wait_gone(self, text: str, timeout=TIMEOUT):
        self.eventually(lambda: text not in self.text(), f"{text!r} to leave the screen", timeout)

    def close(self):
        if self.process.isalive():
            self.process.terminate(force=True)


def isolated_env(home: Path) -> dict:
    """An environment that keeps the user's own git config, settings and recent repositories out of a run."""
    keep = {name: os.environ[name] for name in ("PATH", "LANG", "JAVA_HOME") if name in os.environ}
    return {
        **keep,
        "HOME": str(home),
        "XDG_CONFIG_HOME": str(home / ".config"),
        # Java takes the home directory from the user database, not HOME, so the state needs its own.
        "XDG_STATE_HOME": str(home / ".local" / "state"),
        "TERM": "xterm-256color",
        "GIT_CONFIG_GLOBAL": "/dev/null",
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_AUTHOR_NAME": "Acceptance",
        "GIT_AUTHOR_EMAIL": "acceptance@example.com",
        "GIT_AUTHOR_DATE": "2026-01-01T12:00:00Z",
        "GIT_COMMITTER_NAME": "Acceptance",
        "GIT_COMMITTER_EMAIL": "acceptance@example.com",
        "GIT_COMMITTER_DATE": "2026-01-01T12:00:00Z",
        "GIT_EDITOR": "false",
    }
