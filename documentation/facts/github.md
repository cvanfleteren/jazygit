# GitHub

What GitHub does that jazygit relies on, learned by testing, reading the docs or measuring. Each fact says when it was
learned, so a fact that has grown old can be checked again; see README.md.

## Pull requests

- GitHub only knows what was pushed: a pull request needs the branch and all its commits on the remote. *Why it
  matters:* jazygit pushes before creating a pull request. — learned 2026-11-22 · [ADR
  0025](../adr/0025-push-before-creating-a-pull-request.md)
- Pull request numbers only go up; `pullRequests(first: 1, orderBy: {field: CREATED_AT})` gives the one opened last, in
  any state, and `viewer { login }` gives the account the query runs as. *Why it matters:* "new" pull requests are those
  above the highest number seen, robust to merges, closes and reopens. — learned 2026-11-22 · [ADR
  0029](../adr/0029-unseen-pull-requests.md)
- A comment on an old pull request moves it into the first 100 open pull requests ordered by last update. *Why it
  matters:* "a number not read before" would wrongly flag old pull requests as new. — learned 2026-11-22 · [ADR
  0029](../adr/0029-unseen-pull-requests.md)
- A pull request's head and base commits are `headRefOid` and `baseRefOid`, which the summary list of open pull requests
  can read; `baseRefName` is its base branch. Review threads and line comments are anchored to those commits. *Why it
  matters:* a branch's pull request files open without reading the details first. — learned 2026-11-22 · [ADR
  0061](../adr/0061-the-files-of-a-branch.md), [ADR 0072](../adr/0072-stacks-in-the-branches-list.md)
- A pull request's `headRefOid` gives the commit its branch is at on GitHub, and `isCrossRepository` tells whether its
  head branch is in a fork. *Why it matters:* these tell whether a stack's branches are up to date without a fetch, and
  leave out fork branches that are not on the remote. — learned 2026-11-22 · [ADR
  0099](../adr/0099-checking-out-a-stack.md)
- A pull request's `commits { totalCount }` is the count GitHub's "Commits" tab shows: for a merged one the commits it
  was merged with, for a stacked one those on top of its own base. *Why it matters:* the commit count comes from the
  details query, not from git. — learned 2026-11-22 · ADR 0026
- `behindBy` exists only for a branch with a pull request, compares with the pull request's base on GitHub, and takes a
  request. *Why it matters:* the behind-base count in the branches list is computed locally with git. — learned
  2026-11-22 · [ADR 0046](../adr/0046-how-far-a-branch-is-behind-its-base.md)
- `baseRef { compare(headRef: "refs/pull/<number>/head") { behindBy } }` gives how far a pull request is behind its base
  in the same query as its details. *Why it matters:* the second details query was dropped. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md)
- GitHub lets collaborators edit others' pull requests, including closed and merged ones. *Why it matters:* the Edit
  action is offered whoever wrote the pull request, whatever its state. — learned 2026-11-22 · [ADR
  0059](../adr/0059-edit-a-pull-request.md)
- The `updatePullRequest` mutation does not change whether a pull request is a draft. *Why it matters:* draft state goes
  through `gh pr ready`. — learned 2026-11-22 · [ADR 0059](../adr/0059-edit-a-pull-request.md)
- GitHub keeps `refs/pull/<n>/head` at the head of every pull request in the base repository, also for one from a fork,
  and serves a commit it has by its sha. *Why it matters:* a pull request's head and base can be fetched without the
  fork's remote. — learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md), [ADR
  0058](../adr/0058-less-reading-in-the-background.md)
- GitHub keeps a pull request's head commit after its branch is deleted (`refs/pull/N/head`), so the commit stays
  readable from GitHub even when it is gone from clones that fetched only main or pruned the branch. *Why it matters:*
  workflow files of builds of squash-merged pull requests are still readable from GitHub. — learned 2026-11-22 · [ADR
  0138](../adr/0138-workflow-files-from-github.md)
- A mirror of the same `owner/name` on another host does not have `refs/pull/<n>/head`. *Why it matters:* the remote
  fetched from must be on the host of the pull request's URL. — learned 2026-11-22 · [ADR
  0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- GitHub's "Files changed" of a pull request is a three-dot diff: the head commit against the merge base of head and
  base, the same diff as `git diff <base>...<head>`. *Why it matters:* the pull request's diff can be made locally with
  git. — learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- For a merged pull request GitHub keeps the diff as it was merged, but `base...head` no longer reproduces it: after a
  merge commit it is empty, and after a squash or rebase it mixes the pull request's changes with what the base got
  since; `baseRefOid` no longer moves. *Why it matters:* the diff with remarks is not offered for merged pull requests.
  — learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)

## Review threads

- GitHub's GraphQL `reviewThreads` on a pull request gives each thread with `isResolved` and its comments; threads come
  oldest first, and `totalCount` stays exact beyond the page read. *Why it matters:* reading `last: 100` gets the
  newest, most likely unresolved threads, and "all resolved" is only claimed when every thread was read. — learned
  2026-11-22 · [ADR 0007](../adr/0007-review-thread-counts.md)
