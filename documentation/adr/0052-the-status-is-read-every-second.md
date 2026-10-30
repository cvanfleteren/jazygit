# 52. The status is read every second

Date: 2026-10-30

## Context

ADR 15 made the tick come every 750ms. On it, an idle app runs one git process: `git status --porcelain=v2
--untracked-files=all`, which walks the whole working tree. Profiled idle on this repository, that is 50 to 60ms
of git a tick, and with the frames drawn for a third of what they cost before, the largest part of what an idle app
spends outside the JVM: 1 to 5s of CPU a minute, depending on what else touches the repository.

## Decision

- The tick comes every second instead of every 750ms (`Timers.REFRESH_INTERVAL`); `jazygit.refreshInterval` still
  sets it, as the acceptance tests do.
- Not: watching the working tree for changes, which takes a watch per directory, or reading less often while the
  terminal does not have the focus. Either may come later; this one is a number.

## Consequences

- An idle app runs git a quarter less often.
- A file changed outside the app shows up to a second after it was written, instead of 750ms.
- The ages do not move with the tick, which the clock wakes every second (ADR 21).
- The backoff of a read that failed counts ticks (ADR 31): it now waits up to 64s instead of 48s.

Amends ADR 15 and ADR 31.
