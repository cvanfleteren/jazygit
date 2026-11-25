# 196. A program left running outlives the terminal jazygit runs in

Date: 2026-11-25

## Context

A program jazygit starts and leaves running, an editor or agent with `terminal = false` (ADR 183) or a browser (ADR
85), was started as a plain child process: in jazygit's session and its process group, which is the terminal's
foreground group while jazygit shows. Quitting jazygit from a shell left the program running, as the shell took the
terminal back. But when jazygit is the program that owns the terminal, as in a tmux or zellij popup, a terminal
started as `kitty jazygit` or a key binding of the window manager, the kernel sends a hangup to the terminal's
foreground group when jazygit exits, and most programs quit on it: quitting jazygit closed the editor it had opened.
Closing the terminal window did the same.

## Decision

- **A program left running gets a session of its own, through `setsid`**, put before its command line in
  `Exec.launch`, the one place such programs start. In its own session it has no controlling terminal, so no hangup of
  the terminal reaches it. Java's `ProcessBuilder` cannot call `setsid()` itself, and the child it starts never leads
  a process group, so `setsid` runs the program in its own process without forking: waiting for it to settle and its
  exit code work as they did (ADR 146).
- **Only when both `setsid` and the program are found on the `PATH`.** A program that is not found is run as it is,
  so that it still fails to start, worded as such, rather than as `setsid` exiting with 127.
- **Not on Windows**, where a `setsid` on the `PATH` is Cygwin's or MSYS's, which knows no Windows console. A GUI
  program there has no console to lose; a console program still shares jazygit's, and is closed with it.
- **macOS has no `setsid`**, so a program there is still started as before.
- What jazygit logs and says it ran is the command line it was given, without `setsid`.

## Consequences

- Quitting jazygit, or closing its terminal, leaves the programs it started running on Linux.
- On macOS, and for a console program on Windows, a program started from a jazygit that owns its terminal still ends
  with it. `nohup`, or a session through `posix_spawn` from native code, can take its place when it is asked for.
- A program run with the terminal handed over (`terminal = true`) is unchanged: it is meant to end with the terminal.