- GraphQL `pullRequest.reviewThreads` gives each thread's path, `line`, `startLine`, `diffSide`, `startDiffSide`,
  `originalLine`, whether it is resolved or outdated, whether it is about a line or a whole file, and its comments
  (author, time, Markdown, whether hidden and why). *Why it matters:* this is everything needed to place remarks in a
  diff. — learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- A review thread is pinned to a line by its number in the old or new file (`diffSide` `LEFT` or `RIGHT`), not to a
  hunk; a thread whose line changed afterwards is outdated and has no `line`, only its `originalLine` in a past commit.
  *Why it matters:* threads are placed by line number and side, and outdated ones are listed at the end of their file. —
  learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- A thread's path is the new path of a renamed file on either side, and the old path of a deleted one; git's and
  GitHub's rename detection and hunk boundaries can differ. *Why it matters:* a remark can fail to place and is then
  listed at the end of its file. — learned 2026-11-22 · [ADR
  0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- The node ids of review threads and comments are global and never clash. *Why it matters:* collapsed or opened ids can
  sit in one set, and stale ids in it do no harm. — learned 2026-11-22 · [ADR
  0062](../adr/0062-a-thread-or-a-comment-collapsed-or-open.md)
- A thread says whether the viewer may reply, resolve or unresolve it (`viewerCanReply`, `viewerCanResolve`,
  `viewerCanUnresolve`). *Why it matters:* keys without the right refuse before asking GitHub. — learned 2026-11-22 ·
  [ADR 0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)
- Comment counts of review threads include pending comments of a review the viewer has not submitted yet; only their
  author sees them. *Why it matters:* leaving them out would require reading the state of every comment. — learned
  2026-11-22 · [ADR 0006](../adr/0006-view-pull-request-comments.md), [ADR 0007](../adr/0007-review-thread-counts.md)
- Comments of a pending (unsubmitted) review are visible only to their author. *Why it matters:* pending comments are
  left out. — learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md), [ADR
  0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)

## Commenting on lines

- GraphQL `addPullRequestReviewThread` and `addPullRequestReviewThreadReply` add a comment to the viewer's pending
  review, visible only to them until submitted, while REST review comments publish at once, like GitHub's "Add single
  comment". *Why it matters:* new threads and replies use REST. — learned 2026-11-22 · [ADR
  0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)
- REST `POST pulls/<n>/comments` takes the head commit, a path, a line and a side, plus a start line and start side for
  a multi-line comment, or `subject_type` `file`; `POST pulls/<n>/comments/<id>/replies` takes the REST id of the
  thread's first comment, which GraphQL exposes as `fullDatabaseId`. *Why it matters:* these are the calls and ids used
  to comment and reply. — learned 2026-11-22 · [ADR 0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)
- GitHub refuses a review thread whose first and last lines are in different hunks. *Why it matters:* a range stays
  within one hunk, and GitHub's refusal is shown when hunks differ with whitespace ignored. — learned 2026-11-22 · [ADR
  0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)
- GitHub only accepts a review comment on lines inside its own hunks, which always have three lines of context. A thread
  on a line jazygit shows through wider context or an expansion can be refused. *Why it matters:* such a refusal is
  shown to the user. — learned 2026-11-22 · [ADR 0076](../adr/0076-the-amount-of-context-in-a-diff.md), [ADR
  0077](../adr/0077-showing-more-of-one-hunk.md)
- A comment range may start on the old side and end on the new side, and GitHub takes a clicked context line as the new
  side (`RIGHT`). *Why it matters:* removed lines map to `LEFT`, added and context lines to `RIGHT`. — learned
  2026-11-22 · [ADR 0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)
- GitHub identifies a context line of a diff by its number in the new file, and a review thread can be started on the
  old side of a context line. *Why it matters:* a context line is a cursor stop by its new-file number in the unified
  view, and on either side in the split view. — learned 2026-11-22 · [ADR
  0139](../adr/0139-the-old-and-the-new-file-side-by-side.md)
- REST does not say which thread a newly posted comment started. *Why it matters:* after starting a thread the cursor
  goes to its line rather than the thread. — learned 2026-11-22 · [ADR
  0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)
- Resolving or unresolving a thread is GraphQL only (`resolveReviewThread`, `unresolveReviewThread`), taking its node
  id. *Why it matters:* `x` uses GraphQL while comments use REST. — learned 2026-11-22 · [ADR
  0060](../adr/0060-a-cursor-in-the-rich-view-to-comment-on-lines.md)

## Comments: the conversation, editing, deleting and reactions

- A review that only commented and has no text is what inline diff comments hang from; pending reviews are visible only
  to their author. *Why it matters:* such reviews are left out of the conversation list. — learned 2026-11-22 · [ADR
  0006](../adr/0006-view-pull-request-comments.md)
- GitHub can hide a comment with a reason (outdated, resolved, off-topic, spam…). *Why it matters:* hidden comments are
  listed dimmed with their reason. — learned 2026-11-22 · [ADR 0006](../adr/0006-view-pull-request-comments.md)
- Bots fill their pull request comments with HTML comments (`<!-- … -->`). *Why it matters:* they are stripped before
  rendering Markdown. — learned 2026-11-22 · [ADR 0006](../adr/0006-view-pull-request-comments.md)
