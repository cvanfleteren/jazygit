# 99. Approving a pull request, or asking for changes

Date: 2026-11-10

## Context

A pull request can be commented on from its dialog (ADR 5), and its lines from the diff of its files (ADR 60), but
approving it or asking for changes means going to the browser. ADR 5 left reviews out; ADR 60 sends each comment on
the lines on its own, published at once, and left out a pending review, gathering comments to submit them with an
approval or a request for changes (`wanted.md`).

`gh pr review <n> --approve | --request-changes | --comment --body <text>` submits a review with GraphQL's
`addPullRequestReview`, one call with an `event` and a body. GitHub needs a body to ask for changes or to comment, not
to approve. It refuses an approval or a request for changes on one's own pull request, and it refuses a review while
the viewer has a pending one, started in the browser: a viewer has one pending review per pull request, and
`addPullRequestReview` does not submit it. Anyone who can read a repository can review; only the reviews of those who
can write to it count for the rules of a protected branch.

GraphQL says nothing of whether the viewer may approve: no field does. `viewerDidAuthor` says the pull request is the
viewer's own, `viewerLatestReview { state }` that the viewer has a pending review.

The popups that write a comment share `CommentComposer`: Enter adds a line, Ctrl+S sends, Ctrl+O opens the text in the
editor, Escape drops it, a failed send says why under the text. In the pull request dialog `r` rebases and merges
(ADR 5's list), `c` comments; in the diff of a pull request's files `c` and `r` comment and reply (ADR 60).

## Decision

### What a review is

- A verdict, to approve or to ask for changes, with a text, sent on its own. The comments on the lines stay as ADR 60
  has them, each published when it is sent.
- Not: a review that only comments. Without a pending review it says what `c` says, as a comment of the conversation;
  it comes with the pending review, where it submits the comments gathered without a verdict.
- Not: a pending review (`wanted.md`). It takes reading the viewer's pending review and showing its comments in the
  diff, left out on purpose (ADR 55); writing the comments of the diff into it, with GraphQL's
  `addPullRequestReviewThread` and `addPullRequestReviewThreadReply`, and a way to choose that over publishing at once;
  submitting it with `submitPullRequestReview` or dropping it with `deletePullRequestReview`; and editing and deleting
  its comments. The review of this ADR is where it would be submitted: `GitHub.submitReview` then submits the pending
  review when there is one.

### Where

- `a` in the pull request dialog, after `c` and before `e`, and `a` in `Scopes.PULL_REQUEST_DIFF`, listed by `?`. It is
  free in both.
- Not: the tree of a pull request's files (`Scopes.COMMIT_FILES`), a scope it shares with the tree of a commit.
- Not: the pull requests of other repositories in the pull requests screen, whose dialog actions are for later
  (`prs.md`).

### When it may

- On an open pull request, a draft too. Not on a closed or merged one.
- Not on the viewer's own: the dialog dims `a`, as it dims merging what cannot be merged; in the diff, `a` says so without asking GitHub, as ADR 60 does
  for the rights of a thread. Not: offering only a review that comments, which is left out.
- Not while the viewer has a pending review: `a` says to submit or drop it in the browser, without asking GitHub; from
  the dialog, which shows no error of its own, under the text of the popup, which then sends nothing. Not: submitting
  it, which would publish comments jazygit never showed.
- Who cannot write to the repository can review: GitHub takes it, and shows it, though it does not count.
- `DetailsQuery` reads `viewerDidAuthor` and the state of `viewerLatestReview`, and the node id of the pull request,
  which `addPullRequestReview` takes rather than its number. `ReviewThreadsQuery` reads the same, and `isDraft`, with
  the remarks of the diff, so `a` there needs no details read first: the diff has no dialog to hold them.

### The popup

- A `CommentComposer`, titled `Review pull request #<n>`, with two boxes, as the fields of the pull request dialogs
  are, so the choice and the text are set apart:
  - `Verdict`, the choice: `(•) Approve   ( ) Request changes`.
  - The text, titled by whether the verdict chosen needs it: `Comment (optional)` to approve, `Reason (required)` to
    ask for changes.
- The choice is one stop of the focus, as a group of radio buttons is: its box takes the focus, and shows it by its
  border, as the box of the text does, in every theme (`Panel.choices:focus`). Left or Up chooses approve, Right or
  Down asks for changes. Tab goes on to the text, Shift+Tab back. The focus starts on the choice, on approve,
  whatever the viewer's last review said, so approving is `a` and Ctrl+S. Ctrl+S, Ctrl+O and Escape do the same from
  the choice as from the text. The hint says the arrows choose in `Verdict`, as in the text they move the cursor.
- Not: a key that switches the verdict from the text, as Ctrl+T first did, which only the hint told of; nor Tab for
  it, which TamboUI's router takes to move the focus before any element sees it. Not: each choice a stop of the focus
  of its own, chosen with Space, as first built: the arrows are how a group of radio buttons is gone through, and one
  stop is one Tab less to the text. Not: the focus on the mark of the choice made, reversed, which left the box
  unmarked while the box of the text showed its focus: TamboUI has no `:focus-within` for a box to show the focus of
  what is in it.
- A draft says so in the title, `Review pull request #<n> · draft`, and above the text, in the colour of a warning: the author may
  not want it reviewed yet. Not: asking to confirm. It follows the details of the dialog it came from, as the comment
  popup does (ADR 5), so a pull request made a draft or ready while it is open shows so.
- Ctrl+S on an empty or blank text approves. To ask for changes, it does nothing, as on a blank comment (ADR 5), and
  the popup says a reason is needed. Text is sent as typed, not trimmed.
- Ctrl+O opens the text in the editor, in `PR_REVIEW.md`; the verdict stays in the popup. Escape drops the text
  without asking, as ADR 5.

### Sending

- `GitHub.submitReview(pullRequest, verdict, body)`, by the node id of the pull request, with GraphQL's `addPullRequestReview`, its command in the log. Not:
  `gh pr review`, which would have to be replaced by the call to `submitPullRequestReview` once there is a pending
  review.
- The popup stays open while it is sent, saying so; meanwhile it cannot be changed, sent again or left. The branch of
  the pull request is not marked busy, as for a comment (ADR 5).
- Once GitHub took it, the popup closes, and the details of the pull request are read again, so its review and
  reviewers are current; from the diff, its remarks too, as `R` reads them. A failure says why under the text, which
  is kept.

### Delivery

1. This ADR, and in `wanted.md` and `prs.md`: approving and asking for changes are done; the pending review, with the
   review that only comments, is what is left.
2. The node id, `viewerDidAuthor` and `viewerLatestReview` in `DetailsQuery` and `ReviewThreadsQuery`, and `GitHub.submitReview`.
3. The popup, `a` in the dialog and in the diff with when it may, reading again once sent, and an acceptance test
   against a fake gh that approves, and asks for changes with a blank text and then with one.
