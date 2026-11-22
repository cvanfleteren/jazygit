# 181. Merging on Forgejo when the checks succeed

Date: 2026-11-22

## Context

ADR 180 merges a pull request on Forgejo now, on five keys. A pull request that the rules of its base branch hold,
missing approvals or a check, is refused with Forgejo's reason, and the user has to come back once they are met. On
GitHub, the same keys merge when ready through GitHub's auto-merge (ADR 112): a key merges now or when ready, as GitHub
says the pull request is blocked or not; while auto-merge is on, the key of its method cancels it.

Forgejo merges a pull request by itself once the rules of its base branch are met, when it is merged with
`merge_when_checks_succeed` (as of `main` at 6840f006da):

- It answers 201, scheduled, always: also when the rules are met already. Forgejo then merges it only on the next
  event that makes it look again: a passing status of the head, an approving review, a review dismissed, or a check
  for conflicts that finishes, as after a push. A pull request scheduled once all is green waits until one of those.
  (Gitea looks at once.)
- It waits for every rule of the base branch, the approvals too, not only the checks. A branch without a rule is met by
  any of those events.
- `head_commit_id` is not looked at when scheduling; a push does not cancel it, and what is pushed is merged.
- It answers 409 `pull request is already scheduled to auto merge when checks succeed` once one is.
- `DELETE /repos/{owner}/{repo}/pulls/{index}/merge` cancels it, for who scheduled it or may merge: 204, or 404 when
  none is scheduled.
- No field of a pull request says that its merge is scheduled. Its timeline
  (`GET /repos/{owner}/{repo}/issues/{index}/timeline`) has a `pull_scheduled_merge` event, with who and when, and a
  `pull_cancel_scheduled_merge` one; not how it is to be merged. A scheduled merge that Forgejo tried and could not do,
  or a pull request merged by hand, leaves no event: the timeline then still says it is scheduled.
- `mergeable` of a pull request says nothing of the rules of its base branch. `GET /repos/{owner}/{repo}/branches/{b}`
  gives them to anyone who may read it: `protected`, `enable_status_check`, `status_check_contexts`, globs of the checks
  required, and `required_approvals`. Forgejo counts the checks passed as: without contexts, the combined state of all
  of them, which none never are; with them, each glob matching a check and all those it matches passed.

## Decision

- **Each key works out, as it is drawn, whether it merges now or when the checks succeed**, as on GitHub (ADR 112): it
  schedules the merge when the base branch is protected and a check it requires has not passed, or the pull request
  has fewer approvals than it requires; else it merges now, as ADR 180 does. Its label says so, "Squash when checks
  succeed", shorter than the style's own to fit the column. Not: always merging now and offering to schedule once
  Forgejo refused, which takes two presses; nor a key of its own that switches all five, which a pull request that
  is green would be scheduled into, to wait.
- **The rules are read with the pull request** (`ForgejoBranchRules`), on every `o` and `R`, as the reviews and the
  builds are. A rule that cannot be read leaves the keys merging now, and the log says why. The globs are matched as
  Forgejo compiles them, with gobwas' glob without separators: `*`, `?`, `[…]`, `[!…]` and `{a,b}`; any other
  character, in a class too, is only itself. One that does not compile, as a class or a brace left open, is skipped,
  as Forgejo skips it.
- **The approvals are counted from the reviews read**: reviewers whose last review that counts approves. Forgejo counts
  only those of reviewers it trusts, which it does not tell; a pull request may then be merged now and refused, as
  before.
- **The checks are read again just before it is sent**: once they passed meanwhile, it is merged now. Not: sending it
  scheduled anyway, which Forgejo would leave waiting for the next event. When they cannot be read, it is scheduled,
  as was read.
- **Scheduling keeps the dialog open**, as GitHub's auto-merge does: it is an operation on the branch ("Scheduling
  merge"), logged, after which the dialog reads the pull request again, and the pull requests are read again, as after
  any change to the forge. A merge that failed before is forgotten (ADR 180's `FailedMerge`), as a new merge takes its
  place, as on GitHub (ADR 112). One that fails is kept as a failed merge.
- **The timeline tells whether a merge is scheduled**, read with the pull request, a page of 50 at a time, at most 10
  pages: its last scheduling event, when it is not a cancelling one (`ForgejoScheduledMerge`). One that cannot be read
  shows none, and the log says why. jazygit keeps the style of each merge it scheduled, by its pull request, in
  Forgejo's part, while the pull request is open: Forgejo does not tell it. The style is of the merge the next read
  shows scheduled, by when it was, and forgotten once the timeline shows another, as one cancelled and scheduled again
  in the browser.
- **While one is scheduled, every key cancels it**, "Cancel scheduled merge", as its style may not be known. Not: only
  the key of its style, as on GitHub, which leaves nothing to press when it was scheduled in the browser. Another style
  is chosen by cancelling it, then pressing that key. Cancelling keeps the dialog open too ("Cancelling merge"), and
  reads it again.
- **It shows as GitHub's auto-merge does**, in the line of the dialog GitHub's has for the stack (ADR 175):
  `Auto-merge  squash · @anna · 3m ago`, or `scheduled` in place of the style when jazygit did not schedule it, and how
  long ago as the other times of the dialog (ADR 124). No marker on the branches, which Forgejo's pull requests do not
  have yet.
- **What Forgejo answers that differs from what was read is told, and read again**, as no failure: a 409 of a merge
  scheduled already says so; a 404 of cancelling says it was no longer scheduled, as it was merged or merging it
  failed, which also covers a timeline that still says it is. jazygit then keeps when the merge it showed was
  scheduled, and shows none scheduled until the timeline has a later one; else every key would still cancel it.
- **The details of a key say what Forgejo waits for**: the checks it requires and the approvals; and that what is pushed
  meanwhile is merged too, as Forgejo does not look at the head then.

## Consequences

- A pull request on Forgejo that waits for its checks or a review is merged with one key, and needs no coming back;
  quitting jazygit does not stop it.
- The same key merges now on one pull request and when its checks succeed on another, which its label says, as on
  GitHub.
- Each read of a pull request asks Forgejo twice more: the rules of its base branch, and its timeline, a page per 50
  events.
- A merge Forgejo dropped after trying it shows as scheduled until it is cancelled, which then says so, after which
  the keys merge it again; as does a cancelling event the timeline has beyond the pages read.
- No acceptance test covers it, for the reason of ADR 176. The feature's tests run its update, commands and dialog on
  a Forgejo of the test's, and core-forgejo's run the requests against a server of the test's.
- ADR 180 is amended: a pull request its base branch holds is merged when its checks succeed.