- GraphQL gives each comment `viewerCanUpdate` and `viewerCanDelete`. A repository's admins may delete comments others
  wrote. *Why it matters:* rights are checked without asking GitHub, and the diff can offer `d` to admins. — learned
  2026-11-22 · [ADR 0064](../adr/0064-delete-a-comment.md)
- GraphQL deletes a review comment with `deletePullRequestReviewComment` and a conversation comment with
  `deleteIssueComment`, both by node id. REST deletes them by their REST id. Neither deletes a whole thread: a thread is
  gone once its last comment is. *Why it matters:* deleting goes one comment at a time. — learned 2026-11-22 · [ADR
  0064](../adr/0064-delete-a-comment.md)
- Tested on a test repository: deleting the first comment of a thread with replies keeps the thread under the same id,
  and the next comment becomes its first (its `replyTo` becomes `null`, later replies point at it). A reply sent to the
  REST id of the deleted comment is refused with 404 `Parent comment not found`. Deleting a comment that is already gone
  answers `NOT_FOUND`. *Why it matters:* the next comment becomes the reply target, a reply must not race a delete, and
  `NOT_FOUND` counts as deleted. — learned 2026-11-22 · [ADR 0064](../adr/0064-delete-a-comment.md)
- A submitted review cannot be deleted, so neither can its text. A review without text is gone once all its comments are
  deleted. *Why it matters:* `d` on a review says it cannot be deleted. — learned 2026-11-22 · [ADR
  0064](../adr/0064-delete-a-comment.md)
- GraphQL edits a review comment with `updatePullRequestReviewComment` and a conversation comment with
  `updateIssueComment`, by node id, and answers with the comment as it now is. It does not check whether the comment
  changed since it was read: the last edit wins. *Why it matters:* the body GitHub returns is shown, and an edit by
  someone else can be overwritten without notice. — learned 2026-11-22 · [ADR 0065](../adr/0065-edit-a-comment.md)
- A pull request's description, and the text of a submitted review (`updatePullRequestReview`), can be edited, unlike
  deleted. The conversation marks an edited comment with `includesCreatedEdit`. *Why it matters:* review text could be
  made editable later. — learned 2026-11-22 · [ADR 0065](../adr/0065-edit-a-comment.md)
- GitHub has eight reactions: 👍 👎 😄 🎉 😕 ❤️ 🚀 👀. Anyone who can read a comment can react. GraphQL reads them as
  `reactionGroups`, one per kind, with `reactors.totalCount` and `viewerHasReacted`. *Why it matters:* reactions are
  read with the review threads, at the cost of one field. — learned 2026-11-22 · [ADR
  0086](../adr/0086-reactions-on-review-comments.md)
- `addReaction` and `removeReaction` work by the comment's node id. Adding a reaction twice, or removing one not given,
  changes nothing. *Why it matters:* whether to add or remove is decided from the comment as it is when the reaction is
  chosen. — learned 2026-11-22 · [ADR 0086](../adr/0086-reactions-on-review-comments.md)
- `viewerCanReact` says whether the viewer may react. GitHub refuses reactions on a locked conversation or in an
  archived repository, even from people who can read the comment. GitHub may add new kinds of reaction. *Why it
  matters:* unknown kinds are skipped rather than failing the whole read. — learned 2026-11-22 · [ADR
  0086](../adr/0086-reactions-on-review-comments.md)

## Reviews

- GitHub needs a body to request changes or to comment, but not to approve. *Why it matters:* an empty text approves,
  and a reason is required for a request for changes. — learned 2026-11-22 · [ADR
  0100](../adr/0100-approving-or-asking-for-changes.md)
- GitHub refuses an approval or a request for changes on one's own pull request. *Why it matters:* `a` is disabled on
  the viewer's own PR. — learned 2026-11-22 · [ADR 0100](../adr/0100-approving-or-asking-for-changes.md)
- A viewer has at most one pending review per pull request (e.g. started in the browser). GitHub refuses a new review
  while one is pending, and `addPullRequestReview` does not submit it. *Why it matters:* `a` refuses and tells the user
  to submit or drop the pending review in the browser. — learned 2026-11-22 · [ADR
  0100](../adr/0100-approving-or-asking-for-changes.md)
- Anyone who can read a repository can review its pull requests, but only reviews by those who can write to it count for
  the rules of a protected branch. *Why it matters:* reviewing is not restricted to writers. — learned 2026-11-22 · [ADR
  0100](../adr/0100-approving-or-asking-for-changes.md)
- No GraphQL field says whether the viewer may approve. `viewerDidAuthor` says the PR is the viewer's own, and
  `viewerLatestReview { state }` shows a pending review. *Why it matters:* whether reviewing is allowed is worked out
  from these two fields. — learned 2026-11-22 · [ADR 0100](../adr/0100-approving-or-asking-for-changes.md)
- `addPullRequestReview` takes the pull request's node id, not its number. *Why it matters:* the details and
  review-thread queries read the node id. — learned 2026-11-22 · [ADR
  0100](../adr/0100-approving-or-asking-for-changes.md)
- Pending reviews are managed with `addPullRequestReviewThread`, `addPullRequestReviewThreadReply`,
  `submitPullRequestReview` and `deletePullRequestReview`. *Why it matters:* these are the mutations a future
  pending-review feature needs. — learned 2026-11-22 · [ADR 0100](../adr/0100-approving-or-asking-for-changes.md)

