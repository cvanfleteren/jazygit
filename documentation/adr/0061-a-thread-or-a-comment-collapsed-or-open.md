# 61. A thread or a comment collapsed or open

Date: 2026-10-31

## Context

A resolved thread starts collapsed to one dimmed line, and a hidden comment to `hidden · reason` (ADR 55). `t` in the
tree opens every thread and every hidden comment, and closes them again. `Space` in the diff of a pull request's files
opens or closes the thread of the cursor against what `t` says (ADR 60).

What "closed" means depends on the thread: a resolved one is one line, an unresolved one is drawn whole, only its
hidden comments folded. So `Space` on an unresolved thread changes nothing on the screen, unless it has a hidden
comment: a long thread one is done reading cannot be put out of the way. And a hidden comment is only shown with the
rest of its thread, or with every thread of the tree.

The cursor stops on each thread as one stop (ADR 60), so a key on a comment of its own has nothing to act on.

## Decision

### One rule

- A thread and a comment are each collapsed or open. Collapsed by default: a resolved thread, a thread whose first
  comment is hidden, and a hidden comment. Open by default: the others.
- `Space` collapses or opens what the cursor is on, whatever it is resolved or hidden: the ids flipped against their
  default are one set, kept with the tree as long as it is open, through `R` too. An id gone after a read again stays
  in it, harmless: the ids of threads and comments are GitHub's node ids, which never clash.
- `t` is gone, and so is `opened` in `CommitFilesView`: a resolved thread is opened one at a time. Not: `t` kept to
  open all, which needs the rule of what `Space` flips against to change with it, the two meanings of closed that this
  ends.

### The first comment is the thread

- The first comment of a thread is the thread: `Space` on it collapses or opens the whole thread, and on any other
  comment that comment only. A thread opened shows its first comment whole, hidden or not.
- What the comments of a collapsed thread are is kept, and shows again once it is opened.

### The cursor

- Each comment of an open thread is a stop of its own: `j`/`k` go from comment to comment. A collapsed thread is one
  stop. The stop of a thread holds its first comment, or the thread when it is collapsed; that of a comment holds it,
  the rule after it, and after the last one what ends the thread.
- A comment is anchored by its id and the anchor of its thread (`Anchor.Comment`): when it is gone, as when the thread
  is collapsed, the cursor goes to its thread, and from there as a thread does (ADR 60).
- `n` and `N` go from thread to thread, to its first stop; `r` and `x` act on the thread of the comment the cursor is
  on.
- `DiffStops` knows which threads and comments are collapsed, so the stops it works out are those drawn.

### Drawn collapsed

- A resolved thread: `✓ resolved · author: first line`, dimmed, as now.
- An unresolved thread: `▸ 3 remarks · author: first line`, not dimmed, so it stands out from the resolved ones; the
  count is of all its comments, those GitHub has more of too (ADR 55).
- A thread whose first comment is hidden says `hidden · reason` in place of the first line: it is hidden for a reason.
- A comment: its header, who wrote it and when, then its first line, dimmed; `hidden · reason` for a hidden one.
- `· sending…` follows a collapsed thread while something is sent for it, as it follows a resolved one now.

### Resolving

- Once GitHub resolved or unresolved a thread, its id leaves the set: it shows as a thread resolved or not starts,
  collapsed once resolved, open once unresolved. Not: the id kept, which would open a thread one collapsed, then
  resolved, as being flipped means open for a resolved one. The ids of its comments stay.

## Consequences

- `Space` does something on every thread and every comment.
- A thread one has read can be put out of the way, and a hidden comment read without opening anything else.
- No key opens every thread at once; `t` is free in the tree.
- The cursor stops on each comment, so a thread of many comments takes as many `j`.
- Amends ADR 55, whose `t` and default states this replaces, ADR 57, whose `t` this removes, and ADR 60, whose thread
  of one stop and `Space` against `t` this replaces.
