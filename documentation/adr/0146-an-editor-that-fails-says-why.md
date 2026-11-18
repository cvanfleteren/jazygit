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

- **The error output is kept.** A program left running (`Exec.launch`) writes its error output to a temporary file
  of its own. When it exits with an error before it settled, the first 64 KiB of that file, as for git (ADR 79), are in
  the `ExecFailure.NonZero`. Once it settled, or failed, the file is removed: the program keeps writing to it while it
  runs, and the space is freed when it stops. Its standard output is still thrown away. Without a temporary file, the
  error output is thrown away, as before.
  Not: a pipe that jazygit reads. A program left running is to outlive jazygit; once jazygit quits, or crashes, its
  next line on a pipe nobody reads kills it by `SIGPIPE`.
- **The error says why.** `GitError.EditorFailed` has the line that says why, chosen as for git
  (`Exec.explanation`): `The editor "claude" exited with code 1: 'claude agents' requires an interactive terminal`.
  An editor that has the terminal prints on it, and has none.
- **The log shows what ran.** Every failure of an editor, one the user set or git's, one left running or one with the
  terminal, one that did not start too, carries the command line it was run as, so the command log shows it as it
  shows a git command that failed.

## Consequences

- A misconfigured editor is found from jazygit: the command log has the command, the error why it failed.
- A browser that fails has its error output in the `ExecFailure` too; its error does not show it yet.
- A program left running that prints on its error output without end fills the disk with a file no directory lists,
  until it stops; before, that went to `/dev/null`. Editors and browsers print little there.
- On Windows a file that is open cannot be removed: it is removed when jazygit exits instead.