## Merging and auto-merge

- GraphQL `mergePullRequest` refuses a stacked pull request with "GraphQL: This pull request is part of a stack and must
  be merged using the asynchronous merge REST API." *Why it matters:* stacked pull requests cannot be merged with `gh pr
  merge`. — learned 2026-11-22 · [ADR 0024](../adr/0024-stacked-pull-requests.md)
- `PUT repos/{owner}/{repo}/pulls/{n}/merge-async` (with `merge_method` and the head `sha`) merges in the background, a
  stacked one together with the open ones below it; it answers 202 with a merge id, 200 when the outcome is immediate
  (merged, or already in a merge queue), and 409 when a merge of it is already pending. `GET …/merge-async/{id}` reports
  `pending`, `merged`, `enqueued` or `failed` with a message. *Why it matters:* every merge goes through this endpoint
  and is polled until done. — learned 2026-11-22 · [ADR 0024](../adr/0024-stacked-pull-requests.md)
- Passing the head `sha` to `merge-async` makes GitHub refuse to merge if something was pushed since. *Why it matters:*
  a merge never takes commits the user did not see. — learned 2026-11-22 · [ADR
  0024](../adr/0024-stacked-pull-requests.md)
- Nothing in GitHub's answers tells that a merge queue dropped a pull request. *Why it matters:* an enqueued pull
  request keeps showing as enqueued. — learned 2026-11-22 · [ADR 0024](../adr/0024-stacked-pull-requests.md)
- `mergeStateStatus`: `CLEAN`, `UNSTABLE` and `HAS_HOOKS` can be merged now; `BLOCKED` cannot; `DIRTY` conflicts;
  `BEHIND` must catch up with its base first (reported only when the base requires that); `UNKNOWN` is not worked out
  yet and is often reported for a while after a push. *Why it matters:* the merge keys choose between merging now, when
  ready, or being disabled from this. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- GraphQL has `enablePullRequestAutoMerge(pullRequestId, mergeMethod, expectedHeadOid)` and
  `disablePullRequestAutoMerge`. A `PullRequest` has `autoMergeRequest { mergeMethod enabledAt enabledBy }` (null when
  off), `viewerCanEnableAutoMerge` and `viewerCanDisableAutoMerge`, and the repository has `autoMergeAllowed`. *Why it
  matters:* merging when ready is built on these. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- Auto-merge only applies when the repository allows it and the base branch requires a check; without a required check,
  a PR that does not conflict can be merged at once and is never blocked. *Why it matters:* elsewhere the
  merge-when-ready keys have nothing to do. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- Tested on jazygit's repository: `viewerCanEnableAutoMerge` is false on a CLEAN PR, true on a BLOCKED one, and false
  while auto-merge is on. *Why it matters:* a key merges now or when ready, never both. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- Enabling auto-merge on a stacked pull request fails with `Auto-merge is not supported for stacked pull requests.`,
  although `viewerCanEnableAutoMerge` is true. *Why it matters:* stacked PRs keep merging immediately. — learned
  2026-11-22 · [ADR 0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- Enabling auto-merge again with another method while it is on does nothing and reports nothing; the method stays. *Why
  it matters:* changing the method takes cancelling and enabling again. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- `expectedHeadOid` is checked only when auto-merge is enabled; commits pushed afterwards are merged too once the PR is
  ready. *Why it matters:* the head as last read is sent, and later pushes are accepted the way GitHub does. — learned
  2026-11-22 · [ADR 0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- Auto-merge waits for whatever blocks the pull request, including reviews and other base-branch rules, not only checks.
  It does not bring a `BEHIND` branch up to date. *Why it matters:* the label says "when ready", and `BEHIND` is
  disabled. — learned 2026-11-22 · [ADR 0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- `viewerCanDisableAutoMerge` can be true for auto-merge someone else enabled. *Why it matters:* cancelling is offered
  on others' auto-merge too. — learned 2026-11-22 · [ADR 0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- With a merge queue, GitHub ignores the merge method and puts the PR in the queue when it is ready. *Why it matters:*
  merge queues are out of scope. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)

## Stacks

- A GraphQL `PullRequest` has `stack` (`number`, `size`, `entries`) and `stackEntry` (`position`, 1 being closest to the
  base), both null when not stacked. *Why it matters:* jazygit reads them to show and merge stacks. — learned 2026-11-22
  · [ADR 0024](../adr/0024-stacked-pull-requests.md)
- Stack entries give each pull request's `headRefOid` and `isInMergeQueue`. Rewriting a branch that is in a merge queue
  takes it out of the queue. *Why it matters:* the stack rebase refuses queued branches and knows where a merged branch
  was. — learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- GitHub stacks are linear: each pull request has one below it and at most one above. *Why it matters:* a stack shows as
  one ordered list. — learned 2026-11-22 · [ADR 0072](../adr/0072-stacks-in-the-branches-list.md)
- The Stacks REST API (also used by `gh-stack`): `POST repos/{owner}/{repo}/stacks {"pull_requests":[…]}` makes a stack
  of two or more, bottom first. `POST …/stacks/{n}/add` adds only on top. `POST …/stacks/{n}/unstack` returns 204 when
  no pull requests are left, else the stack that remains. GitHub cannot insert in the middle or reorder. *Why it
  matters:* these are the only operations jazygit's stack menu can offer. — learned 2026-11-22 · [ADR
  0073](../adr/0073-making-and-unmaking-a-stack.md)
- GitHub refuses a stack whose pull request bases do not form one line from the stack's base to its top. It accepts `gh
  pr edit --base` that makes a pull request the base of its own base, and only then refuses the stack. *Why it matters:*
  such targets are filtered out beforehand, and bases already changed stay changed when the stack is refused. — learned
  2026-11-22 · [ADR 0073](../adr/0073-making-and-unmaking-a-stack.md)
- After `unstack`, merged and queued pull requests stay in the stack on GitHub. *Why it matters:* the unstack question
  counts them separately. — learned 2026-11-22 · [ADR 0073](../adr/0073-making-and-unmaking-a-stack.md)
- GitHub links a branch on top of one whose commits it lacks, but then will not merge the stack: it calls the branches
  diverged and offers "Rebase stack" on its web page. Neither the diverged state nor that rebase is in its API. GitHub
  merges a stack only while its history is linear. *Why it matters:* jazygit detects divergence itself and rebases the
  stack locally. — learned 2026-11-22 · [ADR 0073](../adr/0073-making-and-unmaking-a-stack.md), [ADR
  0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- When the bottom pull request of a stack is merged, GitHub usually rebases the rest of the stack by itself. *Why it
  matters:* local branches can be behind what GitHub pushed, so a rebase refuses until they are pulled. — learned
  2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)

## Checks and their timing

- In GraphQL each check run has `status` (`QUEUED`, `IN_PROGRESS`, …), `startedAt` and `completedAt` (empty while
  queued) and `databaseId`, and through `checkSuite.workflowRun` its run's `createdAt`, `runAttempt`, `event` and
  workflow name. *Why it matters:* these are the inputs of the check-time estimates. — learned 2026-11-22 · [ADR
  0032](../adr/0032-how-long-checks-take.md)
- Check runs give when each job started and ended, and the workflow run gives when it was created. *Why it matters:*
  queue time and per-job runner waits are computed from these. — learned 2026-11-22 · [ADR
  0133](../adr/0133-the-timings-of-one-build.md)
- GitHub reports check times to the second. *Why it matters:* start and end times of checks can be shown to the second.
  — learned 2026-11-22 · [ADR 0037](../adr/0037-when-the-checks-started-and-ended.md)
- `checkSuite.workflowRun` is `null` for a check run made by an app other than GitHub Actions (Codecov, SonarCloud, …):
  it has no workflow and no run start; such checks are identified by `checkSuite.app.slug`. *Why it matters:*
  non-Actions checks are keyed `<app slug>/<check>` and estimated from their duration alone. — learned 2026-11-22 · [ADR
  0032](../adr/0032-how-long-checks-take.md)
- A commit status (the older API) has no timing, and only its latest state shows. *Why it matters:* a pending commit
  status makes the overall estimate unknown. — learned 2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md),
  [ADR 0037](../adr/0037-when-the-checks-started-and-ended.md)
- GitHub does not say how long a check usually takes, and the status-check rollup does not say which check waits for
  which. *Why it matters:* checks are timed from their workflow run's start, learned from finished runs. — learned
  2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md)
- A re-run keeps the `createdAt` of the first attempt, and GitHub gives no time at which an attempt started. *Why it
  matters:* only first attempts are learned from, and a re-run is timed from its earliest check `startedAt`. — learned
  2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md), [ADR
  0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md), [ADR
  0037](../adr/0037-when-the-checks-started-and-ended.md)
- `statusCheckRollup` gives only the last attempt of a re-run workflow, and a re-run keeps its run's first-attempt start
  time. Its jobs include those of the earlier attempt that passed, with their original times. *Why it matters:* a re-run
  counts once, is timed from its first job's start, and gets no queue or wait times. — learned 2026-11-22 · [ADR
  0131](../adr/0131-the-builds-of-a-branch.md), [ADR 0133](../adr/0133-the-timings-of-one-build.md)
- A pull request's `statusCheckRollup` state counts all its checks, unlike combining the states of the first 100 check
  runs. *Why it matters:* pull requests use the rollup state. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md)
- A workflow run that waits to start (for its `concurrency` group or a runner) has no check runs, so the commit's
  `statusCheckRollup` is `null`, which reads as no checks. *Why it matters:* waiting workflows must be read from
  `checkSuites` instead. — learned 2026-11-22 · [ADR 0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- A commit's `checkSuites` include, for GitHub Actions, a suite with a `workflowRun` (`createdAt`, `runAttempt`,
  `event`, workflow) from the moment it is queued, a `status` other than `COMPLETED` while not done, and
  `checkRuns.totalCount` 0 while it waits. *Why it matters:* such a suite is shown as a queued check named after its
  workflow. — learned 2026-11-22 · [ADR 0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- Apps that listen to check suites (e.g. `claude`) get a suite on every commit and leave it `QUEUED` for good when they
  never add a check run; those suites have no `workflowRun`. Over the last 40 commits of `main` and every branch of
  jazygit, no Actions suite was left not completed without check runs. *Why it matters:* only suites with a
  `workflowRun` count as waiting workflows. — learned 2026-11-22 · [ADR
  0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- A workflow whose jobs were all skipped is `COMPLETED` with no check runs. *Why it matters:* a suite without check runs
  is not by itself a waiting workflow. — learned 2026-11-22 · [ADR
  0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- GitHub creates the check run of a job that `needs` others only once those are done, so until then the job is not in
  the rollup at all. *Why it matters:* the estimate also waits for checks the history says a run has but which do not
  show yet. — learned 2026-11-22 · [ADR 0036](../adr/0036-wait-for-jobs-not-made-yet.md)
- When every check of a workflow run is done and a job is still missing, it is not coming: its `if:` was false, a job it
  needs failed, or it was taken out of the workflow. *Why it matters:* missing checks are only waited for while a check
  of the run is not done. — learned 2026-11-22 · [ADR 0036](../adr/0036-wait-for-jobs-not-made-yet.md)
- One commit can have two runs of one workflow, e.g. one for a push and one for a pull request, or a cancelled run next
  to the one that replaced it. *Why it matters:* a check is missing per workflow run, not per commit. — learned
  2026-11-22 · [ADR 0036](../adr/0036-wait-for-jobs-not-made-yet.md)
- The checks on the tip of a branch can come from several workflow runs. *Why it matters:* a failed build handed to an
  agent lists failed jobs across runs, each with its run. — learned 2026-11-22 · [ADR
  0161](../adr/0161-a-failed-build-handed-to-an-agent.md)
- GitHub allows a workflow named `<other workflow>/<something>`. *Why it matters:* keys of the form `<workflow>/<check>`
  can collide, which is not handled. — learned 2026-11-22 · [ADR 0036](../adr/0036-wait-for-jobs-not-made-yet.md)

## Builds and workflow runs

- GraphQL gives a branch's commits through `ref(qualifiedName:) { target { ... on Commit { history } } }` and the check
  runs of each commit through `statusCheckRollup`, each with its workflow run. *Why it matters:* a branch's build
  history is read from these. — learned 2026-11-22 · [ADR 0131](../adr/0131-the-builds-of-a-branch.md)
- Workflow runs triggered by `issue_comment` or `issues` are attributed to the tip of the default branch, because GitHub
  runs them there. *Why it matters:* only `push`, `pull_request`, `pull_request_target`, `merge_group`,
  `workflow_dispatch` and `schedule` runs count as builds. — learned 2026-11-22 · [ADR
  0131](../adr/0131-the-builds-of-a-branch.md)
- A workflow run gives its head branch (none once that branch is deleted, as most pull request branches are after
  merging) and its pull request. *Why it matters:* each run across all branches is labelled with its pull request, else
  its branch. — learned 2026-11-22 · [ADR 0131](../adr/0131-the-builds-of-a-branch.md)
- GraphQL has no list of a repository's workflows, but REST does (`actions/workflows`). *Why it matters:* the tabs of
  "every branch" builds come from one REST read per view. — learned 2026-11-22 · [ADR
  0131](../adr/0131-the-builds-of-a-branch.md)
- A check run of GitHub Actions is a job, and its id is the job's id. *Why it matters:* a check's log is fetched with
  the job id. — learned 2026-11-22 · [ADR 0160](../adr/0160-the-log-of-a-check.md)
- A workflow run's jobs are named by the job's `name` (where an expression such as `acceptance (${{ matrix.shard }}/6)`
  can produce any text), or else by its key followed by its matrix values. A job of a called workflow is named after the
  calling job (`verify / package`). *Why it matters:* jazygit redoes this naming to match check runs to jobs in the
  workflow file. — learned 2026-11-22 · [ADR 0131](../adr/0131-the-builds-of-a-branch.md)
- Neither GraphQL nor REST gives a job's `needs`. Only the workflow file says which job waits for which. GraphQL does
  give the file's path (`workflowRun { file { path } }`). *Why it matters:* jazygit reads the workflow file at the
  build's commit to draw job dependencies. — learned 2026-11-22 · [ADR 0131](../adr/0131-the-builds-of-a-branch.md)
- A `pull_request` workflow run runs the workflow file of the merge of the pull request into its base, a commit GitHub
  does not name; the run reports the head commit as what it built. *Why it matters:* jobs of a pull request build that
  its head commit's workflow file does not have cannot be matched. — learned 2026-11-22 · [ADR
  0138](../adr/0138-workflow-files-from-github.md)
- GraphQL exposes the steps of a job (`CheckRun.steps`). *Why it matters:* steps of a picked build could be read with
  one more call later. — learned 2026-11-22 · [ADR 0133](../adr/0133-the-timings-of-one-build.md)

## Re-running and job logs

- GitHub re-runs a workflow run's failed jobs, plus the jobs that need them, with REST `POST
  repos/{owner}/{repo}/actions/runs/{run_id}/rerun-failed-jobs`; the whole run with `/rerun`; and one job with
  `actions/jobs/{job_id}/rerun`. GraphQL has none of these for GitHub Actions. *Why it matters:* `r` in the checks popup
  makes one REST call per failed run. — learned 2026-11-22 · [ADR 0135](../adr/0135-rerun-failed-checks.md)
- GitHub re-runs only a completed run, and only for a user with write access to the repository. A re-run keeps the run
  id with one more attempt, after which the tip's checks show that attempt's jobs. *Why it matters:* runs still in
  progress are skipped, and permission is not checked up front because GitHub's refusal says so. — learned 2026-11-22 ·
  [ADR 0135](../adr/0135-rerun-failed-checks.md)
- After a re-run is requested, GitHub takes a few seconds before it shows a job as queued. *Why it matters:* checks are
  re-read every 10 s for up to a minute after a re-run. — learned 2026-11-22 · [ADR
  0135](../adr/0135-rerun-failed-checks.md)
- A check run of another app, or a commit status, belongs to no workflow run and cannot be re-run through GitHub
  Actions. *Why it matters:* re-run lists such checks as not re-runnable, and says why. — learned 2026-11-22 · [ADR
  0135](../adr/0135-rerun-failed-checks.md)
- `GET repos/{owner}/{repo}/actions/jobs/{job_id}/logs` returns the job log as plain text: each line starts with a
  timestamp, with markers like `##[group]` and `##[error]`, and the tools' colours as ANSI escapes. A log is often
  several MB. *Why it matters:* the log is cached to a file and opened at the first `##[error]` line. — learned
  2026-11-22 · [ADR 0160](../adr/0160-the-log-of-a-check.md)
