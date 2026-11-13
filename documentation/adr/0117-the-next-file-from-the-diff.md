# 117. The next file from the diff

Date: 2026-11-13

## Context

The files pane shows a tree, of the working tree or of the files of a commit, a stash (ADR 44), a pull request
(ADR 55) or a branch (ADR 61). Its highlighted node shows in the content panel, and Enter moves the focus there. To
read the next file, the user goes back to the tree with Escape, moves down with `j`, and goes into the diff again
with Enter: three keys per file, which add up when reviewing a pull request file by file.

In the content panel, `ScrollArea` gives the keys it does not take to move or scroll to `Scopes.DIFF` or
`Scopes.PULL_REQUEST_DIFF` (ADR 60, 77), but only while the diff has a cursor: in the plain style keys only scroll.
`n` and `N` already move to the next and previous remark in the diff of a pull request.

## Decision

`J` and `K` in the content panel move the highlight of the files pane to the next and the previous file. The focus
stays in the content panel, which shows the diff of the file moved to.

### Which file is next

- Files only: directories, and the root `/`, are skipped.
- In the order the tree shows them, with what it leaves out left out: a filter (ADR 105), or the files without remarks
  (ADR 57).
- From a directory or the root, which show several files at once, `J` moves to the first file after it in the tree,
  which for a directory is its first file, and `K` to the last file before it.
- On the last file `J`, and on the first `K`, does not move, and says there is no next or previous file. It does not
  wrap around to the other end: in a long pull request that loses where one is.

### Where it works

- In every tree of the files pane: the working tree and the files of a commit, a stash, a pull request or a branch.
- In both styles of the diff, rich and plain. Which file is next is the tree's, not the cursor's.
- Only when the diff shown is the files pane's highlighted node. On a diff with no tree beside it, such as a whole
  commit entered from the commits pane, `J` and `K` do nothing, and say nothing.
- Not in the files pane itself, where `j` and `k` already move through it.

### The file moved to

- It opens at its top, with the cursor on the bar of its file, as a diff of files does (ADR 60). Not: where it was
  left the last time it was shown, which would keep a place per file.
- An open range (`v`) is dropped, as Escape drops it, and the move goes ahead.

### Help

- `?` in the content panel lists `J` and `K` as next file and previous file, on the diff of a pull request and on
  any other diff, also where they do nothing.

### Why this way

- Moving the tree's highlight, not jumping inside the diff shown: the tree keeps saying which file is shown, and the
  diff is read as it is when a file is highlighted by hand.
- `J` and `K`: free in the content panel, in both scopes and among the global keys, and the larger step of `j` and
  `k`, which move a line there. Not `n` and `N`, which move to remarks; nor `Ctrl+J`, which many terminals send as
  Enter.

## Consequences

- The highlight of the files pane is view state in `FileTreeList` (ADR 2): moving it from the content panel needs a
  way in that does not take the focus from the content panel.
- The plain style gets keys besides scrolling for the first time; `ScrollArea` routes `J` and `K` without a cursor.
- Moving through the files of a whole commit entered from the commits pane, section by section, is left to a later
  decision.
