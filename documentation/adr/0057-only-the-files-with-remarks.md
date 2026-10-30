# 57. Only the files with remarks

Date: 2026-10-30

## Context

The tree of a pull request's files (ADR 55) lists every file it changes, and shows `💬2` after a file with unresolved
review threads, their count. A directory does not add up those of its files. In a pull request of many files, the few
with remarks are hard to find: the tree has to be walked file by file.

The tree of a commit or a stash is the same tree, with the same keys (`commitFiles`): Escape, `t` and `R`. A
directory that is highlighted shows the diff of the files directly in it (`FileTree.directEntries`). Whether every
thread is shown open, `t`, is kept in `CommitFilesView`, not in the settings.

## Decision

### Which files are listed

- `c` in the tree cycles through what it lists: every file, then only the files with an unresolved thread, then the
  files with any thread, resolved too, then every file again. Unresolved comes first, as it is what is looked for most,
  one key away. Not: one switch between every file and the unresolved ones only, which leaves no way to find the
  resolved ones.
- It is kept in `CommitFilesView` as `listed`, a `CommitFilesView.Listed`: `ALL`, `UNRESOLVED` or `REMARKED`, whose
  `next()` gives the order of the cycle. Each tree opens on `ALL`; it is not kept in the settings, as `t` is not.
- A file is kept when a thread of the kind is on it as `ReviewThread.isOn` says: on its path or the path it was
  renamed from. A thread about the whole file, an outdated one and one that cannot be placed count as any other.
- It does nothing in the tree of a commit or a stash, which have no threads.

### The tree

- The tree is pruned: only the files kept, and the directories that lead to them. A directory that is highlighted
  shows the diff of the files kept directly in it, as `directEntries` reads the pruned tree.
- The title says what is listed: `Files of #123 · unresolved remarks` or `Files of #123 · with remarks`, after
  `· merged` or `· closed` when the pull request is: `Files of #123 · merged · with remarks`. The count in the border
  counts the files kept.
- When none is kept, the pane says why in place of the tree, and the content panel shows nothing: `No file has
  unresolved remarks`, or `No file has remarks`; `Reading the remarks…` while they are read; and why the read failed
  when it did and none were read before. Not: refusing to switch, which a slow read of the remarks would make look
  like a lost key.
- The files kept are those of the threads read last: after `R`, a file whose last unresolved thread was resolved
  leaves the tree of unresolved remarks. The highlight stays on its file while it is listed, else it moves to the next
  file listed, or to the one before at the end.

### Marks

- A file with resolved threads shows `✓1` after them, their count, dimmed in `diff-thread`, after its `💬`:
  `Foo.java 💬2 ✓1`, or `Bar.java ✓3` when all are resolved. It shows in every mode, so a file's marks do not depend on
  what is listed. Not: no mark, which would list a file with only resolved threads in `REMARKED` without saying why.

### Delivery

1. This ADR.
2. `Listed`, the pruning of the tree, `c`, the titles, the empty pane and the `✓` marks, with a test of the pruning on
   its own and an acceptance test against a fake gh.

## Consequences

- The files of a pull request with remarks, unresolved or all, can be listed on their own, one or two keys away.
- Every file with resolved threads shows a mark it did not show before.
- `c` in the tree of a commit or a stash does nothing.
