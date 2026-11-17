# 144. Filters that hide hunks of a diff

Date: 2026-11-17

## Context

Reviewing a diff, much of what changed is not worth reading: imports added and removed as the code around them
changed, the year of a copyright header, a lockfile, generated code. Reading past it costs as much as reading the
change itself, and in a large pull request it adds up.

`Ctrl+W` already leaves out changes to whitespace only (ADR 76), through git's `--ignore-all-space`. Git also has
`-I<regex>`, which drops a hunk whose changed lines all match: the right rule, but one regex for every file, in POSIX
ERE, and only at the time the diff is read.

The diff of the files pane is read lazily: the tree is built from `--name-status`, and only the files of the node
highlighted are diffed (`LoadCommitFileDiff`). Whether a file has anything left once filtered is only known once its
diff is read.

## Decision

### The rule

- A filter applies to files by language, the `Language` the lexer tells them apart by (ADR 53), by path glob, or
  both: a file is in its scope when either matches. A filter has at least one of the two.
- It has line patterns, Java regexes that match anywhere in a changed line, without its `+` or `-`, as grep and
  `git -I` do; `^` and `$` anchor. A filter without patterns matches every line: it hides all of a file, a lockfile
  or generated code. Not: a second kind of filter for whole files.
- A hunk is hidden when each of its added and removed lines matches some enabled filter that applies to the file:
  imports and a copyright year in one hunk hide it together. A hunk with any other change shows whole, its imports too.
  Not: hiding single lines, which leaves a hunk that reads as something else than what changed.
- A hunk of a pull request with a review thread on one of its lines is never hidden: a remark hidden is worse than a
  few imports shown.
- With the whole file as context (ADR 76), git gives one hunk a file, which is hidden only when all the file's changes
  match. That follows from the rule, and is not worked around.

### Where it is applied

- When the diff is drawn, not when it is read: `ParsedDiff` keeps every hunk, and the content panel leaves out the
  hidden ones. Toggling a filter changes nothing that was read, and shows at once, without reading again. The filters
  are not part of `DiffOptions`, and git is never given `-I`.
- In every diff the content panel shows: of the working tree, a commit, a stash, a branch, a pull request, and a whole
  commit from the commits pane. Nothing is staged from a hidden hunk, so hiding one in the working tree is no trap.
- In the rich and the split style (ADR 139), on both sides alike. The plain style is git's text as it printed it, and
  is not filtered.
- Not: leaving files out of the tree. That needs every file's diff when the tree is built: a read of the whole diff,
  against the cap of ADR 79, or a `git diff -U0` or `--numstat -I` pass of every file, which a large pull request
  pays for on every open. The lazy reads stay as they are.

### What shows instead

- A hidden hunk joins the gap between the hunks around it (ADR 77), whose bar says the gap holds filtered changes.
  Expanding it shows the hidden hunk's lines as the changes they are, from the parsed diff, between the lines read from
  the whole file. Not: as unchanged context, which they are not.
- A file of a diff of several files, of a directory or the root, whose hunks are all hidden is left out of that diff,
  bar and all.
- The diff of one file whose hunks are all hidden says so, naming the filters: `All changes hidden by filters (Java
  imports)`. An empty panel would look broken.
- The title says `· N hunks filtered` when filters hid something in the diff shown, and `· filters off` when they are
  switched off and would have. It says nothing otherwise, whatever is enabled.

### The keys

- `J` and `K` (ADR 117) step past a file whose hunks are all hidden, once its diff is read, and on to the next, until
  a file with something left or the end of the tree. Each costs the read `J` already does. The tree still lists it,
  and still shows it when highlighted by hand.
- `:` to a line of a hidden hunk (ADR 140) expands its gap and goes to the line.
- Searching (ADR 126) and copying lines (ADR 94) take what is shown: a hidden hunk is found or copied once its gap is
  expanded.

### Choosing them

`Ctrl+F` opens a popup of the filters, anywhere a diff can show:

- Its first row switches all filters off and on again for this session, without changing which are enabled: to see
  everything a moment, as when an import looks suspect. It is on at the start of every session, and not saved.
- Then the filters, enabled or not: the built-in ones by language, the built-in ones by path, and the user's. One that
  applies to the file highlighted says what it hides there: `· 3 hunks here`.
- Space toggles the highlighted row, which applies at once and is saved at once, as the settings panel saves. `/`
  filters the list. Escape and `Ctrl+F` close it.
- The settings panel does not list them: the popup is their one place.

Which filters are enabled is kept in the settings file, `diff-filters.enabled = ["java-imports", …]`, by id: what to
skip in a review holds from one session to the next. Every built-in starts out disabled, so nothing is hidden before
the user asks. Not: for the session only, as ignoring whitespace is.

### Defining them

A filter is a table of an array, `[[diff-filters]]`:

```toml
[[diff-filters]]
id = "java-imports"
name = "Java imports"
languages = ["java"]
lines = ['^import (static )?[A-Za-z0-9_.]+(\.\*)?;$']

[[diff-filters]]
id = "lockfiles"
name = "Lockfiles"
paths = ["package-lock.json", "yarn.lock", "Cargo.lock", "go.sum", "flake.lock"]
```

- `id` is required: it is what `enabled` lists, and what a user's filter replaces a built-in by. `name` is what the
  popup shows, the id when there is none. `languages` names the `Language`s in lowercase; `lines` are written as TOML
  literal strings, so a backslash stays as typed.
- The built-in filters are such a file in the jar, read as the user's are. So the format can say all they say, and a
  new one is data.
- The user's are tables of `config.toml`, as the editors are (ADR 110): only read, never written by the app, and read
  again when the settings file is (ADR 111). One with the id of a built-in replaces it.
- One that cannot be used, with a regex that does not compile, a language not known, or neither `languages` nor
  `paths`, is listed in the popup, disabled, with why, and reported as a setting not valid is.
- Not, for now: filters of a repository, in a file committed to it, which would share them with a team. The format
  is the same, so they can be read later.

### The built-in filters

Each is a filter of its own, disabled at the start:

- Imports: of Java (`import`, `import static`), Kotlin, Scala, Groovy, C# (`using`), C and C++ (`#include`),
  JavaScript and TypeScript (an `import … from` and a `require` on one line), Python (`import`, and `from … import` on
  one line), Go (`import` on one line), Rust (`use` on one line), PHP (`use`), Ruby (`require`) and Swift (`import`).
  Each by its `Language` where the lexer has it, else by glob.
- The `package` line of Java, apart from its imports, as moving a class to another package can matter.
- The year of a copyright or licence header, in any file.
- Lockfiles, by name: `package-lock.json`, `yarn.lock`, `pnpm-lock.yaml`, `Cargo.lock`, `go.sum`, `poetry.lock`,
  `uv.lock`, `Gemfile.lock`, `composer.lock`, `flake.lock`, `gradle.lockfile`.
- Generated files: `*.min.js`, `*.map`, `*_pb2.py`, `*.pb.go`, `__snapshots__/*.snap`.

Not, for now: version bumps, which can matter in a review; files `.gitattributes` marks `linguist-generated`, which
take a read of the attributes; renames without changes, which have no hunks to hide.

## Consequences

- Imports spread over lines, as Python's `from x import (`, a Go `import (` block or Rust's `use a::{`, are not
  matched: their inner lines look like any other. The lexer's tokens could tell them later.
- A file with nothing left still shows in the tree, and is only stepped past by `J` and `K` once read.
- A gap's bar can say it holds changes, which a gap of ADR 77 never did.
- `Ctrl+F` is taken, anywhere a diff shows.
