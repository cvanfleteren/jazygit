# 15. Check the git directory before reading the branches, the stashes and the worktrees

Date: 2026-10-22

## Context

Once ADR 14 stopped the frames, an idle app still took 7 to 10% of a core. Every 500ms tick ran four git processes:
the status, the branches, the stashes and the worktrees. That is some 8 processes a second, about 3% of a core in
git itself and 1% in reading what it writes. Of the four, only the status changes while the user edits files. The
other three change when a ref, a HEAD, the config or a worktree changes, and git writes those as files.

## Decision

- The tick comes every 750ms instead of 500ms.
- On a tick, the branches, the stashes and the worktrees are not read. `Cmd.CheckMetadata` stamps the files they are
  read from instead (`GitInfoProvider.metadata`, `CliMetadata`), and runs no git. A stamp holds when each file was
  modified, its size and its file key (inode), and none for a file that is not there:
  - branches: the HEAD of this worktree, of the main one and of every linked one (which branch is checked out
    where), the config (upstreams), `packed-refs`, `reftable/` and the directories under `refs/heads` and
    `refs/remotes`;
  - stashes: `refs/stash`, its reflog, `packed-refs` and `reftable/tables.list`;
  - worktrees: what the branches look at, as a worktree shows the commit its branch is on, and `worktrees/` with
    each one's `gitdir` and `locked`.
- Of the loose refs, only the directories are stamped. Git writes a ref to a lock file and renames it into place,
  which changes the directory it is in. A repository with many loose refs has far fewer directories.
- A file system keeps modification times only so precisely: a few milliseconds on ext4, a second or two on FAT and
  many network mounts. Two ref updates in one directory within that time, with a check in between, would leave it
  looking the same, and the second would never be read. So a stamp with a file modified less than 2 seconds before
  it was taken (`CliMetadata.SETTLED`) tells nothing, as git does not trust such a file in its index either: its
  load runs, and keeps no stamp. An idle repository's files are older than that, so the saving stays; right after
  a change, a load runs on every tick for those 2 seconds.
- `Jobs.readAt` keeps, for each guarded load, the stamp it was last started with. Only a load whose stamp differs
  starts. It is left out of `Model.drawn()`, like the other jobs.
- A load that still runs when its stamp changes is not started, and keeps its old stamp. It may have read before
  the change, so the next check looks again.
- A load that fails forgets its stamp, so the next tick tries it again, as before. A check that fails starts all
  three. A provider that cannot look at the files (`metadata()` is none, as with the test stub) reads all three on
  every tick, as before.
- An operation that is done, the end of a fetch and the start read everything at once (`Update.refresh`). They do
  not wait for a stamp: a check already queued may have looked before a background push or fetch wrote its refs.
  So the next tick reads the guarded loads once more, as their stamp is older than that read.

## Consequences

- An idle app runs one git process per tick instead of four, and ticks 1.3 times a second instead of 2.
- What changes the branches without changing a stamped file is only seen at the next change that does. For
  example, a worktree whose directory is deleted outside git (prunable) shows only after something else changes.
- The status is still read on every tick. Telling working tree changes from files would take watching the whole
  tree.
