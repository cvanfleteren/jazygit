# 89. How far the checks are

Date: 2026-11-07

## Context

The checks popup (ADR 32) says in words how far a commit's checks are: how many failed, run and passed, `done in ~3m`,
when they started and how long they run (ADR 37), and for each check how long it runs and is expected to take still.
That is all there, but it has to be read; how far along they are does not show at a glance.

A bar that fills up shows it at a glance, but a bar that is full while a check still runs says what is not true. When
the checks are expected to be done is an estimate (ADR 32), and checks often take longer than it.

TamboUI has a `LineGauge`, but the popup draws its lines as rows of styled text, which its tests read off the screen.

## Decision

The checks popup shows a bar of how far the checks are together, across the dialog under when they started, and a
smaller one after each check that is not done. A bar fills with time up to 95% when the checks are expected to be
done, and goes past that only slowly, never reaching 99%. It is full only once GitHub says they are done.

### Shown

- On a line of its own after when the checks started and ended, before the blank line:

  ```
  ✗ 1 failed · ● 2 running · ✓ 7 passed · done in ~3m
  started 14:02:10 · running 4m 31s
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━──────────────────────────────────────  58%

  ✗ lint                                     failed after 1m 12s
  ● acceptance                running 1m 35s ·       ~2m 5s left  ━━━━━━━━────────
  ◌ deploy-preview            queued 2m      ·       ~3m 5s left  ────────────────
  ✓ build                                                2m 41s
  ```

  The part done is a thick line, the rest a thin dimmed one, then how far that is in whole percents, rounded down.
  The dialog is one line higher, so it still lists 16 checks.
- The bar of a check, 16 columns, comes after how it is doing, two spaces apart. Only checks that are not done have
  one. A queued one is empty, and one of which how far it is is not known is dashed, `╌╌╌╌`. The bars of the checks
  take room only while one is not done; once they all are, the names get it back.
- The popup is 100 columns wide, not 80, so the names keep as much room as before next to the bars. Other popups
  are 110 wide.
- Not: the bar as a `LineGauge`. Text rows keep the popup one list of rows, styled by the themes' classes and read by
  its tests as the rest is.
- Not: the bar in the pull request dialog nor the branches pane, which have no room for it. They can get it later.

### How far

- From the start of the checks (ADR 37) to when they are expected to be done together (ADR 32), the bar goes from 0
  to 95% as the time goes by.
- Past that, it goes on as `95% + 4% × (1 − e^(−late / expected))`, where `late` is how long past it is and `expected`
  how long they were expected to take. So it still moves, slower the later it is, and it stays under 99%: the label
  says 98% at most.
- Only when they are all done is it full, 100%.
- While when they are done is not known, as before the history is read, or when only commit statuses are left, it is
  95% of the share of the checks that are done.
- A check that runs goes from its own start to when it is expected to be done, in the same way. A queued check is at
  0, a done one has no bar.
- It does not go back. When the checks are read again and turn out to be expected later, the bar keeps how far it
  had come and fills more slowly from there. The popup keeps that, with the start of the checks it was for, so the
  bar starts again from 0 when the checks start again: a re-run, or the checks of a new commit. Only the bar
  together keeps it; a check's own bar can go back when it is expected later, which is rare.

### Colours

- While the checks run, the part done takes the new class `checks-progress`, in each theme the colour of a pull
  request: blue in most.
- Once one failed, `checks-failed`, also while others run: that one failed is what one most wants to know. Once all
  passed, `checks-passed`.
- More than a minute past when they were expected (`GRACE`, ADR 32), `checks-running`, yellow in most themes, the
  moment the counts say `taking longer than usual`. A check's own bar does the same once it is overdue.
- While when they are done is not known, the bar together is dimmed, as it then counts checks instead of time.

## Consequences

- Built in one step.
- `CheckProgress` in `jazygit-gh` tells how far the checks are, beside `Eta` and `CheckSpan`; `ProgressBar` draws a
  bar.
- Tested: 0 to 95% with time, slowing past that without reaching 99%, full only when done; the share done while it is
  not known when; a check from its own start, a queued one empty, one not known with no bar; the bars on the screen,
  98% long past the estimate and 100% when done; each colour; the bar together not going back when the checks are
  expected later, and starting again when they start again; and the rows lined up in the wider popup.