- A job has a log only once it is done. GitHub keeps logs for 90 days by default, and a finished job's log never
  changes: re-running a run creates a new attempt with new jobs. *Why it matters:* logs can be cached by run, attempt
  and job with no invalidation, and a missing log on an old job is expected. — learned 2026-11-22 · [ADR
  0160](../adr/0160-the-log-of-a-check.md)
- A queued workflow run has no job yet. A check from another app, or a commit status, has no log on GitHub. *Why it
  matters:* jazygit explains why there is no log for those checks instead of fetching. — learned 2026-11-22 · [ADR
  0160](../adr/0160-the-log-of-a-check.md)
- A commit status (from an outside CI or a bot) has a context (its name) and a target URL, and its logs are at that URL
  rather than on GitHub. *Why it matters:* statuses become failed jobs without a run, and the agent is pointed to their
  URL. — learned 2026-11-22 · [ADR 0161](../adr/0161-a-failed-build-handed-to-an-agent.md)
- A pull request from a fork controls the job names, the branch name and what its builds print in logs. *Why it
  matters:* text passed to an agent's prompt from the forge is untrusted (prompt injection), so jazygit fences it as
  data. — learned 2026-11-22 · [ADR 0161](../adr/0161-a-failed-build-handed-to-an-agent.md)

