# 132. Older builds on demand

Date: 2026-11-15

## Context

The builds of a branch read its last 100 commits, and on every branch the last 100 runs of a workflow (ADR 131): on
jazygit's main branch, 5 days. Whether the build has been getting slower, or when it started failing now and then,
can take longer than that to tell. ADR 131 left more than 100 for later.

The reading is already done some at a time, each page saying where the next one starts: it stops at 100 only because
it is told to. A call for 25 commits costs about 3 of the 5,000 points an hour, and 2 to 3 s.

## Decision

- `m` in the builds reads 100 more: of the branch, 100 more commits; with every branch shown (`a`), 100 more runs of
  the workflow of the tab. They are read 25 a call, as the first 100 are, from where those read end, and show as they
  come in.
- After reading failed, `m` reads again from where it failed, instead of having to read everything again with `B`;
  the title then says `m to try again`.
- While more can be read, the title says so: `Builds: main · last 100 commits · m for more`. At the oldest commit or
  run, or while reading, it does not, and `m` does nothing.
- Not: reading more once the list is scrolled to its end. The builds are text to scroll, with the summary, the chart
  and the jobs above the list, and each `m` costs points and seconds: it is asked for, not stumbled into.
- Not: reading every commit at once. A branch of years has thousands, read in minutes, and kept in memory as long as
  the builds show.

## Consequences

- How far back the builds go is up to the user; what is read is still not kept (ADR 131), so it is read again each
  time `B` shows them.
- The summary and the chart take in every build read, unless `n` shows fewer (ADR 131).
