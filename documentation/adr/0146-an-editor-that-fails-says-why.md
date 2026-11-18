# 146. An editor that fails says why, and how it was run

Date: 2026-11-18

## Context

An editor jazygit starts and leaves running (ADR 110), as a browser is (ADR 85), has its output thrown away. When it
exits with an error before it is taken to have started, the error says only its program and exit code:
`The editor "claude" exited with code 1`. Why it failed went to the error output that was thrown away, here
`'claude agents' requires an interactive terminal`: the entry lacked `terminal = true`.

Nor did the command log show what was run. A failed editor's `GitFailure` carried no commands, so the command line,
`{dir}` filled in, or git's editor with the files added, was only in the debug log. A git command that fails is in the
command log; an editor was not.

## Decision

- **The error output is read.** A program left running (`Exec.launch`) has its error output read on a thread of its
  own for as long as it writes it, of which the first 64 KiB are kept, as for git (ADR 79). Its standard output is
  still thrown away. When it exits with an error before it settled, the error output it printed, read for at most half
  a second more, is in the `ExecFailure.NonZero`.
  Not: throwing it away once the program settled, by closing the pipe, which kills a program at its next line; nor a
  file, which a program that runs for days fills.
- **The error says why.** `GitError.EditorFailed` has the line that says why, chosen as for git
  (`Exec.explanation`): `The editor "claude" exited with code 1: 'claude agents' requires an interactive terminal`.
  An editor that has the terminal prints on it, and has none.
- **The log shows what ran.** Every failure of an editor, one the user set or git's, one left running or one with the
  terminal, one that did not start too, carries the command line it was run as, so the command log shows it as it
  shows a git command that failed.

## Consequences

- A misconfigured editor is found from jazygit: the command log has the command, the error why it failed.
- A browser that fails has its error output in the `ExecFailure` too; its error does not show it yet.
- Each program left running holds a thread, which ends when the program and what it started close their error output.
