# 44. A nicer commit diff, with a tree of its files

Date: 2026-10-29

## Context

What a commit or a stash changed is shown as the text `git show` (or `git stash show`) prints: a `--stat`, then the
unified diff, cut off after `CliDiff.MAX_LINES` lines (`commitChanges`, `stashChanges`). The content panel colours
it line by line (`ContentPanel.diffLine`): `+`, `-`, `@@` and the file headers. It shows git's noise between the
files (`diff --git`, `index`, `---`, `+++`), no line numbers, and not which words of a changed line changed. A big
commit is cut off before its last files, and nothing lets the user look at one of its files only.

The files pane already shows the changed files of the working tree as a directory tree (`FileTree`, `FilesPanel`),
whose highlighted node's diff the content panel shows: a directory shows the files directly in it
(`FileTree.directEntries`).

`wanted.md` (11) plans word diffs and side-by-side diffs as more fields of `DiffOptions`.

## Decision

### Two switches: how a diff is drawn, and which part is shown

- How a diff is drawn is a **diff style**: `PLAIN`, git's text as it is shown today, or `RICH`, the nicer view.
  Ctrl+D switches it anywhere outside a dialog, as Ctrl+W does whitespace. It is kept from one session to the next,
  in the settings (`ui.diff-style`). Not: a field of `DiffOptions`, which says how git *reads* a diff; the style only
  says how it is drawn.
- Which part of a commit is shown is the **file tree** of the commit, opened with `F`: one file, or the files of one
  directory, in whichever style is chosen. Not: the tree being the nicer view, which would leave no way to read a
  whole commit in it.

### The rich view

- It draws what a commit, a commit of a divergence, a commit of a branch's commits and a stash changed. The diffs of
  the working tree stay plain: they are to be staged hunk by hunk later (`wanted.md` 1).
- The diff is parsed into files, hunks and lines (`UnifiedDiff`, in `git.model`), a pure function tested on its own.
  What it does not know is kept as a line of its own, so nothing git prints is lost.
- Each file starts with a bar: what happened to it and its path, like `modified  src/Foo.java`, or
  `renamed  old/Foo.java → src/Foo.java`. It is drawn reversed in the `diff-header` colour, as wide as the widest line
  of the file. Git's own header lines are not shown. A file without lines to show says why under its bar:
  - a binary file: `binary file, not shown`;
  - a file whose mode only changed: the bar says `mode 100644 → 100755`;
  - a file renamed without changes: `renamed, contents unchanged`.

  A submodule shows as git prints it, `-Subproject commit …` and `+Subproject commit …`, in a hunk.
- Each line has a gutter with its number in the old and in the new file, as wide as the largest number of the file,
  dimmed. A long line scrolls sideways, as in the plain view, gutter and all. Not: wrapping, which the content panel
  does not do for any diff.
- The changed words of a changed line are highlighted (`WordDiff`, in `git.model`, pure):
  - In each block of removed lines followed by added lines, the first removed line is paired with the first added
    one, and so on. A line left without a partner gets only the colour of its line.
  - A pair is split into words, runs of whitespace and single other characters, and the longest common subsequence
    of those is kept. The rest is highlighted, but only when at least half of the characters of the pair are kept:
    a line that was rewritten would be highlighted all over.
  - What git notes in a block, like `\ No newline at end of file` after a changed last line, does not end it.
  - The comparisons run where the diff is drawn, so a whole diff shares a budget (`WordDiff.BUDGET`, two million
    cells of the comparison tables). Once it is spent, the lines left get only the colour of their line. Not: a
    limit per pair only, which let a diff of thousands of long, similar lines hold the screen up for seconds.
