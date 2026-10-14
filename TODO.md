# TODO

Refactorings that were left out of the functional/immutable cleanup, and why.

## Side effects in `JazygitApp.render`

- [ ] **Stop dispatching selection changes from `render`.** `render` calls
  `branchesPanel/commitsPanel/filesPanel.selectionChange(model)` and dispatches the result. It does this
  because the TamboUI list and tree widgets only know their selection after they have been rendered.
  Fixing it means reacting to selection changes in the widgets' key and mouse handlers, or changing how
  the panels work with TamboUI.
  The same goes for `Msg.Focused`, which `render` dispatches when the focus has moved to another element
  outside the popups, as the focus lives in TamboUI's focus manager rather than in the model.
- [ ] **The other effects of `render`.** It also activates the theme on the shared `StyleEngine`, moves the focus
  into a popup that opened and back out of one that closed, and fills a text dialog's input the first time it
  shows. The panels copy the focus (`FilesPanel.focused`, `LoadableList.focused`), and `BranchesPanel` copies
  the busy branches and pull requests, because `LoadableList` builds its rows from the value alone.

## Git layer

- [ ] **Reshape the capability interfaces** so that each one maps onto one `Cli*` class (optional):
  - `GitRead.branches()` belongs with `GitBranches`;
  - `GitRead.stashes()` belongs with `GitStash`;
  - `commits`/`log`/`commitChanges` belong with `GitCommits`;
  - `GitCommits.commit` and `amendLastCommit` are implemented in `CliIndex`.

## Smaller items

- [ ] **The 23 one-line `Msg.apply` overrides**, one per feature message interface, are kept: each one is how
  a feature's messages reach its update class. Removing them would mean a central dispatch, which the
  design avoids on purpose.
- [ ] **No `Update.cancel` helper.** The cancel cases are one-liners, `Next.of(model.withoutPopup(X.class))`,
  and a helper would be no shorter. A generic `PopupMsg.Closed(type)` sent by the dialog widgets would remove
  some 20 `Cancelled` records, at the cost of a message that is not about a feature.
- [ ] **Telescoping constructors** on `Branch`, `Commit` and `FileEntry` (cleanup only).
- [ ] **Stronger types** for branch names, stash refs, remotes and the theme, which are now plain `String`s.
  Also for the stage target, where `""` stands for the repository root; a sealed `Target` would be clearer.
- [ ] **Typed reasons for ignored requests.** Some requests do nothing and don't say why, for example
  squashing a root or merge commit, or deleting the current or a busy branch. They could report an
  `AppError` instead.
- [ ] **One process layer for git and gh.** `GitProcess` and `GhProcess` turn an `ExecFailure` into their own
  errors the same way, and `GitError` and `GhError` repeat the same five process failures, which have started
  to drift (`TimedOut.workTree`, `CommandFailed.says`). A `process.ProcessFailure` that both wrap would share
  them, at the cost of changing every match on those errors.
- [ ] **A fetch can run next to another git command.** It runs on its own thread so it doesn't hold up the
  others; git's locks keep the repository intact, but a command that needs a ref the fetch holds can fail.
  Retrying such a failure, or pausing the fetch while an operation runs, would avoid it.
- [ ] **Robustness left out:**
  - The command log reserves one line for the error, which can wrap onto more.
  - Thousands of paths in one command can exceed the system's limit; `--pathspec-from-file` would avoid
    that.
  - Commit signing with a terminal pinentry still prompts on the TUI's terminal.
  - The pull requests are read up to 200 at a time.
  - Opening the app from inside `.git` reports a failed command, not that there is no working tree.
- [ ] **Show what the repository is in the middle of.** A merge or rebase in progress isn't shown. Operations
  on it are left to git, which refuses most of them, and the app no longer undoes what the user started.
- [ ] **Run the app by hand** (`./run.sh`) after the refactoring, and try the main operations: checkout,
  push, merge, rebase, reword, squash, fixup and stash. Force a failure too, such as a rebase conflict, and check
  the error text and the command log.
