# 105. Filtering the files of a tree

Date: 2026-11-11

## Context

`/` filters the list of a popup (`ListFilter`) and searches the commits pane (ADR 4). The files pane has no filter:
in a working tree or a commit with many changed files, finding the ones under a name, or staging all the tests, means
scrolling through the tree and acting on them one at a time.

The files pane shows one of two trees through `FileTreeList`: the files of the working tree, and, while they are
open, the files of other changes (`CommitFilesView.Changes`): of a commit or a stash (ADR 44), a pull request
(ADR 55) or a branch (ADR 61). Both are loaded in full, so a filter needs no git process. A directory, and the root
`/` that holds everything, act on all files beneath them.

## Decision

`/` in the files pane filters its tree, the working tree's and that of the changes open, each with a filter of its
own.

### The match

- As in the popups: in memory, on every key typed, through `ListFilter.matches`. A substring, ignoring case unless the
  filter has an uppercase letter. No prefixes, regex or globs.
- It matches the whole path from the root of the repository, so a directory's name keeps all files under it. A
  renamed file matches by either of its paths.

### The tree

- It is built from the matching files only: it keeps the root `/` and the directories leading to a match, and drops
  the others.
- While filtered, every directory is open, so no match is hidden. A directory can still be collapsed by hand; the next
  change to the filter opens all again. Clearing the filter brings back the directories collapsed before.
- A directory, and the root, act only on the files shown under them: staging `/` with `Test` typed stages the
  matching files only. While filtered, a directory is given to git as its files, not as a pathspec of itself
  (`StageUpdate`), which would take the hidden files too. Its diff shows only those too. A dialog, like that of
  discarding, names what it touches.
- When nothing matches, the dimmed "No files match" of `ListFilter.noMatches()` takes the place of the tree, without
  a root to act on, and the content panel says the same in place of a diff no longer highlighted.
- The filter narrows the files listed (ADR 57). When none is listed to begin with, what ADR 57 says stays, such as
  `No file has unresolved remarks`: the filter is not why the tree is empty. "No files match" is only for files
  listed that the filter all hides.
- The title gets the filter after what it says already: `Files · /test`, `Files of #123 · with remarks · /test`.
  The count of rows counts the rows shown.

### The keys

- `/` opens a `FilterLine` above the tree. Up and Down move the highlight of the tree from the line, as in the popups.
- Enter closes the line and keeps the filter; the keys go to the tree again.
- Escape in the line puts back the filter from before `/`: none, when there was none.
- `/` while filtered opens the line again with the filter in it.
- Escape in a filtered tree clears the filter only. The next Escape does what it did before: it closes the files of
  the changes open (ADR 44), or lets go of the commit copied to cherry-pick.
- While the filter changes, the highlight stays on its node when that is still shown, else it goes to the first
  matching file, not the root. Clearing the filter keeps it on the same path, or on the directory holding it when
  that is collapsed again.

### How long it lasts

- The filter of the working tree stays through reloads and operations, and while the files of other changes are open
  and closed again. Escape or another repository clears it.
- The filter of the changes open goes when their tree closes, or when other changes open. It stays when the same
  changes are read again (`Changes.sameAs`), as a pull request after a force-push (ADR 60), and when `c` changes
  which files are listed (ADR 57).
- Neither is kept for the next session.

### Not changed

- `c` (commit), `A` (amend) and `s` (stash) act on the repository, not on the node, and ignore the filter: the stash
  still takes everything. Stashing only some paths would be a feature of its own.

### Where it lives

- The filter's text, and whether its line is open, are in the Model: the working tree's in `RepoView`, that of the
  changes open in `CommitFilesView`, with their Msgs and update, as commit search has.
- The match of a file is a pure function, tested on its own.
- Which directories are open, and the highlight, stay view state in `FileTreeList`, which rebuilds from the matching
  files when the filter changes.
- `feature.files.filter` declares the `/` keys, as `feature.commit.search` does.

### Why this way

- In memory and as you type, not on Enter: the files are read already, and it is how `/` works in the popups.
- A tree, not a flat list: the directories and the root still act on the files at once, now on those shown.
- Acting on what is shown: the screen says what a key touches, and "stage all that matches" is the reason to filter.
- One Escape at a time: one key should not both clear the filter and close the files of a commit.

## Consequences

- A directory or the root acts on fewer files than it holds while a filter is on; the title shows that one is.
- `FileTreeList` gets the filter, and with it a second reason to rebuild the tree besides new files.
- Escape in the files pane has one more layer.
- Staging and unstaging a directory no longer always hand git the directory: tests cover the root and a directory,
  staged and unstaged, with a filter on.
- While filtered, staging a directory gives git one path per file, as discarding one always did: tens of thousands
  can go over the system's limit on the length of a command. Passing them on stdin (`--pathspec-from-file`) is left
  to issue #410.
