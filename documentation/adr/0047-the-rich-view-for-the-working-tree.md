# 47. The rich view for the working tree

Date: 2026-10-29

## Context

ADR 44 brought the rich view of a diff: a bar for each file, the line numbers in the old and the new file, and the
changed words of a changed line highlighted. Ctrl+D switches it, and it is kept in the settings (`ui.diff-style`).
It draws what a commit or a stash changed, but the diffs of the working tree, the staged and unstaged areas of the
files pane, stayed plain (`ContentView.diffAreas`, `Body.Diff`): "they are to be staged hunk by hunk later
(`wanted.md` 1)".

The diffs of the working tree are read by `CliDiff.diff`: `git diff --cached` for the staged changes, `git diff` for
the unstaged ones, with the untracked files marked intent-to-add in a copy of the index, so git shows them as
`new file`. They are read without `CliDiff.PREFIXES`: with `diff.mnemonicPrefix`, git puts `c/`, `i/` and `w/`
before their paths, which `UnifiedDiff` would read wrong.

While a merge or a rebase stops on a conflict, git shows an unmerged path as a combined diff in the unstaged changes,
and as one line in the staged ones:

```
diff --cc f
index 189acaa,1ed2302..0000000
--- a/f
+++ b/f
@@@ -1,2 -1,2 +1,6 @@@
  a
++<<<<<<< HEAD
 +B2
```

```
* Unmerged path f
```

`UnifiedDiff` only starts a file at `diff --git `, so such a path ends up as lines of the file before it, or in the
preamble when it comes first.

## Decision

### One switch for every diff

- The diff style draws the staged and the unstaged changes of the working tree too: Ctrl+D switches every diff git
  reads. Not: a setting of its own for the working tree. `PLAIN` stays the default.
- This lifts ADR 44's reason to keep them plain. Staging hunks and lines (`wanted.md` 1) will build on the parsed
  `UnifiedDiff`, in either style: its hunks and line numbers are what a range is picked from. So `UnifiedDiff` keeps
  enough of git's text, its headers and its `\ No newline at end of file`, to make a patch out of again.

### Reading

- The diffs of the working tree are read with `--src-prefix=a/ --dst-prefix=b/` (`CliDiff.PREFIXES`) in both
  styles, as those of a commit and a stash are. Switching the style only draws them again, it does not read them.

### Drawing

- An untracked file is drawn as an added one: its bar says `added  path`. The files pane already marks it `??`.
  Not: a bar of its own, which needs the untracked paths where the diff is drawn.
- `UnifiedDiff` also starts a file at `diff --cc ` and at `* Unmerged path `. Such a file's bar says
  `conflicted  path`; its hunks are shown as git prints them, without line numbers and without changed words. Not:
  a full drawing of a combined diff, with a gutter for each parent, for a state that lasts until the conflict is
  resolved (`wanted.md` 2); nor drawing a whole area plain once it has a conflict.
- The staged and the unstaged areas are parsed and drawn each on their own: each has its own budget of
  `WordDiff.BUDGET`, its files their own gutter widths, and each says it is cut off after `CliDiff.MAX_LINES` lines,
  as now. Their titles, and the messages when there are no changes or only changes to whitespace, stay as they are.
- With whitespace ignored, a file of the working tree with changes to whitespace only is left out of the diff, as
  now: when a directory is highlighted, it is not listed. Not: saying so under its bar, as the tree of a commit does
  (ADR 44), which needs the diff checked against the files pane.

### Delivery

1. This ADR.
2. `UnifiedDiff` reads conflicted files.
3. The diffs of the working tree are read with `CliDiff.PREFIXES`.
4. The staged and unstaged areas are drawn in the chosen style, with an acceptance test that switches to the rich
   view with a staged, an unstaged and an untracked file. `wanted.md` (11), the help of Ctrl+D, the javadoc of
   `Settings.diffStyle` and `ARCHITECTURE.md` say the style holds for the working tree too.

## Consequences

- Ctrl+D changes every diff git reads; that of a pull request, read with `gh pr diff`, stays plain.
- A conflicted file shows in the rich view as git prints it, under a bar, and no longer runs into the file before it.
- A diff of the working tree with `diff.mnemonicPrefix` set shows `a/` and `b/` in the plain view too.