## Rate limits and cost

- A user has 5000 points an hour of GraphQL. *Why it matters:* polling budgets (e.g. up to 720 calls an hour for an open
  pull request dialog) are sized against it. — learned 2026-11-22 · [ADR
  0034](../adr/0034-check-times-to-the-second-in-the-dialogs.md)
- GitHub ends a GraphQL call after 10 s, and when it does it charges extra rate-limit points for an hour. *Why it
  matters:* paged reads are sized to stay well under 10 s per call. — learned 2026-11-22 · [ADR
  0131](../adr/0131-the-builds-of-a-branch.md)
- Reading the first 20 `checkSuites` of a commit next to its rollup keeps the branches query at 1 point a page and adds
  1 point to each pull request query (to 3–4 and 2–3 points), about 60 points an hour more for the one read every
  minute. *Why it matters:* the extra suites are affordable. — learned 2026-11-22 · [ADR
  0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- On jazygit's main branch, reading a branch's last 100 commits with their check runs in one GraphQL call took 7-9 s and
  cost 11 of the 5 000 points an hour. 25 commits took 2.3 s (2-3 s, about 3 points). *Why it matters:* builds are read
  25 commits per call. — learned 2026-11-22 · [ADR 0131](../adr/0131-the-builds-of-a-branch.md), [ADR
  0132](../adr/0132-older-builds-on-demand.md)
- GraphQL lists a workflow's runs on every branch by its id (`node(id:) { ... on Workflow { runs } }`), and a branch's
  builds give that id (`workflowRun { workflow { id } }`). 50 runs with their jobs took 1.8 s. *Why it matters:* runs
  across all branches are read 25 per call, once per view. — learned 2026-11-22 · [ADR
  0131](../adr/0131-the-builds-of-a-branch.md)

## Repositories, branches and contents

- A GitHub repository's owner and name cannot contain `/`. *Why it matters:* `owner/name` is safe as a directory path. —
  learned 2026-11-22 · [ADR 0063](../adr/0063-state-per-repository.md)
- A GitHub owner name is letters, digits and `-`, at most 39 characters; a repository name is letters, digits, `.`, `_`
  and `-`, and may contain a `.` (e.g. `vercel/next.js`). *Why it matters:* this is the pattern references are matched
  with, and it is why `docs/file.md#12` is taken for a reference. — learned 2026-11-22 · [ADR
  0098](../adr/0098-references-and-bare-links-in-a-comment.md)
- `gh api repos/{owner}/{repo}/branches?protected=true --paginate` lists a repository's protected branches and needs
  only read access. *Why it matters:* the main branch and protected branches are taken as roots that are never part of a
  stack. — learned 2026-11-22 · [ADR 0072](../adr/0072-stacks-in-the-branches-list.md)
- GitHub can rename a branch so that its pull requests follow it, but a push that deletes a PR's head branch closes the
  pull request. *Why it matters:* renaming on the remote from jazygit closes the branch's open PR. — learned 2026-11-22
  · [ADR 0090](../adr/0090-renaming-a-branch-on-the-remote.md)
- `GET repos/{owner}/{repo}/contents/{path}?ref={sha}` with `Accept: application/vnd.github.raw` returns a file at any
  commit GitHub has, raw rather than base64 in JSON. *Why it matters:* this is the fallback when git lacks the workflow
  file at a build's commit. — learned 2026-11-22 · [ADR 0138](../adr/0138-workflow-files-from-github.md)
- GitHub exposes blame through GraphQL. *Why it matters:* it was not used. Pull requests are blamed locally at the
  fetched commits instead. — learned 2026-11-22 · [ADR 0147](../adr/0147-blame-on-a-version-of-a-file.md)

## Links, Markdown and images

- Every review comment has an anchor in its pull request's conversation,
  `https://{host}/{owner}/{repo}/pull/{number}#discussion_r{id}`, using the comment's REST id (`fullDatabaseId`), and it
  shows outdated and resolved threads. The files tab anchor `/files#r{id}` shows no outdated thread and can miss, since
  a large diff loads its files after the page. A conversation comment's anchor is `#issuecomment-{id}`. *Why it
  matters:* `o` links to the conversation anchor. — learned 2026-11-22 · [ADR
  0085](../adr/0085-a-review-remark-opens-in-the-browser.md)
- GitHub logins and hosts are matched without regard to case. *Why it matters:* browser settings per account are looked
  up case-insensitively. — learned 2026-11-22 · [ADR 0088](../adr/0088-a-browser-per-account.md)
- Issues and pull requests of a repository share one number series, and GitHub redirects `/<owner>/<repo>/issues/<n>` to
  `/pull/<n>` when `n` is a pull request. *Why it matters:* `#N` can be linked to `/issues/N` without asking GitHub
  which one it is. — learned 2026-11-22 · [ADR 0098](../adr/0098-references-and-bare-links-in-a-comment.md)
- GitHub renders `#N` in comments as a link to issue or PR N, also links `owner/repo#N` (only when that repository
  exists), `GH-N`, `@user` and commit hashes. *Why it matters:* jazygit links `#N`, `owner/repo#N` and bare URLs itself,
  and may link an `owner/repo#N` that GitHub would not. — learned 2026-11-22 · [ADR
  0098](../adr/0098-references-and-bare-links-in-a-comment.md)
- GitHub's autolinks end a bare URL at a space, `<`, `>`, `|`, `[`, `]`, `` ` `` or `\`, drop trailing `.`, `,`, `:`,
  `;`, `!`, `?`, `*`, `_`, `~` and quotes, and drop a `)` that no `(` in the URL opens. *Why it matters:* bare URLs in
  comments are cut the way GitHub cuts them. — learned 2026-11-22 · [ADR
  0098](../adr/0098-references-and-bare-links-in-a-comment.md)
- GitHub shows a Markdown file rendered, can show the rendered file in place of its diff in a pull request, and renders
  YAML front matter as a table. *Why it matters:* this is the reference for rendering a version of a Markdown file. —
  learned 2026-11-22 · [ADR 0103](../adr/0103-a-version-of-markdown-rendered.md)
- GitHub colours annotations in its colour for numbers. *Why it matters:* the `syntax-annotation` class uses the theme's
  number colour. — learned 2026-11-22 · [ADR 0113](../adr/0113-annotations-in-the-rich-view.md)
- When an image is pasted into a comment, GitHub writes an `<img>` tag with the alt text "image". Bots use `<img>` tags
  with inline size and style attributes for status icons. The images GitHub keeps are on `github.com` and
  `*.githubusercontent.com`. *Why it matters:* only those hosts are fetched (other hosts would learn who reads the
  remark), and an alt text of "image" is dropped. — learned 2026-11-22 · [ADR
  0082](../adr/0082-an-image-in-a-comment-is-a-placeholder.md), [ADR
  0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md)
- GitHub's "Quote reply" quotes a screenshot inside the quote. *Why it matters:* images in block quotes are drawn too. —
  learned 2026-11-22 · [ADR 0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md)

## GitHub Enterprise Server

- GitHub Enterprise Server without stacked pull requests refuses every GraphQL query that asks for `stack` or
  `stackEntry`, and may have no `merge-async`. *Why it matters:* every pull request read and merge would fail there;
  GHES without stacks is unsupported. — learned 2026-11-22 · [ADR 0024](../adr/0024-stacked-pull-requests.md)
- A repository with the stacks preview off, and GitHub Enterprise Server, answer the Stacks API with an error. *Why it
  matters:* no support check is made first; the error is shown. — learned 2026-11-22 · [ADR
  0073](../adr/0073-making-and-unmaking-a-stack.md)
