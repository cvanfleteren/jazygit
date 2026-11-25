# 201. A file in conflict is handed to a merge tool or to mergiraf, and opens at its first conflict

Date: 2026-11-25

## Context

The resolve dialog (ADR 198, 200) keeps one side of a file in conflict, or opens it in an editor (`e`, ADR 145) at its
first line. Merging the two sides inside the file is done there by hand, or outside the app: in the merge tool git is
configured with (`git mergetool`), or with a tool that merges by the syntax of the file, as mergiraf does.

Mergiraf works two ways. As git's merge driver, set in the user's git config and `.gitattributes`, git calls it for
every merge, rebase, cherry-pick and revert, which the app runs with git, so it needs nothing of the app; what it
cannot solve is left as a conflict. As `mergiraf solve <file>`, it solves what it can of a file already in conflict and
leaves markers for the rest. It reads conflicts with the base in them (diff3), and writes a backup, `<file>.orig`,
unless told not to.

`git mergetool` asks in the terminal when it does not know what to do: which tool, when no `merge.tool` is set; whether
to keep or delete a file one side deleted; whether the merge worked, when the tool's exit code is not trusted. It keeps
the file as it was in conflict as `<file>.orig` (`mergetool.keepBackup`, true by default), which then shows as an
untracked file.

## Decision

1. **`e` opens a file in conflict at its first conflict.** Opening a file in conflict reads it for markers first, as
   staging does (ADR 198), and opens it at the line of the first `<<<<<<< `: in the files pane, in the resolve dialog,
   and in the question staging asks of a file with markers. Only an editor set in the settings (ADR 145) takes a
   line; git's editor, when none is set, opens it at its start, as it did, and the dialog says so. Not: guessing from
   git's editor's name what argument would take it to a line, which differs per editor and breaks on a wrapper.
2. **`g` hands the file to git's merge tool**, from the resolve dialog: `git mergetool -- <path>`, on the terminal,
   which the app hands over as it does to an editor (ADR 145), and waits for. Git decides whether the file is resolved,
   by the tool's exit code or by asking, and stages it when it is.
   - Offered only when `merge.tool` is set, read when the dialog opens: without it, git asks which tool to use.
   - Only on a file both sides changed: on a file one side deleted, git asks whether to keep it or delete it, which
     `o` and `t` say already.
   - Run with `-c mergetool.keepBackup=false`, unless the user set `mergetool.keepBackup`, whichever way: a backup
     the user did not ask for is an untracked file to clean up.
3. **`m` solves the file with mergiraf**, from the resolve dialog: `git checkout --conflict=diff3 -- <path>`, which
   writes the conflict again with its base, then `mergiraf solve --keep-backup=false <path>`. It runs without the
   terminal, as it asks nothing. It never stages the file: what mergiraf merged is for the user to look at first, as
   mergiraf says itself.
   - Offered only when a program `mergiraf` runs, tried when the dialog opens, and only on a file both sides changed.
   - Mergiraf's exit code 2, conflicts left, is not a failure: the file has fewer of them, and the user goes on with
     `e`, `o` or `t`.
4. **After `g` or `m`, the app says where the file is.** It reads the file for markers and whether it is still in
   conflict: still in conflict without markers, it says that `space` stages it; with markers left, where the first
   is; resolved, nothing, as the files pane marks it so.
5. **The dialog says the command of each.** `ConflictTools` says what the repository has, and the command lines of
   `g` and `m`; the commands run them as it says them, and the dialog quotes them (ADR 200), so the two cannot differ.
   The command of `e` with an editor set takes the line it opens at.
6. **With mergiraf as git's merge driver, `u` brings back what mergiraf made of the file**, not git's own conflict:
   `git checkout -m` merges the file again from the index, and git calls the driver for that too. What comes back is
   the file as the operation stopped with it, the conflicts mergiraf could not solve, with its own labels (`ours`,
   `base`, `theirs`). The help of `u` says "as the operation stopped with it". Not: running `checkout -m` without the
   driver, which would bring back conflicts the user never saw.
7. **The dev shell has mergiraf**, and CI installs it beside git, for the tests of `m`.

This amends ADR 198, whose dialog kept a side or opened the file at its first line, and whose consequences left
merging the sides of a file to an editor.

## Consequences

- A file is merged in the user's merge tool, or by mergiraf, without leaving the app.
- Opening the resolve dialog reads `merge.tool` and `mergetool.keepBackup`, tries `mergiraf --version`, and reads the
  file for markers: four processes and a file read, each time it opens.
- Opening a file in conflict in an editor reads it for markers first.
- A file that has `<<<<<<< ` at the start of a line where it belongs opens there too.
