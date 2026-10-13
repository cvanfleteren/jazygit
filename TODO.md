# TODO

Refactorings that were left out of the functional/immutable cleanup, and why.

## Side effects in `JazygitApp.render`

- [ ] **Stop dispatching selection changes from `render`.** `render` calls
  `branchesPanel/commitsPanel/filesPanel.selectionChange(model)` and dispatches the result. It does this
  because the TamboUI list and tree widgets only know their selection after they have been rendered.
  Fixing it means reacting to selection changes in the widgets' key and mouse handlers, or changing how
  the panels work with TamboUI.
- [ ] **Move the focus bookkeeping out of `render`.** `helpOrigin` and `lastFocus` are mutable fields that
  `render` updates, so that a closing popup can give the focus back. The model could hold the focus origin
  instead, for example the pane in `HelpPopup` and `ThemePopup`.

## Translated text outside the views

- [ ] **`StashUpdate.defaultName`** builds the default stash name with `Messages` ("WIP on …"). That text is
  passed to git as the stash message, which is why it was left. Options:
  - don't pass `--message` when the name is blank, and let git generate the name. The listed name then
    becomes git's own format, `WIP on main: abc1234 msg`;
  - build the name without `Messages`.
- [ ] **`RepoStatus.branchLabel`** in `git.model` uses `Messages` for "HEAD detached at …". Move the text
  into the view, and keep only the data in the model.
- [ ] **`CliDiff.truncated`** adds a translated "… truncated" line in the git layer. `Diffs` could carry
  whether the diff was cut off, and the view could add the line.

## Git layer

- [ ] **`CliBranch.tracking`** runs its own `git for-each-ref` per branch to find where the branch is
  pushed. `CliBranches.list` already reads all branches in one call, but `Upstream` only keeps the short
  name, for example `origin/main`. That name is ambiguous when a remote name contains `/`. To reuse the bulk
  read, keep the remote name and the remote ref in `Branch`/`Upstream`, then derive the push target from
  them.
- [ ] **Reshape the capability interfaces** so that each one maps onto one `Cli*` class (optional):
  - `GitRead.branches()` belongs with `GitBranches`;
  - `GitRead.stashes()` belongs with `GitStash`;
  - `commits`/`log`/`commitChanges` belong with `GitCommits`;
  - `GitCommits.commit` and `amendLastCommit` are implemented in `CliIndex`.

## Smaller items

- [ ] **The 17 one-line `Msg.apply` overrides**, one per feature message interface, are kept: each one is how
  a feature's messages reach its update class. Removing them would mean a central dispatch, which the
  design avoids on purpose.
- [ ] **No `Update.cancel` helper.** The cancel cases are one-liners, `Next.of(model.withoutPopup(X.class))`,
  and a helper would be no shorter.
- [ ] **Telescoping constructors** on `Branch`, `Commit` and `FileEntry` (cleanup only).
- [ ] **Stronger types** for branch names, stash refs, remotes and the theme, which are now plain `String`s.
  Also for the stage target, where `""` stands for the repository root; a sealed `Target` would be clearer.
- [ ] **Typed reasons for ignored requests.** Some requests do nothing and don't say why, for example
  squashing a root or merge commit, or deleting the current or a busy branch. They could report an
  `AppError` instead.
- [ ] **Run the app by hand** (`./run.sh`) after the refactoring, and try the main operations: checkout,
  push, merge, rebase, reword, squash and stash. Force a failure too, such as a rebase conflict, and check
  the error text and the command log.
