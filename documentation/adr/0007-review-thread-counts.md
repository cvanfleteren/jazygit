# 7. Counting the review threads of a pull request

Date: 2026-10-21

## Context

The pull request dialog says how many comments a pull request has, but those are only the comments of its
conversation. The inline comments on lines of its diff, in review threads, are not counted anywhere, and the comments
popup (ADR 6) leaves them out. Whether a review left threads, and whether they are resolved, is what tells if a pull
request still needs work.

`gh pr view --json`, which read the details, has no field for review threads: `comments` are those of the
conversation, and `reviews` only give the text of each review. GitHub's GraphQL API has them as `reviewThreads`, each
with whether it is resolved and its comments.

## Decision

The pull request dialog gets a **Threads** row, under Comments, always shown:

- `0` without review threads;
- `5 with 12 comments, 2 unresolved`;
- `5 with 12 comments, all resolved` once none is unresolved;
- `130 with 180 comments` when none of the threads read is unresolved, but not all of them were read (see below).

It is plain text, as the Comments row is, without a colour for the unresolved ones. The plan was a row labelled
"Review comments", but the label column is 11 characters wide, and widening it for one row would move every other
row to the right.

Every thread counts, outdated ones too, and every comment in them, the replies too. The pending comments of a review
the user has not submitted yet count too: leaving them out would mean reading the state of every comment rather than
how many there are, for comments only their author sees.

The Comments row stays as it was: the comments of the conversation, without the reviews that have a text.

### One query

The details are read with one `gh api graphql` query in place of `gh pr view --json`, so the dialog still costs one
process. It asks for the fields of the pull request, how many comments it has, its labels, latest reviews and review
requests, the checks of its latest commit, and its review threads, each of whether it is resolved and how many
comments it has. The jq filter prints the same lines as before, plus one `threads` line: how many threads, how many
were read, and of those how many are unresolved and how many comments they hold.

Every list is read up to 100, without paging: labels, latest reviews, review requests, checks and threads.
`gh pr view` read all the checks; a pull request with more than 100 checks or threads is too rare to page for.

How many threads there are stays exact past 100, from `totalCount`; how many are unresolved and how many comments they
hold are then only of the ones read. That undercount is fine, but a wrong "all resolved" is not: it is the answer to
whether the pull request still needs work. GitHub gives threads oldest first, and the old ones are the ones most
likely resolved, so the newest 100 are read (`last: 100`), and "all resolved" is only said when every thread was
read.

### The model

`PullRequestDetails` gets a `ReviewThreads(threads, read, unresolved, comments)` component, after `comments`.
