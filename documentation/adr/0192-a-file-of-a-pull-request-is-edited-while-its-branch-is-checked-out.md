# 192. A file of a pull request is edited while its branch is checked out

Date: 2026-11-24

## Context

Feedback on a pull request is read in its files (`F` on its branch, ADR 55, 61): the review threads are there, beside
the diff they are on (ADR 57). Acting on it means changing the files, and `e` does not open them there: ADR 128 left
`e` out of the files of a commit, a stash, a branch or a pull request, as "it is a commit, not the working tree", and
the file on disk "may be of another branch, or edited since". In the diff of a pull request's files `e` edits a review
comment (ADR 65). To change a file the threads point at, the user leaves the files (Escape, then `a` in the files
pane), finds the file again, and opens it there.

ADR 145 then said `e` opens the file of the working tree "also when the files or the diff are of a commit, a stash or a
branch", which its diff does, but no key was added to the files of those.

Of ADR 128's two reasons, only the first does harm. A file edited since is what the user is after: the edit is the
answer to the thread. A file of another branch is not: the user would change a file of what is checked out, believing
it the pull request's.

## Decision

- `e` on a file in the files of a commit, a stash, a branch or a pull request opens the file of the working tree, at
  its first line, as `e` in the files pane does (ADR 145): in the editor set to open files, else in git's editor.
  `?` lists it. A directory is not opened, as in the files pane.
- The files of a branch, of the main branch as committed, or of a pull request, are opened only while that branch is
  checked out in this worktree. Else `e` opens nothing, and says why: `feature is not checked out: e edits the file on
  disk, so check it out first`. Not: opening it anyway, and saying it is another branch's, which is the mistake the
  check is for. Not: checking it out, which is a step of its own, with its own questions (uncommitted changes, another
  worktree having it).
- The files of a commit or a stash open the file of the working tree whatever is checked out, as their diff does
  (ADR 145): they are of no branch to check.
- A pull request's files know the local branch they were opened from: `Changes.OfPullRequest` keeps it, as
  `OfBranch` and `OfTip` keep theirs, the branch `F` was pressed on, or the head branch of one listed on the pull
  requests screen. It stays when the pull request moves on to other commits (ADR 60). One opened with no branch known
  is not edited, and says so with its number: `#42 is not checked out…`. Not: matching its head commit with HEAD,
  which a commit made to answer the feedback, not pushed yet, would break at once.
- The branch is compared by name with the checked-out one. Which pull request a branch has is the forge's to know
  (ADR 171); the kernel keeps only the name it was given, which names no forge.
- In the diff of a pull request's files, `e` still edits the review comment of the cursor (ADR 65).
- Whether `e` applies shows in what it says when pressed, not in the help: a key's binding does not read the model.

## Consequences

- Answering a review thread is `e` on its file, in the files it was read in, while the pull request's branch is
  checked out.
- `Changes.OfPullRequest` has a second component; matches on it name both.
- Left for later: `e` in the diff of a pull request's files opening the file at the line of the cursor, which needs a
  key other than `e`; a file the change deleted, which `e` opens as a new, empty one.
