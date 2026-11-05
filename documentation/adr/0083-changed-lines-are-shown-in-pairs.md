# 83. Changed lines are shown in pairs

Date: 2026-11-05

## Context

Git gives a block of changed lines as its removed lines, then its added ones. In a block of version bumps:

```
-<checkstyle.version>14.3.0</checkstyle.version>
-<errorprone.version>2.51.0</errorprone.version>
-<native-maven-plugin.version>1.1.17</native-maven-plugin.version>
+<checkstyle.version>14.4.0</checkstyle.version>
+<errorprone.version>2.52.0</errorprone.version>
+<native-maven-plugin.version>1.1.18</native-maven-plugin.version>
```

the eye goes back and forth to find what each line became. No option of git puts them under each other: a hunk
holds every changed line next to another, even with `-U0`, and gives its removed lines first.

`WordDiff` (ADR 44) already pairs the lines of each block: the first removed line with the first added one, and so
on, and highlights the words of a pair that changed. It runs where the diff is read (ADR 50), so `ParsedDiff` holds
the segments of each line. A pair it finds rewritten, or does not compare as its budget is spent, has one unchanged
segment per line; one with as many lines on each side is not paired by anything but its place.

The cursor of the rich view (ADR 60) stops on the lines in the order they are drawn, and a range holds the lines
from where it was opened to the cursor, in the order of the diff: what GitHub covers with a thread from its first
line and side to its last, and what staging lines will take (`wanted.md` 1). A thread on several lines marks the lines
it covers in the gutter, in that same order (ADR 55).

## Decision

- The rich view shows a block of removed lines followed by as many added ones a pair at a time: the first removed
  line, the first added one, the second removed line, and so on. Only when each pair has a word that changed: one
  rewritten, or not compared, means the pairing is a guess, and the block is shown as git gives it. So is a block of
  more lines on one side than the other. The plain view (Ctrl+D) is as git gives it: there is no setting of its own.
- It is so in every rich diff: of a commit, a stash, the working tree and a pull request. Pairs are shown whether the
  changed words are highlighted or not: they are paired by the comparison, not by how it is drawn. A conflicted file
  has no pairs.
- The order is worked out by a pure function in `git.model` (`LinePairs.groups`), from the segments of the lines of a
  hunk: it cuts it into groups shown together, each a pair or a line by itself. `ParsedDiff` keeps the lines as git
  gives them, which ranges, threads and patches are worked out from. Not: the lines of `ParsedDiff` reordered, which
  would put an order of the view in what patches are made from.
- What goes after a line goes after its pair: the threads on either line, and a note of git's, like
  `\ No newline at end of file`. So a pair is never split.
- The hunk a range is open in (`v`) is shown as git gives it until the range is dropped: the lines tinted are the
  lines it holds, which run in the order of the diff, and it reaches no further when extended. The area works it out
  from the stop of the mark, and draws the diff again; the view moves by as much as the line of the cursor moves, so
  that line stays where it was on the screen, as far as the view can scroll. The model learns where the view is with
  the next move of the cursor.
- A block a placed thread on several lines covers is shown as git gives it too, so the gutter marks the lines it
  covers in a run.
- The cursor stops on the lines in the order they are shown; its anchors name a line by its side and its number, so
  a line keeps its anchor whatever its place.
- Not: pairing a block whose sides differ in length by what is left over. Which line goes with which is then a guess
  that reads as a claim.
- Not: interleaving a hunk while a range is open in it, with the lines it holds tinted wherever they are: a range
  from one line to the next would tint lines above and below others it does not hold.

## Consequences

- Opening and dropping a range moves the lines around the cursor in its hunk.
- `DiffLines`, the lines a range is worked out from, stays in the order of the diff: that is how the hunk of a range
  is shown.
- Staging lines later works from the lines as git gives them, through their anchors, whatever order they are shown in.
