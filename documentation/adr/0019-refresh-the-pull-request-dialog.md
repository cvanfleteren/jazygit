# 19. Refresh the pull request dialog

Date: 2026-10-22

## Context

The pull request dialog (`PullRequestDialog`) read the details of its pull request once, when it opened. Left open
to watch the checks or the reviews, it went out of date, and the only way to see the latest was to close it and open
it again. The pull requests screen already reads its lists again: every minute while it is shown, and with `r`.

## Decision

The dialog reads its pull request again, both by hand and by itself.

- **By hand**: `R` (Refresh), the last of its options. `r` is taken by Rebase and merge. It is disabled while the
  first read, or another refresh, is running.
- **By itself**: a `PullRequestMsg.Tick` every `FETCH_INTERVAL` (a minute), like the pull requests screen. It only
  reads again while the dialog itself is the open popup, not while it is kept under the comments or a comment
  opened from it.
- Both re-run `LoadPullRequestDetails` for its number; only the details, not the list of pull requests. Reading the
  details already updates the pull request on its branch (`GitHubState.withRead`).

### While reading again

- The details stay shown, and the options keep working; the title gets a spinner: `Pull request #42 ⠋`. The
  "Reading the pull request from GitHub…" placeholder is only for the first read, so the dialog does not flicker
  every minute.
- `PullRequestPopup` keeps this as `refreshing`, next to `details`.

### When reading again fails

- Details that were read stay shown, and the title says they are stale: `Pull request #42 · not up to date`. The
  failure is kept as `refreshFailed`, and cleared by the next read that succeeds.
- When there were no details yet, as the first read failed, the new failure replaces the old one, as before.
- No error is raised: a failing tick would raise one every minute.
