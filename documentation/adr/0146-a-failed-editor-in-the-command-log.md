# 146. An editor that fails is in the command log, and one that exits as it starts hints at a terminal

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

- **The log shows what ran.** Every failure of an editor, one the user set or git's, one left running or one with the
  terminal, one that did not start too, carries the command line it was run as, so the command log shows it as it
  shows a git command that failed.
- **An editor that exits as it starts hints at a terminal.** An editor left running that exits with an error before it
  settled fails with `GitError.EditorExitedAtStart`, not `EditorFailed`:
  `The editor "claude" exited with code 1 as it started. If it runs in the terminal, set terminal = true for it in the
  settings file.` An editor that fails that fast, set without `terminal = true`, is most often a terminal program.
- **The error output is still thrown away.** Not:
  - a pipe jazygit reads: a program left running is to outlive jazygit, and once jazygit quits, or crashes, its next
    line on a pipe nobody reads kills it by `SIGPIPE`;
  - a temporary file, removed once the program settled: a program that prints without end fills the disk with a file
    no directory lists, until it stops;
  - a pipe, with the program started through `sh -c 'trap "" PIPE; exec "$@"'` so that it ignores `SIGPIPE`: every
    editor and browser through a shell, which Windows has not, and a program that takes a failed write as fatal may
    still stop.

## Consequences

- A misconfigured editor is found from jazygit: the command log has the command, and the error the likely fix.
- The error does not say why the editor failed when it is not for want of a terminal; running the command from the log
  in a shell does.
- An editor with the terminal prints why on the terminal, as before.

Amended by ADR 183: a program the user set that exits as it starts fails as a `ProgramFailure.ExitedAtStart`, not a
`GitError.EditorExitedAtStart`; the command log still shows what ran.
