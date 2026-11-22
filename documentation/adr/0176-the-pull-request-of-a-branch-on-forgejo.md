# 176. The pull request of a branch on Forgejo

Date: 2026-11-22

## Context

ADR 174 brought Forgejo as a forge of its own, showing how the builds of a branch went, and left its pull requests
for later. ADR 175 gives each forge its own pull-request dialogs, alike by convention, on the keys GitHub uses, and
moves to `kernel-ui` what a second dialog takes of GitHub's.

Forgejo's API (as Gitea's, `/api/v1`) lists the pull requests of a repository
(`GET /repos/{owner}/{repo}/pulls?state=open`, a page of at most 50 by default), gives one
(`/pulls/{index}`: its state, `draft`, `mergeable`, `merged`, its author, labels, comments, additions, deletions,
changed files, requested reviewers, head and base with their `ref`, `sha` and repository, when it was opened and last
changed) and its reviews (`/pulls/{index}/reviews`: each one's `state`, `APPROVED`, `REQUEST_CHANGES`, `COMMENT`,
`PENDING` or `REQUEST_REVIEW`, and whether it is `stale` or `dismissed`). It gives no count of the commits of a pull
request, anywhere. The builds of its head are the commit's combined status, which ADR 174 reads.

## Decision

- **First, a dialog that shows the pull request of a branch**, with two actions: `o` opens it in the browser and `R`
  reads it again. Merging it, closing it, commenting on it and marking it ready or a draft come each in a change of
  its own, merging first, with Forgejo's styles and its merge once the checks succeed.
- **The open pull requests of the home (ADR 177) are read into Forgejo's part**, by the branch of a remote they
  merge (`ForgejoPullRequests`), when Forgejo turns on, after a fetch and after a push, as GitHub reads its own. A
  page of 50 at a time, at most 10 pages. A read that fails keeps those read before, and says why when one is asked
  for. Not: asking for the pull request of a branch only when `o` is pressed, as the list answers at once, knows when
  there is none, and can show on the branches later.
- **A pull request's branch is matched by its head** (ADR 177): its branch, in the remote whose repository on the
  home's host it is in. A pull request from one's fork into the project, the fork being `origin` and the project
  `upstream`, is found; one from someone else's fork, whose branch has the same name, is not.
- **`o` on a branch shows the pull request that merges the branch it is pushed to** (`Branch.pushTarget`: its
  upstream, else the branch of its name on `origin`), on the key GitHub uses (ADR 175). A branch no open pull
  request merges is told so in the command log; creating a pull request on Forgejo is a change of its own. Before
  the list is read, or when it could not be, the error says so, as GitHub's does.
- **The dialog shows the rows of GitHub's that Forgejo gives**, labelled, in the same order, with the same fixed
  height (ADR 175): the title; the state, with "· draft"; the author; the branch, `head → base`; when it was created
  and last updated (GitHub's "pushed"); the changes, `+/−` and the files, without commits; whether it merges, for an
  open pull request that is no draft, as "yes" or "no", as Forgejo says no for a draft, while it checks for conflicts
  and for one merged or closed, so that a conflict is not known; where the review stands; the reviewers asked; the
  comments; the labels; and the builds of its head: how many failed, run and passed, then the failed and the running
  ones by name. Not shown, as Forgejo has none of them: the
  stack, auto-merge, the review threads, the bar of the checks and when they are expected to be done.
- **Where the review stands is each reviewer's last review that approves or asks for changes, not dismissed**:
  changes requested when one asks for them, else approved when one approves, else none yet. The reviews are read a
  page of 50 at a time, as the pull requests, so that the last of each is read on a long pull request.
- **Reviews or builds that cannot be read leave the pull request shown without them**, and the log says why; a pull
  request that cannot be read shows why, and one read again that fails stays shown, marked not up to date.
- **The browser is `$BROWSER`, else the desktop's** (`xdg-open`, `open`, the URL handler on Windows), started as
  gh's browser is (ADR 85). Not: fj, which jazygit does not need to show pull requests.
- **The review symbols and a check by its name are every forge's** (`ReviewSymbols`, `CheckSymbols.named` in
  `kernel-ui`), as Forgejo's dialog draws them (ADR 175).

## Consequences

- A branch on Forgejo shows its pull request with `o`, with no setup beyond `fj auth login` for a private
  repository.
- Each fetch reads the open pull requests of the home once, a page per 50 of them, and an empty page after
  the last: a host may serve fewer a page than asked, without saying so.
- The fork workflow finds the pull request of a branch: it is the project's, its branch in one's fork.
- No acceptance test covers it: Forgejo is found only over https (ADR 174), which the acceptance tests do not
  serve. The feature's tests run its update, commands and dialog on a Forgejo of the test's.
