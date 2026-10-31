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
- Drafts count. Pull requests by the user do not: the login is the account the list is read as, which the same
  query asks GitHub for (`viewer { login }`): the repository's `jazygit.githubUser`, otherwise the active `gh` login.
- PR numbers only go up, so each repository keeps the highest number read. A pull request above it is new; each
  read raises it, and puts the new numbers in an **unseen** set. Not: new as "a number not read before", as a
  comment on an old pull request brings it into the 100, and would make it new.
- The highest number is the one of the pull request opened last, in any state, which the same query asks for
  (`latest: pullRequests(first: 1, orderBy: CREATED_AT)`). Not: the highest of the open ones, which lags when the
  newest were merged or closed, and is 0 when none is open: one reopened above it would be new.
- The first read of a repository only sets the highest number: nothing is unseen, else every open pull request
  would be.

### Seen

- A pull request is seen once its row is highlighted, in any pane: also the highlight on the first row when the
  screen opens, and scrolling past. Its preview showed it. Not: opening it with Enter, which leaves the marks too
  long.
- One that is closed, merged, or no longer in the list read leaves the set: only open ones count.
- No key to mark them all seen, for now; it fits in later without changing the model.

### Shown

- The tab in the top title: `PRs ●2`, only when there are any. A click on the count shows the screen, as one on
  its name.
- On the pull requests screen, a `●` before the number of each unseen row, in every pane it is in, in place of
  the space after its age, so the rows do not shift.
- Both in a class of their own, `.unseen`: the colour of the notices, in bold, in every theme.

### Read while the screen is hidden

- The tick reads "This repo" also while the pull requests screen is hidden: one `gh api graphql` a minute, behind
  the `reading` guard. The other two lists are read only while it is shown, as before.
- A tick about 3 seconds after a repository is opened, at startup and when switching to another one, so the count
  does not wait a minute. Not at once: it would compete with the fetch at startup (0017) and the first read.
- A read that fails keeps the highest number and the unseen set as they were.

### Kept

- The highest number and the unseen set are kept per repository, by `owner/name`, in `state.toml`, next to the
  recent repositories, in a table `[unseen-pull-requests."owner/name"]`. Pull requests opened while the app was
  closed are new at the next start.
- The list read brings what was kept for its repository along; the model goes by its own once it has it.
- Writing it keeps what else the file has; the recent repositories, which wrote the file anew, now do so too.

## Consequences

- A repository with more than 100 open pull requests drops an unseen one that falls out of the 100, as though it
  were closed.
- "This repo" is read every minute for as long as the app runs, not only while the screen is shown.
- `state.toml` is written when the set changes: after a read with new pull requests, and when one is seen.
- Tested by unit tests of the update (new above the highest number, the first read, seen on highlight, closed ones
  leaving, a reopened one not new, the user's own left out, the tick while hidden), of the state file, of the
  title and the rows. The acceptance tests run without GitHub, so none shows the count.

Amended by ADR 58: the new pull requests are read with the open pull requests the branches show, after every fetch,
rather than by a tick of their own while the screen is hidden or 3 seconds after a repository opens.

Amended by ADR 63: the unseen pull requests are kept in a file per repository, `repos/<owner>/<name>/unseen-pull-requests.toml`.
