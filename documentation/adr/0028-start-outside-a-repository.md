# 28. Start outside a repository

Date: 2026-10-23

## Context

`JazygitApp.start` opens the repository containing the start directory (`CliGitInfoProvider.open`). When there is
none, it prints the error on stderr and exits with 1: started in the wrong folder, or in a new one, the app is gone
before it shows anything. `JazygitApp` cannot be built without a repository; it can show another one in place of
the one it shows (`switchTo`), as the recent repositories do.

The app opens a repository without commits as it is: the status, log, branches, stashes and worktrees all read
empty on an unborn HEAD, and the acceptance tests start it on a plain `git init`.

## Decision

### When

- The start directory is not inside a repository, or the start path does not exist: a dialog says so and offers
  what to do.
- A bare repository (no working tree) is left for a feature of its own: offering to create a worktree.
- When git cannot be run, the app prints the error and exits with 1, as before.

### A startup program before the app

- A small TamboUI program of its own, Elm-like as the app is (its own model and update), runs before `JazygitApp`
  is built. It ends with the directory of a repository, which goes to `start` as the start directory did, or with
  quitting.
- Not: an app without a repository (`Option<Repo>` in the model, every pane knowing about it), showing the dialog
  as a `Popup`. That touches nearly everything, for a dialog shown before anything else.
- The theme and the settings are loaded before it, so it looks like the app, `--theme` included.

### The dialog

- Centred, as the app's dialogs: `<absolute path> is not inside a git repository`, or `<absolute path> does not
  exist`.
- The options are a list: ↑/↓ and j/k move, Enter chooses, and each has a key of its own:
  - **i: init here.** A plain `git init` in the start directory, as the user's git config has it (the initial
    branch, templates); no initial commit. Then the repository opens. When it fails, git's error shows in the
    dialog, which stays. Not offered when the path does not exist: no creating the folder, as a typo would create
    one.
  - **o: open another folder…** A path is typed: relative to the start directory, `~` for the home directory, no
    tab completion. A folder inside a repository opens it, searching upwards as git does. A folder outside one
    shows the dialog again, for that folder, so it can be initialised there. A path that does not exist shows an
    error under the input, which keeps what was typed. Esc goes back to the options.
  - **r: recent repositories.** The list of the recent repositories, with the rows the app shows (`RecentRepo`).
    Choosing one opens it, Esc goes back to the options. Not offered when there are none.
  - **q: quit.** Esc, q and Ctrl-C quit too. The exit code is 0, with nothing on stderr: the user chose to.

### Not now

- Cloning a repository.
- Opening a folder from inside the app (Ctrl-O); it can use the same input, through `switchTo`.

## Consequences

- Starting in the wrong or a new folder no longer ends the app; a new repository is one key away.
- The theme and settings load before the repository is opened, also when there is one.
- Init leads to a repository without commits. Unstaging and discarding there likely fail, as they reset and check
  out against `HEAD` (`CliIndex`); that is fixed apart from this, in `TODO.md`.
- Tested by unit tests of the startup program's update (init, open another folder, back to the dialog, quit), and
  by acceptance tests that start the app in a folder outside a repository: init, open another folder, quit with
  exit code 0.
