# 102. The original or the new version of a file

Date: 2026-11-10

## Context

The rich view (ADR 44, 47) shows what changed in a file: its hunks, with the removed and the added lines among the
lines around them. To read the file as it was or as it became, without the lines of the other side in the way, the
user leaves jazygit for an editor, or for `git show`.

A diff read with the whole file as context (`DiffContext.WHOLE_FILE`, ADR 76) holds both: its unchanged lines with
its removed ones are the original, with its added ones the new version. Showing more of one hunk (ADR 77) already
reads the diff of one file that way, the way that diff was read: the working tree's staged or unstaged changes, a
commit, a stash, or the files of a commit, a stash, a branch or a pull request, with the options of the moment.

But not while whitespace is ignored (`-w`): a line whose whitespace only changed is then a line of context, and git
prints a line of context as it is in the new file. `  b` that became `\tb` shows as ` \tb`, so the original built from
it would hold `\tb`.

A diff is read up to 50 000 lines or 4 MiB (ADR 79); a frame costs the same whatever its length (ADR 78). Reading a
file of about 3000 lines whole takes git a few milliseconds.

## Decision

### What is shown

- An area of the rich view whose diff holds one file can show, in place of the diff, the new version of that file or
  its original: the whole file, read as ADR 77 reads it, and lexed whole. Not: one side of the diff as it is shown,
  with its context and markers, which is not the file.
- A version is always read with whitespace not ignored, whatever Ctrl+W says: ignoring it is how the diff is shown,
  and the original has to be the original. So in a version, a line whose whitespace only changed is tinted as changed,
  as it is: the file changed there. Not: Ctrl+O doing nothing while whitespace is ignored, which Ctrl+W keeps for the
  session.
- The original of a merge is its first parent's file: the rich view reads a merge against its first parent (ADR 44),
  never as git's combined diff.
- The lines that changed keep their tint in a version, and their changed words their highlight while Ctrl+E has them
  on: the added lines in the new version, the removed ones in the original. So a version shows where the changes sit
  in it. Whether the tint stays is to be seen in use. The `+` or `-` before a changed line is blank when the theme
  tints the line, as it says nothing the tint does not, and makes a line of Markdown like `+- item` hard to read; in a
  theme without a background for changed lines, as `mono`, it stays, as nothing else tells the line changed.
- The gutter keeps the two columns of the diff, as wide: the numbers of the new version in the column of the new file,
  those of the original in that of the old one, the other column blank. So switching moves neither the code nor the
  numbers sideways. Not: one column, which shifted the code left as the version showed. It stays as wide when the
  file has more digits than the lines of its diff, as a file of 120 lines changed at line 93: the new version's
  number ends where the gutter does, the original's runs on into the blank column.
- The bar of the file says which version shows: `new version  src/Foo.java`, `original  src/Foo.java`. For a renamed
  file, each names its own path: `original  old/Foo.java`, `new version  src/Foo.java`.
- The title of the area says it too, from the moment Ctrl+O is pressed: `Files of #403 · new version`, `… · original`,
  with `(reading)` after it while the file is read, and `(could not be read)` when it failed. The bar scrolls away
  with the first line of the file; the title does not. Back on the diff, the title is as before.
- A version is cut where the diff read whole is cut (ADR 79), and says so, as a diff does. Not: no limit for a
  version, which brings back what ADR 79 ended for a large file added by mistake.

### The key

- Ctrl+O, in the diff (`Scopes.DIFF` and `Scopes.PULL_REQUEST_DIFF`), cycles the area the cursor is in through the
  diff, the new version and the original. Not: a global key, as the diff options have: a file with staged and
  unstaged changes shows two areas, and only the cursor says which one is meant.
- A version that does not exist is skipped: an added file has no original, a deleted one no new version.
- Where it cannot apply, Ctrl+O does nothing: in the plain view, in an area of several files (a directory, a commit
  or stash of several files, the working tree's areas with a directory highlighted), on a binary file, on a file whose
  mode only changed, or renamed without changes, on a submodule, whose versions are one `Subproject commit` line
  each, and on a conflicted file. `?` lists it as it lists the other keys of
  the diff. Not: a message saying why.
- An area holds one file whatever shows it: a file highlighted in the files pane or a tree of files, or a commit or a
  stash that changed only that file.

### When the file is read

- The first Ctrl+O on a file reads it whole, unless an expansion already did with whitespace not ignored. The diff
  shows until it is read, then the version, and the title says it is being read. A press while it is read moves the
  cycle on, and the area shows where the cycle is once it is read.

### What the version is kept with

- It is kept with the area, as an expansion is (`RepoView.expanded`), while the area shows the same file: read again
  after a save (ADR 58). Toggling whitespace or changing the amount of context keeps it, and does not read the
  version again, as it is read without either. Once the area shows another file,
  it is dropped: coming back shows the diff. Not: kept per path, or for the session, as the diff options are: seeing
  a whole file is wanted for one file, not for the next.
- Back on the diff, it shows as before the switch: its context, its expansions and its markers. The file read whole
  is not unfolded for it.

### The cursor, review threads and copying

- The cursor stays on its line across a switch: a line of the other side goes to where it was in the version shown, a
  removed line to the line after its block in the new version, an added one to the line after its block in the
  original, or to the last line where the block ends the file. The
  line of the cursor keeps its row on the screen as far as the view scrolls, as when a range is opened (ADR 83).
- In a pull request's file, the threads on the side shown are drawn on their lines; those on the other side are not
  shown while a version is.
- A range (`v`) and a new thread are for the diff only: GitHub takes only lines in its hunks. `y` copies the lines of
  the version.

## Consequences

- A version is a view on the file read whole: no other command of git is read for it. While whitespace is ignored,
  that is a read of its own, apart from the one an expansion makes.
- The cursor gets anchors on lines that may be in no hunk; they are named by their side and number, as an expanded
  line already is (ADR 77).
- An area has three ways to show one file, and its state says which.
