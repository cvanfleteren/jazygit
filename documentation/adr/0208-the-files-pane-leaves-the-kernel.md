# 208. The files pane leaves the kernel, and the resolving marks its files through slots

Date: 2026-11-26

## Context

ADR 158 put the files pane in `features-changes`, beside the commits and stash panes, but it stayed in `kernel-ui`
(`ui.files.FilesPanel`), and `LeftColumn` built it itself. It was the last pane the kernel built, and it drew a
feature's state: the files resolved since an operation stopped, with a `✓` after each, and `· 2 of 4 resolved` in its
title (ADR 198), which are midway's. The content panel, in `kernel-content`, called the pane for how a change is named,
why none of its files is listed, and whether the filter keeps none; the features' dialogs used its id.

One class drew four trees: the changed files of the working tree, all its files, the files of a commit, and all files
of a commit, with the filter, the lock and the keys around them, in 819 lines.

## Decision

- **The pane moves to `changes.panes.files`**, with `FileTreeList`, `FollowedFiles` and `Unguided`, which nothing else
  uses. The app gives `LeftColumn` a `FilesLeftPane`, a `LeftPane` that also steps to another file without the focus
  (ADR 117, 207) and lists what the pane in use highlights (ADR 141), as the other panes are given.
- **What the content panel says of the files listed stays the kernel's**, in `ui.files.ListedFiles`: how a change is
  named, why none of the files of a pull request is listed, which files the filter keeps, and whether it keeps none.
  Its id is `PaneId.FILES` for everything outside the pane.
- **The pane declares two slots** (ADR 172), in `FilesPane`: `MARK`, a `FileMark` that gives what follows the name of a
  file of the working tree and whether its row is dimmed, and `TITLE`, what its title says after what it is. Every
  feature's is shown, in their order. The resolving, in midway, fills both.
- **Enter on a file in conflict needs no slot**: it is the resolving's binding on `Scopes.FILES` already (ADR 200),
  which the pane asks before it opens the diff, as for any feature that binds Enter.
- **The review threads of a file stay drawn by the pane**: they are the kernel's `RemarksByPath`, which names no forge
  (ADR 171), and a second forge's remarks are drawn the same way. So are the lists of only some files of a pull request
  (`CommitFilesView.Listed`), which the kernel's state and the content panel read too.
- **The pane is split by what it lists**: `WorkingTreeFilesList` and `CommitFilesList`, two trees each, which share
  `FileTreeList`, the keys around them (`TreeKeys`) and what tells their nodes apart (`FileNodes`). `FilesPanel` keeps
  the filter, the lock and the focus, and draws the list that is shown in a `ListFrame`.
- **Its texts move with it**: the keys only the pane names are `changes.panes.files.*`, the resolving's
  `midway.panes.files.resolved`; those the kernel names too stay in `kernel-ui`.

## Consequences

- The kernel builds no pane: `LeftColumn` is given every pane of the repository screen.
- With the resolving off, its marks and its count do not show (ADR 171).
- A feature that marks the files of the working tree, or adds to the title of the pane, fills a slot and edits no
  pane.
- Each list keeps where its highlight was as it switches between its changed files and all of them on its own, where
  the pane kept one for both.