- The diffs that are parsed are read with git's own `a/` and `b/` before their paths (`--src-prefix=a/
  --dst-prefix=b/`), whatever the user's `diff.noprefix`, `diff.mnemonicPrefix`, `diff.srcPrefix` or
  `diff.dstPrefix` say. Not: `--default-prefix`, which needs git 2.41.
- A changed line has a tinted background (`diff-added-line`, `diff-removed-line`), and its changed words a stronger
  one (`diff-added-word`, `diff-removed-word`); its text keeps the colour of its kind. The line numbers are
  `diff-line-number`, the bar `diff-file-header`. Every theme has these; `mono` and `none` reverse the changed words
  instead of colouring them. A changed line is padded to the width of its file, so the background makes a block.
- A whole commit shows its header and message as before, then git's `--stat`, then one section per file. A file or
  directory of the tree shows its sections only.
- A merge is diffed against its first parent, `--diff-merges=first-parent`: what it brought into its branch, as a
  revert takes it (`help.commits.revert.details`). The plain view keeps git's combined diff. As the style decides how
  a merge is read, it is part of how git reads a diff: `DiffOptions.firstParent`, which `Preferences.diffOptions()`
  derives from the style. Switching the style reads the diffs shown again, as Ctrl+W does, and what was read with the
  other style is dropped when it comes in (`Cmd.DiffLoad`).

### The tree of a commit's files

- `F` on a commit of the commits pane, of a divergence, of the commits of a branch, or on a stash, shows the files it
  changed in the files pane, in place of the files of the working tree, and moves the focus there. The pane is
  titled `Files of abc1234` (or `Files of stash@{0}`). Not: listing them in the pane the commit is listed in, as
  lazygit does, which each of those panes would have to do on its own; nor a tree in the content panel, which takes
  the width of the diff.
- It moves as the files pane does: the highlighted file or directory shows in the content panel, Enter moves the
  focus into it and Escape there back to the tree. A directory shows the files directly in it, as in the working
  tree. Each file shows the letter of its change (`A`, `M`, `D`, `R`), in the colour of a staged change. Not: the
  counts of lines added and removed, which the `--stat` of the whole commit has, and which take the width of a narrow
  pane.
- Escape in the tree closes it: the files pane shows the working tree again, and the focus goes back to the pane the
  tree was opened from, whose highlight did not move. Giving the focus to another pane closes it too, so the files
  pane never shows one commit while another is highlighted. Not: keeping it open on its commit, nor having it follow
  the highlight, which is what the whole commit shown already does.
- The files are read with `git show --name-status -z -M --diff-merges=first-parent`, or `git stash show
  --include-untracked --name-status -z -M`. The diff of a node is read when it is highlighted, through a `Preview`
  with a slot of its own: `git show <sha> -- <paths>`, with both paths of a rename. Its line limit is its own. Not:
  cutting the diff of the whole commit into files, which loses the files after its first `MAX_LINES` lines.
  `git stash show` takes no paths, so the diff of a stash's files is `git diff <stash>^1 <stash> -- <paths>`, and that
  of its untracked files `git diff <empty tree> <stash>^3 -- <paths>`, when it has some.
- With whitespace ignored, the tree still lists every file: `--name-status` does not leave out the ones with
  whitespace changes only, and the tree does not change when Ctrl+W is pressed. Such a file says that only whitespace
  changed in it.
- The tree only shows. Checking a file out as it was in the commit, opening it in the editor, or reverting the
  changes of one file are for later (`wanted.md`).
- `FileTree` is generic over what has a path (`Pathed`): the `FileEntry`s of the working tree, and the `CommitFile`s
  of a commit, which have a path, a change and the path a rename came from.
- A commit is known by its sha, so the tree stays open when the commit leaves the log, after a rebase or a reload. A
  stash dropped while its tree is open closes it, as Escape does: its name would now be that of another stash.

## Consequences

- A diff in the rich view is parsed and its words matched once each time it is shown again, not on every frame: the
  content panel keeps the text it styled until the body changes.
- The rich view of a merge shows other changes than the plain view: those against its first parent only.
- While the tree is open, the files of the working tree cannot be seen nor staged; Escape is one key away.
- The tree has no actions yet; `wanted.md` lists them.

Amended by ADR 47: the diff style draws the staged and unstaged changes of the working tree too, and a conflicted
file shows under a bar of its own.

Amended by ADR 50: a diff is parsed and its words compared where it is read, not where it is drawn.

Amended by ADR 51: a pair that could not keep enough to be highlighted, whatever the order of its words, is drawn
whole and spends none of the budget.
