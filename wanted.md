# Wanted features

Features to consider, roughly most valuable first.

## High impact (daily-driver gaps)

1. **Stage hunks and lines.** Only whole files can be staged today. Add a mode in the diff pane to stage or unstage
   one hunk or a range of lines using `git apply --cached`. A patch made from a diff that ignores whitespace (Ctrl+W)
   does not apply: stage from a diff read without `--ignore-all-space`, or refuse while whitespace is ignored, as
   lazygit does.
2. **Resolve conflicts in the app.** Pick ours or theirs per file, or open the file in `$EDITOR` (see TODO.md).
3. **Open in `$EDITOR`.** Suspend the TUI and open the file, or the commit message, in your editor. Conflict
   resolution and longer commit messages both need it, and it works around the missing word-editing keys in text
   fields.
   - [x] Open the selected file (`e` in the files pane).
   - [x] Edit the commit message in the editor from the commit and reword dialogs (Ctrl+O).
4. **Interactive rebase.** Move a commit up or down, and mark several commits for squash, fixup or drop in one pass.
   The single-commit versions exist; the next step is a batch "edit todo" view.
5. **Undo with the reflog.** A reflog view with "reset to here", plus an `undo` key that reverts the last operation
   the app ran, building on the command log.

## Medium

6. **Revert a commit** from the commits panel (revert is only handled as an in-progress state today).
7. **Tags.** The `Tag` model exists. Add a tags tab to create (lightweight or annotated), delete, push and check out
   tags.
8. **File history and blame.** The log for the selected file (`git log --follow`), and maybe a blame view that jumps
   to the commit.
9. **Search commits.** Filter by message, author or path (`-S` for pickaxe), possibly reusing `ListFilter`.
10. **Commit graph.** `CommitGraph` exists in the model; draw the lanes in the commits panel if it doesn't yet.
11. **Diff options.** Ignoring whitespace is done (Ctrl+W, `DiffOptions`), and so is the rich view of the changes of
    the working tree, of a commit and of a stash, with line numbers and the changed words highlighted (Ctrl+D, ADR 44,
    ADR 47), and its code coloured by its syntax (ADR 53). Still to do: change the amount of context, and side-by-side, as more fields of `DiffOptions`; the rich
    view for the diff of a pull request. The pull request diff (`gh pr diff`) shows all whitespace; it could be
    filtered, or read with git when the branches are fetched.
    - **More of the syntax** in the rich view (ADR 53): a table in the settings from paths to languages, for the
      extensions a team makes up; more languages; the code in a fenced block of Markdown in its own language; Rust's
      raw strings, nested block comments, shell heredocs, and the expressions in Python's f-strings and Kotlin's
      templates.
    - **Act on the files of a commit** in the tree `F` opens (ADR 44): check a file out as it was in the commit, open
      it in the editor, or revert the changes of one file.
12. **Start from git's prepared message.** Fill the commit dialog from `MERGE_MSG` or `SQUASH_MSG` (see TODO.md).

## GitHub

13. **Create a PR** from the current branch, with base selection and draft or ready status (`gh pr create`).
14. **Check out someone else's PR** (`gh pr checkout`), possibly into a new worktree.
15. **CI check details and logs** for a failing check, and a rerun action.
    - **More of the review remarks** in the diff of a pull request (ADR 55): reply to a thread, resolve it, or start
      one on a line, once the content panel can highlight a line; the diff, and its remarks, of a merged
      pull request, read against the commit it was merged with; the remarks in the preview of the pull requests
      screen, for a pull request of another repository.

## Nice to have

16. **Custom commands** defined in the TOML settings and bound to keys, with templated `{branch}` and `{sha}`
    placeholders.
17. **Bisect.** Mark commits good or bad from the commits panel and show bisect as an in-progress state.
18. **Compare two refs.** Mark one commit or branch, then diff it against another.
19. **Submodules.** List, update and enter them.
20. **Auto-refresh with a file watcher.** Reload status when the working tree changes outside the app.
21. **Build a patch from a commit.** Move selected files or hunks out of an old commit into the index or into a new
    commit.
