# 107. The parts of state depend one way

Date: 2026-11-11

## Context

`state` held over a hundred files in one package: the Elm core (`Model`, `Msg`, `Update`, `Cmd`, `Program`) and every
part of the model with its messages, updates and commands. Only the pull requests screen had a package of its own,
`state.prs`. A file's package said nothing of what it belonged to, and nothing kept the parts from reaching into each
other.

Three things tie the parts together:

- A message applies itself (`Msg.apply(Model)`), and the `Model` holds every part. Whatever holds a part and its
  messages needs the `Model`, and the `Model` needs it.
- Data held the reads that fill it: a `Version` held its `ReadVersion`, an expansion its `ReadWholeFile`, and the
  views made their own reads (`CommitFilesView.load()`). A read knows where in the `Model` it puts what it read
  (`Target`, a lens into it), so data, read and `Model` formed a loop.
- A few lookups sat on the side they were used from, not on the side they read, such as `Version.in(model, ...)`.

## Decision

`state` keeps the core; each part of the model is a subpackage of it, with its data, messages, updates and commands:
`diff`, `view`, `load`, `github` (with the pull requests screen in `github.prs`), `operation`, `index`, `popup` and
`preferences`.

### One way

- The parts depend on each other one way only: `diff` ← `view` ← `load` ← `github` ← `operation` ← `popup`, with
  `index` and `preferences` below `load` and `github`. `ArchitectureTest.stateIsFreeOfCycles` checks it.
- The core is no part. It uses them all and they all use it, as the `Model` holds them and they work on it.
- `load` sits above `view`: a read writes what it read into the view, through its `Target`. An update that starts
  reads is in `load` too (`SelectionUpdate`, `CommitSearchUpdate`).
- What two parts both need goes to the lower one, or to the core when it reads several parts: `Changes` and
  `BaseTip` to `diff`, `GitHubBases` to the core.

### Data, not reads

- A part keeps what a read is about, never the read. A `Version` and an expansion keep the `WholeFile` they show, and
  `ReadVersion.of(area, file)` and `ReadWholeFile.of(area, file)` make the read of it again; the area is the key they
  are kept under.
- A read is made from the data it reads for (`LoadCommitFiles.of(view)`, `SearchCommits.of(search)`), not by it.

### Not

- Not: the core layered as well, never using a part. The `Model` names the type of every part, so each part would
  split in a package of data below the core and one of messages and updates above it, and `Jobs`, `GitHubState` and
  the popups could hold no command of a part. That undoes the part as one package, for which the features were made
  to own their messages.
- Not: `load` below `view`. Each read would need a way to report into the view without naming it.
- Not: one package per kind (all messages, all updates). What changes together would sit apart.

## Consequences

- New state code goes in the part it belongs to; a part that needs a higher one is a sign the code sits in the wrong
  place, and the build says so.
- A part's data is plain values: it can be built and compared in a test without a read.
- Members that cross a part are public, where they were package-private in one package.
