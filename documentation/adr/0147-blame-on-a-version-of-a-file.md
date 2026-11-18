# 147. Blame on a version of a file

Date: 2026-11-18

## Context

A version of a file (ADR 102) shows the whole new file or its original in place of its diff, read as a diff of the
file with the whole file as context, always with whitespace not ignored. Its lines are tinted where the diff changed
them, but nothing says when the other lines were written, by whom, or in which commit. For that the user leaves
jazygit for `git blame`, tig or GitHub.

`git blame --porcelain <rev> -- <path>` gives, for each line of the file at `<rev>`, the commit that last changed it,
its author, its author time, its summary, and the commit and path before it (`previous`). A version is the text of one
blob, so its lines are those blame reads, one for one. A diff is not: each hunk would need two blames, of the parent
and of the commit, lined up with its context lines.

`git blame` follows a file renamed whole, but takes a line moved within a file, or from another file, as written by the
commit that moved it. `-M` looks for a moved block in the same file, in the parent, for the lines a commit added; it
costs a comparison with one file. `-C` looks in the other files the commit changed too, and `-C -C -C` in every file
of every commit: on a large repository that is many comparisons for each commit that touched the file. `-w` ignores
whitespace when it compares, so it changes whom a line is blamed on, never its text.

jazygit gives git no input on stdin (`Exec`); a file it hands git is written with `TempFiles.using`, as for
`fmt-merge-msg` and the copy of the index of `CliDiffs`. A read of git is stopped after 30 seconds; a load that is
superseded is not stopped, its result is dropped once it holds for nothing (`Targeted`). The files of a pull request
fetch its head and base when they are missing, from the remote of its repository (`CliPullRequest.fetched`), with
their history; nothing reads blame from GitHub. `F` opens the files of any commit by its sha
(`CommitFilesMsg.Opened`); the log knows only the 3000 commits it read (ADR 130), and nothing moves its highlight to
a commit by its sha. `y` copies the lines of a version (ADR 102); copying a sha was left for later (ADR 94, #291).
Enter in the diff shows the lines a marker leaves out, opens a node of a version rendered (ADR 122), and shows the
hunks a filter hides (ADR 144): one binding, which does nothing on a line.

## Decision

### Where it applies

- A version shown as source can show, beside its lines, who last changed each of them: its blame. Not: the diff,
  whose two sides would be blamed at two revisions and lined up with its context; that is for an ADR of its own. Not:
  a version rendered (ADR 103), which has no lines. A version is drawn unified in the split view (ADR 139), so it is
  blamed there as anywhere.
- `b` turns blame on and off for the area the cursor is in, in the diff (`Scopes.DIFF`, `Scopes.PULL_REQUEST_DIFF`),
  and in the files pane and the files of a commit (`Scopes.FILES`, `Scopes.COMMIT_FILES`) for the area showing the
  file highlighted, as Ctrl+O and Ctrl+T do. On the diff, a version rendered, the plain view, and wherever Ctrl+O does
  nothing, `b` does nothing. `?` lists it with the other keys of the diff. Not: a message saying why.
- Blame is kept with the version, as whether it is rendered is (`Version`): Ctrl+O to the other version shows that
  one blamed too; Ctrl+T on a blamed version renders it without blame, and Ctrl+T back shows it blamed again; a version
  read again after a save is blamed again. Once the area shows another file, it is dropped. Not: a setting to blame
  every version, which blames a file whose code is all that is wanted.

### What is blamed

- Each version is blamed at the revision its lines are of:

  | Source                                 | New version                     | Original                          |
  |----------------------------------------|---------------------------------|-----------------------------------|
  | A commit, or a file of its files       | `<sha>`                         | `<sha>^`, the first parent        |
  | A stash, or a file of its files        | `<stash>`                       | `<stash>^1`                       |
  | The files of a branch                  | its tip                         | its base                          |
  | The files of a pull request            | its head                        | its base                          |
  | The working tree, unstaged             | the file on disk                | the index                         |
  | The working tree, staged               | the index                       | `HEAD`                            |
  | A file without changes (ADR 108, 128)  | its commit, or the file on disk | —                                 |

  The original of a merge is blamed at its first parent, as the rich view reads a merge (ADR 44). The original of a
  renamed file is blamed at its old path.
- The index is written to a file with `TempFiles.using` and blamed with `--contents <file> HEAD`. Not: `--contents -`,
  which needs `Exec` to give git stdin, which nothing else needs.
- A pull request is blamed in the repository, at the commits its files fetched. When they are not there, as when no
  remote is of its repository, the blame could not be read. Not: GitHub's blame through GraphQL, which is another
  read of its own for the rare case.
- An untracked file has no history: `b` does nothing on it.
- Blame is read with `-M`, so a block moved within a file keeps its author, and with `-w` while Ctrl+W ignores
  whitespace, as that is how the user is reading the changes. Not: `-C`, which can take minutes on a large
  repository, and a read jazygit cannot stop.
- A version cut where its read is cut (ADR 79) is blamed up to its last line shown: `-L 1,<last>`. Not: blaming the
  whole file, slowest for the files that are cut.

### When it is read

- The first `b` on a version reads its blame; the new version and the original are each read once, and kept with the
  version. The version shows at once, a placeholder in place of the blame, and the title says
  `(reading blame)` until it is read: `Files of a3f9c21 · new version, blamed (reading blame)`.
- A blame that failed shows the version without it, and the title says `(blame could not be read)`. The next `b`
  reads it again. A read stopped after 30 seconds failed: the porcelain of git is not in the order of the lines, so
  what it printed until then is not a blame of the first lines.
- A read that holds for nothing any more, as once the area shows another file, is dropped, as the read of a version
  is.
- The porcelain is read by a pure function in `git.model`, with the `previous` of each commit kept, though nothing
  uses it yet.

### How it is drawn

- The blame is a column before the numbers of the lines: a bar of the age of the commit, its short sha, the initials
  of its author as the commits pane draws them (`CommitsPanel.initials`), and its author date:
  `▌ 9c4d2e8 AM 2026-09-14`. Not: the name of the author, or a relative time, which take more width and the second of
  which goes stale.
- Of the lines of a run from one commit, only the first is labelled; the bar goes on beside the others. The line of the
  cursor shows its label always.
- A line of the commit whose version this is, which is tinted as changed already, is labelled `this commit`, dimmed.
  A line not committed yet, which git blames on a sha of zeros, is labelled `uncommitted`, dimmed, without a bar.
- The bar is shaded by age in five groups: the commits the version is blamed on, newest first, split in five as
  evenly as they go, by rank. Not: by date, from the oldest commit of the file to the newest, where one line of years
  ago puts all others in the newest group. Not: groups of fixed ages, under which every line of a young project is
  as new as every other. The five shades are styles of the theme. A theme without background colours, as `mono`, has
  no bar.
- The column is as the numbers are: it stays where it is when the code scrolls sideways, a search (ADR 126) does not
  look in it, `y` copies the code without it, and a click on it puts the cursor on its line. A review thread under a
  line of a pull request's version is drawn as it is, after the column.
- In an area where the code would have fewer than 60 columns beside the whole column, the column is the bar and the
  sha only, and the title says `(blame shortened to fit)`. Not: hiding the column in a narrower area still, as the
  user asked for it; the code scrolls sideways there, as it does.
- While blame shows, the last row of the area holds the commit of the line of the cursor: its sha, the name of its
  author, how long ago, and its summary: `9c4d2e8 Alex Moreau · 2 months ago · A version cycles diff, new,
  original`. `Not committed yet` for a line that is not. On the bar of the file, the version's only stop that is no
  line, the row is blank; while blame is read it says `reading blame…`; when it failed it is blank, as the title says
  why. Not: the status bar of the application, as a file with staged and unstaged changes shows two areas.

### Acting on a line

- Enter on a line of a blamed version opens the files of its commit where the commits are, as `F` does
  (`CommitFilesMsg.Opened`), with the file of the version highlighted when the commit changed it. It is a part of the
  one binding of Enter in the diff, as a version has no markers. On a line of `this commit`, or not committed yet,
  Enter does nothing. Not: highlighting the commit in the log, which holds only the commits it read, and has nothing
  to move its highlight to a sha.
- Copying the sha of a line is left for the copying of shas (#291): the last row shows it. Not: `Y` for it now, which
  #291 would have to fit in.
- Blaming the file again before the commit of a line, as tig's `,` does, is left for later: `previous` is kept for it.

## Consequences

- A version has a third thing the area holds of it, beside which version and whether it is rendered: whether it is
  blamed, and the blame of each version read.
- The gutter of a version can be wider than the numbers of its lines: what scrolls sideways, what is searched and
  where a click goes start after it.
- `b`, Enter on a line of a version and the last row of an area are taken.
- A blame of the diff, blaming before a line's commit, and copying its sha can come as their own ADRs, on the porcelain
  read here.
