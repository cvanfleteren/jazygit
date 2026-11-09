# 96. The index written anew once it changed

Date: 2026-11-09

## Context

ADR 93 (`0093-the-index-written-anew-now-and-then.md`) writes the index anew every 30th tick while the terminal has
the focus, as the status every tick reads takes no optional locks and never writes back what it refreshed. That fixes
a stale index within 30 s, but it runs a git process every 30 s whether or not anything changed, and after a checkout
the status stays slow for up to 30 s.

The index goes stale in two ways. Files that git writes in the same second as the index, in a checkout, a rebase, a
reset, a pull or a clone, are racily clean; git writes the index in all of those, so it changes. Files that a tool
touches without changing them, a formatter or a build, are stat dirty, and nothing in the git directory changes.

`Cmd.CheckMetadata` already looks at the files of the git directory every tick, without running git, and the stamp of
the diff (ADR 58) looks at the index. A watcher on the index was weighed and left out: Java's `WatchService` only polls
on macOS, and the stamp of every tick sees a change within a second.

## Decision

1. **`GitMetadata` stamps the index too,** and `LoadMsg.MetadataChecked` carries that stamp. As every stamp, it is none
   while the index was written in the last 2 s (`CliMetadata.SETTLED`), so it tells only once it settled, which is
   also when a refresh can write the racily clean files: one in the same second as the checkout leaves them racy.
2. **`IndexUpdate.checked` asks for `Cmd.RefreshIndex` when the stamp differs from the one it last saw, or 5 minutes
   (`IndexUpdate.FALLBACK`) after the last refresh anyway,** for the files that were touched. As before, only while the
   terminal has the focus, and while no operation of the app nor another refresh runs. The first stamp that can be told
   after the app starts differs from none, so it is written anew then.
3. **The refresh's own write is no change.** `Jobs.indexRefresh` keeps what the index looked like and when it was last
   written anew. A refresh that wrote it (`Msg.IndexRefreshed(true)`) has the next stamp that can be told taken as what
   it left, rather than writing it anew again. One that could not, as another command held the index, keeps the stamp
   from before, so the next change, or the fallback, tries again, and a lock that stays does not start a git process
   on every tick.
4. **What the index looked like is not drawn.** `Model.drawn()` leaves `Jobs.indexRefresh` out.

## Consequences

- After a checkout, rebase or reset, from jazygit or from a shell, the status is slow for about 3 s rather than up to
  30 s, and an idle app runs `git update-index` once every 5 minutes rather than every 30 s.
- A file that a tool touches without changing it makes the status slow for up to 5 minutes, rather than 30 s.
- A change of the index within about 2 s of a refresh that wrote it is taken for that refresh's own write; the
  fallback writes it anew within 5 minutes.
- Without stamps (`Status.metadata()` none), the index is never written anew; that is only so when the git directory
  cannot be found.

Amends ADR 93 (`0093-the-index-written-anew-now-and-then.md`).
