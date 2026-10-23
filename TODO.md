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

## Smaller items

- [ ] **Stronger types** for stash refs and remotes, which are now plain `String`s, like branch names are now
  `BranchName`. Also for the stage target, where `""` stands for the repository root; a sealed `Target` would
  be clearer. The `gh` layer keeps branch names as `String`s, as it knows nothing of git.
- [ ] **Typed reasons for ignored requests.** Some requests do nothing and don't say why, for example
  squashing a root or merge commit, or deleting the current or a busy branch. They could report an
  `AppError` instead.
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
- [ ] **Word-level editing in text fields.** Ctrl+W and Alt+Backspace (delete word back), Ctrl+←/→ and Alt+B/F
  (move by word), Ctrl+A/E (line start/end), Ctrl+U/K (delete to line start/end). TamboUI has none of these:
  its states only edit by character and can't set the cursor, its key handlers treat Ctrl+arrows as plain
  arrows, and they handle keys before an `onKeyEvent` lambda. A wrapper registered under the field's id, like
  `SubmittableTextArea`, can see the keys first and apply pure word functions on (text, cursor). Start with
  the single-line inputs and the commit summary; the description area needs rows and wrapping. How terminals
  send Ctrl+Backspace and Ctrl+arrows varies. Worth contributing to TamboUI later.
- [ ] **Resolve conflicts in the app.** A merge, rebase, cherry-pick or revert in progress is shown in the status
  panel, and can be continued, aborted or skipped (M); operations started in the app that stop on conflicts are left
  so. But the conflicts themselves are resolved outside the app. Picking ours or theirs per file, and opening a file
  in `$EDITOR` (suspending the TUI), would make that possible here.
- [ ] **Commit with the message git prepared.** After resolving a squash merge, or a merge that is committed instead
  of continued, git keeps a message in `.git/SQUASH_MSG` or `.git/MERGE_MSG` (listing the squashed commits, or naming
  the merged branch). The commit dialog starts empty instead; it could start from that message, without git's `#`
  comment lines.
- [ ] **Back out of a squash merge that stopped on conflicts.** Git keeps no merge in progress for it, so M offers
  nothing; `git reset --merge`, which keeps unrelated uncommitted changes, is run outside the app. Discarding all
  changes in the app would throw those away too. M could offer "abort" (`reset --merge`) when files have conflicts
  but nothing is in progress.
- [ ] **Skipping an amend that stopped on conflicts loses the staged changes.** Amending an older commit commits them
  as a `fixup!` commit and rebases to fold it in. When that stops on the `fixup!` commit, skipping it (M) leaves the
  changes out: once the rebase is done, only the reflog still has them. The rebase is marked as an amend (see
  `CliInProgress.markAmending`), so M could warn before skipping it, or offer to abort instead.
