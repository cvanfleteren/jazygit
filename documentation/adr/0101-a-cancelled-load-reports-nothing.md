# 101. A cancelled load reports nothing

Date: 2026-11-10

## Context

ADR 31 gave `Cmd` a `skipped()`: what a command reports when a newer one for its slot cancelled it before it started.
A `Load` reported `LoadMsg.Skipped`, to take it out of `refreshing`. ADR 33 then made `Program` report every end of a
command as `Msg.Done(id, Option<Msg>)`, and `Msg.Done` takes the command out of what runs, whatever it reported.
Since then `LoadUpdate` handled `LoadMsg.Skipped` by changing nothing: it was the only `skipped()` there was, and it did
what reporting nothing does.

## Decision

- `Cmd.skipped()` and `LoadMsg.Skipped` are gone. `Program` reports a command that a newer one for its slot cancelled
  as `Msg.Done(id, none)`. It is still no failure, and the backoff of a read that failed before stays.

## Consequences

- `Cmd` holds only what `Program` asks of every command: `run`, `failed`, and how it is scheduled (`background`,
  `label`, `slot`, `guard`).
- A command that has to tell the model it was cancelled would bring a way to say so back.
