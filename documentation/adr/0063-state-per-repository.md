# 63. State per repository, and threads stay collapsed or open

Date: 2026-10-31

## Context

The threads and comments `Space` collapsed or opened against their default (ADR 62) are kept with the tree as long as
it is open: closing the files of a pull request, or quitting, loses them, and a long review is folded again each time.

What jazygit keeps by itself is in two files: `state.toml`, with the repositories opened last and a table of the unseen
pull requests per repository (ADR 29), and `check-timings.toml`, with a table per repository of how long its checks
took (ADR 32). Every change reads a file whole and writes it whole, under a `.lock` next to it. `state.toml` is a few
hundred bytes; `check-timings.toml` grows with the repositories and their checks, 88 KB already, and is written each
time checks are done, by every jazygit running, whatever repository each has open.

## Decision

### A directory per repository

- What belongs to one GitHub repository is in a directory of its own, `repos/<owner>/<name>/` next to `state.toml`, a
  file per concern: `check-timings.toml`, `unseen-pull-requests.toml` and `collapsed.toml`. Each file has no table
  per repository any more. Neither owner nor name can hold a `/`, so they are safe as a path.
- The key is `owner/name`, as the tables are now: clones and worktrees of one repository share it. Not: the host in
  the path too, which only GitHub Enterprise would need; it is a change of the path once that comes.
- `state.toml` keeps what is not of one repository: the repositories opened last.
- A directory stays when its repository is no longer opened: a few small files are not worth a sweep.
- The lock stays `static synchronized` across all files in the JVM: a jazygit has one repository open, so its writes
  are to that repository's files and `state.toml`, and hardly wait for each other. The split is for two jazygits on
  two repositories, which no longer lock or write the same file, and for a write being of one small file.

### No migration

- The new files start empty. `check-timings.toml` and the `unseen-pull-requests` tables of `state.toml` are deleted
  once, at startup. Without check timings, the next read tops them up from GitHub (ADR 32); without unseen pull
  requests, the first read takes them all as seen (ADR 29). Not: copying them over, code run once that guards next to
  nothing.

### Collapsed or open is kept

- The ids `Space` flipped against their default are written to `collapsed.toml` of the repository, under the number
  of their pull request, and read again when the files of that pull request open. Only the flips are kept: the cursor
  and the focus stay view state.
- Each id is kept with whether it was collapsed by default when it was flipped. When it is read again, an id whose
  default is no longer that is dropped: a thread opened, then resolved while jazygit was closed, starts collapsed, as
  ADR 62 has it for one resolved while it is open.

  ```
  [42]
  touched = 2026-10-31T10:00:00Z

  [42.flipped]
  "PRRT_kwDO…" = true
  "IC_kwDO…" = false
  ```

- Each `Space` writes the file at once, as a command; the update does not wait for it. A write that fails is logged,
  and what is in memory stays what is shown. Not: writing when the tree closes, which a crash loses, nor debouncing,
  needless for a file this small.
- A pull request's flips are dropped once none of them changed for 30 days, at each write of the file. Nothing is
  dropped when a pull request is merged or closed: jazygit reads only the open ones, and the files of a merged one
  can be opened from its branch (ADR 61).

## Consequences

- A thread folded out of the way stays so when the files of its pull request open again, for 30 days.
- A write of state rewrites one small file of one repository, and two jazygits on two repositories never wait for each
  other.
- Upgrading forgets how long checks took until they are topped up again, and which pull requests were unseen.
- Amends ADR 29 and ADR 32, whose state moves to a file per repository, and ADR 62, whose flips outlive the tree.
