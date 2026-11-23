# 183. The programs the user set are started through a launcher, not through git

Date: 2026-11-23

## Context

The editors that open a worktree or a file (ADR 110, 145) and the agents (ADR 161) are programs the user set in the
settings file. They were started through `Git.editor()`, with `open` (the terminal handed over) and `launch` (started
and left running), beside the methods that edit a file in the editor git names. Starting a program the user set is not
git's business: `features-agent` needed a `Git` only to start a process, and each of the three commands that started
one chose between the two modes and handed the terminal over itself. A program that failed did so as a `GitError`,
worded as an editor's, so an agent that could not start was "the editor `claude`". User commands on any subject (the
next step of making jazygit's parts plugins) would have added a fourth copy.

## Decision

- **A `ProgramLauncher` in `Env`** (`kernel.state.launch`) starts the programs the user set, with the two modes ADR 110
  named: `run` hands the terminal over and waits until the program exits; `start` leaves it running, with no input and
  its output thrown away, as a browser is (ADR 85). `launch(program, directory, commandLine)` picks the mode the
  `Program` says. The launcher hands the terminal over itself, so a command no longer does.
- **A field of `Env`, not a `Service`.** It is the kernel's, as the terminal is, and every group of features may start a
  program. The app sets it, from the terminal it shows on, when it takes the environment over (`withApp`); everywhere
  else, as in the tests, it is `ProgramLauncher.NONE`, which starts nothing and says each program ran.
- **Its own failures.** A `ProgramFailure` (`NotStarted`, `Failed`, `ExitedAtStart`, `Interrupted`) is a
  `Failure.Phrased`, worded by the program's name, not as an editor's. It carries the command line it ran as, so the
  command log shows it (ADR 146), and `ExitedAtStart` keeps the hint at `terminal = true`.
- **Git keeps what is git's.** `Editor` keeps `edit`, `editMessage` and `editText`, which run the editor
  `git var GIT_EDITOR` names. `open`, `launch` and `GitError.EditorExitedAtStart` are gone from `core-git`.
- **An architecture rule** keeps the features and the kernel from starting a program through `Exec` themselves: only
  the launcher does.

## Consequences

- The commands that start a program are one line: `env.launcher().launch(editor, directory, commandLine)`, and their
  tests give a launcher that records what it was asked, rather than a fake git.
- User commands on a subject, a difftool or a mergetool start their programs the same way.
- Git's own editor still runs through `CliEditor`, which hands nothing over itself: the commands that edit a commit
  message or a file still hand the terminal over around it. Running it through the launcher too is left for when it
  pays off.
- The browsers `core-gh` and `features-forgejo` start for a link are still their own, and the architecture rule lets
  a `Browser` start one. A `ProgramLauncher` that opens a link is the next step, once a third place opens links.
