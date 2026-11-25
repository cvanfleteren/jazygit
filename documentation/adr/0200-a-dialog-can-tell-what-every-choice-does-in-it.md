# 200. A dialog can tell what every choice does in it, and Enter resolves a file in conflict

Date: 2026-11-25

## Context

A dialog of choices (`ChoiceDialog`) shows one line per option, `(o) Keep main's version`, and, when its options tell
what choosing them does, that of the highlighted one only, in a box of its own under the dialog (`WithDetails`). That
keeps a long list short, as the branch actions or the settings are, but in a dialog of three or four choices that
each do something hard to undo, the user reads one at a time, moving the highlight to compare them.

The mockup of resolving conflicts drew the resolve dialog otherwise, and the user liked it: every option with what it
does under its label, dimmed, and the git command it runs under that, fainter; the letter in the accent colour in a
column of its own; the highlight across all the lines of an option; a blank line between options and a little room
inside the border.

In the files pane, `enter` opened the diff of the highlighted file, as `→` does, and the resolve dialog was on `r`
(ADR 198), which had to be learnt.

## Decision

1. **A dialog can show every option with what it does**, when its `Spec` asks for it (`inline()`). Each option then
   takes several lines:
   - the letter, in the class `choice-key` (the theme's accent; bold where a theme has no colours), and the label after
     it, one column in from the border;
   - what choosing it does (`withDetails`), wrapped under the label, aligned with it, dimmed;
   - the command it runs, if it says (`withCommand`), under that, in the class `choice-command` (dimmed, italic);
   - a blank line before the next option.

   The highlight covers all the lines of the option. An option that does not apply is struck through and dimmed, its
   text under it dimmed as any. No box under the dialog: what it would show is already in it. The filter, the
   headings and the hint work as before. What is behind the dialog is dimmed already (`backdrop`), as for any popup.
2. **Every other dialog is drawn as before**: a dialog asks for it, none does by default. A long list would grow two
   to four times as tall.
3. **The resolve dialog asks for it**, and says the command of each option: `git checkout --theirs -- <path> && git add
   <path>` for keeping a side, `git rm -- <path>` for a side that deleted it, `$EDITOR <path>` for editing it. Not yet:
   the question staging asks of a file with markers (ADR 198) and `M`, which stay as they are until this is seen in
   use.
4. **`enter` on a file in conflict opens the resolve dialog**, in the files pane; `→` still opens its diff, and
   `enter` on any other file or a directory does as before. `r` stays. This amends ADR 198, which kept `enter` for the
   diff.

## Consequences

- The resolve dialog is about three times as tall as before: four lines or so per option instead of one.
- Every theme gets two classes, `choice-key` and `choice-command`.
- `enter` means two things in the files pane, by what is highlighted; a file in conflict is marked so already (`UU`),
  and the keys under the panes say what `enter` does on it.
