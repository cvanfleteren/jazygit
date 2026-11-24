# 187. A source heads what another shows

Date: 2026-11-23

## Context

ADR 186 has the content panel show the commit of the highlighted reflog entry as for any commit, and with it what the
entry is: its selector (`HEAD@{3}`), when HEAD was moved to it, its whole subject, and whether HEAD is at its commit or
the commit is lost. A row has room for little of that.

The commit is drawn by `CommitSource` in `kernel-content`, with the header, message and diff of `ContentText`. A
feature cannot use `kernel-content`: no feature draws the content panel (ARCHITECTURE.md). The reflog could not draw
the commit itself without the diff drawing that lives there, and a source of its own that answered for the commits pane
would have to.

What the reflog has to add is only a few lines above a commit the kernel already shows. The same holds for what other
features may say of what another shows: a tag above the commit it names, a note of a stack above a branch's log.

## Decision

- **A `ContentSource` can put a `Heading` above what is shown next to a pane**, whichever source shows it:
  `ContentSource.heading(model, pane)`, none by default. `ContentView` asks every source for one, in the order of the
  sources, once the first source that answers has given what is shown, and puts them above its first area, the first
  source's on top. A message gets none. A gated source heads nothing while its feature is off.
- **A `Heading` is a value**, lines of text each in a class of the theme or plain (`Heading.Part`), in `ui.content`
  beside `Body`, so a feature builds one without the kernel's drawing.
- **`Headed(heading, body)` is the body with the heading above it**: a `Body` that draws the heading's lines before the
  body's, as part of its lead, and is the body otherwise (its diff, keys, hunks; a click below the heading is the
  body's, one on it nothing). A body whose drawing starts with a lead, as a commit's header, takes the lines into it
  (`Drawn.headed`); one without gets them as its lead.
- **The reflog heads its entry's commit**: `ReflogSource` shows nothing and heads the commits pane while the reflog tab
  is shown, the highlighted entry is known (`ReflogMsg.Highlighted`) and the commit shown is that entry's.

## Consequences

- The commit of an entry is drawn by the kernel as for any commit, and the reflog says what the entry is above it.
- A source that only heads declares no areas and shows nothing; the content panel asks every source for a heading on
  each draw, which costs little as most say none at once.
- The heading is put above the first area only: what is stacked below is not headed.
