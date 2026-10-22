"""
Drives the built jazygit jar in a pseudo terminal, and reads its screen and the repository it works on.

The tests only know the jar, the keys and git: nothing here imports or reads the application's code.
"""

import os
import re
import subprocess
import time
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
        self.env = env

    def __call__(self, *args) -> str:
        return subprocess.run(
            ["git", *args], cwd=self.path, env=self.env, check=True, capture_output=True, text=True
        ).stdout.strip()

    def has_head(self) -> bool:
        return subprocess.run(["git", "rev-parse", "--verify", "-q", "HEAD"], cwd=self.path, env=self.env,
                              capture_output=True).returncode == 0

    def write(self, name: str, content: str):
        file = self.path / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)


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
        """Like the module's eventually, but fails at once when the app has died."""
        def checked():
            self.check_running()
            return check()

        return eventually(checked, description, timeout)

    def text(self) -> str:
        self.pump()
        return "\n".join(line.rstrip() for line in self.screen.display)

    def press(self, *names):
        for name in names:
            self.process.send(key(name))
            # Give each key its own read, the way a person's keystrokes arrive.
            time.sleep(0.05)

    def type(self, text: str):
        self.process.send(text)

    def wait_for(self, text: str, timeout=TIMEOUT):
        """Waits until text shows anywhere on the screen."""
        try:
            self.eventually(lambda: text in self.text(), f"{text!r} on screen", timeout)
        except AppExited:
            raise
        except AssertionError as e:
            raise AssertionError(f"{e}\n--- screen ---\n{self.text()}") from None

    def wait_gone(self, text: str, timeout=TIMEOUT):
        try:
            self.eventually(lambda: text not in self.text(), f"{text!r} to leave the screen", timeout)
        except AppExited:
            raise
        except AssertionError as e:
            raise AssertionError(f"{e}\n--- screen ---\n{self.text()}") from None

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
