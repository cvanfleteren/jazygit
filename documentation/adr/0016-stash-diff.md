# 16. Show the diff of the highlighted stash

Date: 2026-10-22

## Context

Highlighting a stash showed nothing of it: the content panel kept showing the diff of the working tree, as it does
for any pane without a preview of its own. To know what a stash holds, the user had to pop it, or leave the app.

## Decision

- Highlighting a stash shows what it stashed, as one diff: the staged and the unstaged changes and the untracked
  files, which is what applying it brings back. There is no list of its files to go through one by one.
- It is read with `git stash show --include-untracked --stat=1000 --patch --no-color --no-ext-diff
  [--ignore-all-space] <sha>` (`GitInfoProvider.stashChanges`). The flags are all given, so the user's
  `stash.showPatch`, `stash.showStat` and `stash.showIncludeUntracked` do not change it, and it is read as the
  other diffs are: no external diff tool, colored by the app, cut off after as many lines. `--include-untracked`
  takes git 2.32, which the git 2.45 the README asks for has.
- A stash is named by its sha, which stays, not by `stash@{n}`, which changes as stashes are added and dropped. The
  preview is `RepoView.stashDetail`, a `Preview<Sha, DiffText>` of its own, loaded by `Cmd.LoadStashDetail` and
  `LoadMsg.StashDetailLoaded` as the commit's is: what arrives after another stash was highlighted, or after the
  diff options changed, is dropped, and a load that failed is tried again on a tick.
- Like a commit, it is shown under a short header: `stash@{n}: <message>`, the branch it was stashed on and when,
  in the format of a commit's date, then `---`, the changed files with a summary, and the diff. `Stash` holds the
  branch and the time for that; both come from the `git log` that lists the stashes, so the header takes no git
  process. The branch is read from both of git's messages, `On <branch>: …` and `WIP on <branch>: …`. On a
  detached HEAD, git writes `(no branch)` in its place; that is no branch, and the header leaves the line out.
- Ignoring whitespace applies to it, and reads it again.
- Enter on a stash moves the focus to its diff, to scroll it, as Enter on a commit does.
- While the stashes are read, the panel says so; without stashes, it says there are none. A stash read before stays
  shown while the next one is read.

## Consequences

- Each stash highlighted takes a git process, as each commit does. Nothing is kept: going back to a stash reads it
  again.
- A stash cannot be applied file by file, nor its files looked at one at a time. That would be a view of its own.
