# 66. The actions of a pull request in two columns

Date: 2026-10-31

## Context

The popup of a pull request (ADR 19) lists what can be done with it under its details: eleven actions, one per line,
in a `ChoiceList`, the list of options that fifteen popups share. The details always take the lines of the fullest
pull request, eight checks listed among them, so the popup keeps its size when they arrive. Together that is a popup
of some forty lines, of which the actions take eleven, each far narrower than the popup.

A `ChoiceList` is one column: Up and Down move the highlight to the next option, skipping headings, and stop at either
end; Home and Page Up go to the first, End and Page Down to the last, and the wheel moves three. A letter chooses its
option. It can group options under headings, and filter them with `/`. Neither the popup of a pull request uses.

## Decision

### Where

- Two columns are an option of a `ChoiceList`, which a popup turns on. Only the popup of a pull request does. Not: a
  layout of that popup alone, nor every list in two columns: the others are short, and a list to pick from reads
  best down one column.
- The popup gets the lines it saves back: it is that much shorter. Not: more checks listed in them; eight is what the
  popup was sized for, and the checks popup lists them all.

### The layout

- The options fill the first column down, then the second, in the order they are given: the order to read them is
  the same as in one column. The first column has half of the options shown, the one more of an odd number; an
  option left out does not count. For the pull request: open, comment, edit, comments, files and draft on the left;
  the three merges, close and refresh on the right. Not: across, row by row, which mixes what reads a pull request
  with what changes it. Not: a break between columns that the popup places; one can come when a list needs it.
- Each column is half the width of the popup, its highlight as wide; a column does not grow with its widest option,
  so the right one does not move when an option's text changes, as a merge's does in a stack (ADR 24).
- When the widest option does not fit in half the width, the list is one column again. The options of a pull request
  fit in the popup of 80.
- Headings and the filter are refused with two columns, as two options with one letter are: a heading would cut a
  column, and filtering would move the options from one column to the other as the filter grows. They can come when a
  list needs them.

### The keys

- Up and Down move in the order to read: down from the bottom of the first column to the top of the second, and up
  back again. At the very first and very last option they stop, as in one column. Home, End, Page Up, Page Down and
  the wheel are as in one column too.
- Left and Right go to the same row of the other column; Right from the bottom of the first, when the second is a row
  shorter, to the last option. Left in the first column and Right in the second do nothing. Not: `h` and `l`, which
  may be the letters of options.
- An option that does not apply can be highlighted, but not chosen, as in one column.

### Delivery

1. This ADR, two columns in `ChoiceList`, and the popup of a pull request in them.

## Consequences

- The popup of a pull request is five lines shorter, so it fits in a terminal of fewer lines.
- A list in two columns has neither headings nor a filter.
- The arrows to the sides mean something in a list: to the other column. In one column they still do nothing.
