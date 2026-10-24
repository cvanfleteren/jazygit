# 29. Unseen pull requests

Date: 2026-10-24

## Context

New pull requests in the open repository only show on the pull requests screen, and only once it is looked at: the
screen reads its lists when it is shown, with `r`, and on a `PrOverviewMsg.Tick` every minute, but only while it is
shown (`PrOverviewUpdate`). Working on the main screen, there is no telling that someone opened one.

The screen's first pane is "This repo" (`PrOverview::repository`): the first 100 open pull requests of the
repository, by when they were last updated (`REPOSITORY_PRS_QUERY`). A `PullRequestSummary` has no `createdAt`.
The `PRs` tab is in the top title (`StatusPanel`).

## Decision

It shows how many pull requests of the open repository were opened and not seen yet: visible, but not in the way.

### What is new

- Only pull requests of the open repository, opened since. Not new commits, comments or review requests on ones
  that were there.
- Drafts count. Pull requests by the user do not: the login is the repository's `jazygit.githubUser`, otherwise
  the active `gh` login.
- PR numbers only go up, so each repository keeps the highest number read. A pull request above it is new; each
  read raises it, and puts the new numbers in an **unseen** set. Not: new as "a number not read before", as a
  comment on an old pull request brings it into the 100, and would make it new.
- The first read of a repository only sets the highest number: nothing is unseen, else every open pull request
  would be.

### Seen

- A pull request is seen once its row is highlighted, in any pane: also the highlight on the first row when the
  screen opens, and scrolling past. Its preview showed it. Not: opening it with Enter, which leaves the marks too
  long.
- One that is closed, merged, or no longer in the list read leaves the set: only open ones count.
- No key to mark them all seen, for now; it fits in later without changing the model.

### Shown

- The tab in the top title: `PRs ●2`, in the accent colour, only when there are any.
- On the pull requests screen, a `●` before the number of each unseen row, in every pane it is in.

### Read while the screen is hidden

- The tick reads "This repo" also while the pull requests screen is hidden: one `gh api graphql` a minute, behind
  the `reading` guard. The other two lists are read only while it is shown, as before.
- A tick about 3 seconds after a repository is opened, at startup and when switching to another one, so the count
  does not wait a minute. Not at once: it would compete with the fetch at startup (0017) and the first read.
- A read that fails keeps the highest number and the unseen set as they were.

### Kept

- The highest number and the unseen set are kept per repository, by `owner/name`, in `state.toml`, next to the
  recent repositories. Pull requests opened while the app was closed are new at the next start.

## Consequences

- A repository with more than 100 open pull requests drops an unseen one that falls out of the 100, as though it
  were closed.
- "This repo" is read every minute for as long as the app runs, not only while the screen is shown.
- `state.toml` is written when the set changes: after a read with new pull requests, and when one is seen.
- Tested by unit tests of the update (new above the highest number, the first read, seen on highlight, closed ones
  leaving, the user's own left out, the early tick), and an acceptance test showing the count on the tab.
