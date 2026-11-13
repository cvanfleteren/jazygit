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
- Every file the tree holds, in its order, also those in a collapsed directory: collapsing a directory tidies the
  tree, it does not take its files out of the walk. The directories leading to the file moved to open, so its row
  shows highlighted, and stay open when the highlight moves on, as when the user opens them by hand.
- What the tree leaves out is left out: the files a filter hides (ADR 105), or the files without remarks (ADR 57).
- In the mode of all files (ADR 108), the files without changes are files of the tree too, and are walked as well.
- From a directory or the root, which show several files at once, `J` moves to the first file under it, or after it
  when it holds none, and `K` to the last file before it.
- On the last file `J`, and on the first `K`, does not move, and says there is no next or previous file. It does not
  wrap around to the other end: in a long pull request that loses where one is.

### Where it works

- In every tree of the files pane: the working tree and the files of a commit, a stash, a pull request or a branch.
- In both styles of the diff, rich and plain. Which file is next is the tree's, not the cursor's.
- On what the content panel shows of a file without changes in the mode of all files (ADR 108): its content, or what
  it says in place of it, as for a binary file. A walk does not stop on the first file without a diff.
- Only when what the content panel shows is the files pane's highlighted node. On a diff with no tree beside it, such
  as a whole commit entered from the commits pane, `J` and `K` do nothing, and say nothing.
- Not in the files pane itself, where `j` and `k` already move through it.

### The file moved to

- It opens at its top, with the cursor on the bar of its file, as a diff of files does (ADR 60). Not: where it was
  left the last time it was shown, which would keep a place per file.
- An open range (`v`) is dropped, as Escape drops it, and the move goes ahead.

### Help

- `?` in the content panel lists `J` and `K` as next file and previous file, on the diff of a pull request and on
  any other diff, also where they do nothing.

### Where it lives

- The highlight of the files pane, and which directories are open, stay view state in `FileTreeList` (ADR 2). The
  Model gets nothing new.
- `ContentPanel` is given a callback beside `leaveScrollArea`, by `JazygitApp` through `FocusKeeper`, which `J` and
  `K` call with their direction. As `leaveScrollArea` does, it asks `ContentPanel.paneOfArea` which pane the focused
  area shows the content of. Only for the files pane does it go on: `FilesPanel` moves the highlight of the tree it
  shows, opening the directories on the way, and says whether it moved. The focus does not move.
- The highlight moved sends what a highlight moved by hand sends, `SelectionMsg.FilesSelected`,
  `UnchangedFileSelected` or `CommitFilesSelected`, and the content is read as it is then. Nothing records where the
  content shown came from: the area that has the focus says it.
- Which file is next or previous in a tree, with its filter, is a pure function of the files, tested on its own.

### Why this way

- Moving the tree's highlight, not jumping inside the diff shown: the tree keeps saying which file is shown, and the
  diff is read as it is when a file is highlighted by hand.
- A callback between the views, as Escape already gives the focus back to the pane through `leaveScrollArea`, not a
  Msg: the update would have to move a highlight the Model does not hold.
- `J` and `K`: free in the content panel, in both scopes and among the global keys. Not `n` and `N`, which move to
  remarks; nor `Ctrl+J`, which many terminals send as Enter. The comments dialog uses them otherwise: there `J` and
  `K` scroll the body of the comment a line, while `j` and `k` move between comments.

## Consequences

- The plain style gets keys besides scrolling for the first time; `ScrollArea` routes `J` and `K` without a cursor.
- A walk through a large tree in the mode of all files opens every directory it passes through.
- Moving through the files of a whole commit entered from the commits pane, section by section, is left to a later
  decision.
