# 74. Rebasing and pushing a stack

Date: 2026-11-04

## Context

GitHub merges a stack only while its history is linear: each branch has all the commits of the one below it, and the
bottom one those of the base (ADR 73). A stack loses that when its base moves on, when a branch in it gets a commit
more, or when a branch is linked on one it was not built on. GitHub then says the branches have diverged, and offers
"Rebase stack" on its web page: a rebase on its servers, from the bottom up. Neither that rebase nor whether a stack
diverged is in its API. When the bottom pull request is merged, GitHub rebases the rest of the stack by itself.

`r` rebases the checked out branch on the highlighted one, with
`git rebase --autostash --keep-empty --rebase-merges --no-autosquash refs/heads/<onto>`; conflicts stop it, and `M`
continues, skips or aborts it. A stack needs every branch moved, each onto the new tip of the one below.

Three ways were looked at:

- One rebase of the top branch with `--update-refs`: git moves every branch whose tip is in what it replays. A branch
  that is not built on the one below, as one linked onto it, is not in what the top replays: it is not moved, and the
  stack stays diverged.
- A rebase per branch, from the bottom up, each `--onto` the new tip of the one below, as `gh stack rebase` does. That
  takes a state of its own to go on after a conflict, as several rebases run one after the other.
- One interactive rebase whose todo jazygit writes: for each branch, from the bottom up, `pick` its own commits, then
  `update-ref refs/heads/<branch>`. That moves a branch that was not built on the one below too, and is one rebase:
  git keeps where it is between conflicts. Tried:

  ```
  pick c1, pick c2, update-ref refs/heads/c, pick d1      # d was built on main, not on c
  $ git rebase -i --onto main main d                     # with that todo
  d1 (d) ← c2 (c) ← c1 ← m1 (main)
  ```

  An `update-ref` moves a branch also when it is checked out in another worktree, under the files of that worktree.
  (`--update-refs` leaves such a branch out, without saying so.)

`gh stack rebase` runs `git rebase` without `--rebase-merges`: merge commits in a branch are dropped, and only its
other commits replayed. GitHub's documentation calls its server-side rebase the same as `gh stack rebase`.

## Decision

### One rebase, with a todo jazygit writes

"Rebase stack", from the stack menu (ADR 73) on any branch of a stack on GitHub or on its header, rebases the whole
stack in one interactive rebase, whose todo jazygit writes instead of an editor:

```
git rebase -i --autostash --keep-empty --no-autosquash --onto <base> <base> <top>
```

with, for each open branch from the bottom up:

```
pick <each of its own commits, oldest first>
update-ref refs/heads/<branch>          # not for the top, which the rebase moves itself
```

- The own commits of a branch are those that are not merges, from where it meets the branch below as that was before
  the rebase, its merge base with it, to its tip: `git rev-list --reverse --no-merges <merge base>..<branch>`. The
  commits of the branches below, and of the base, are left out; so is what a branch below brought in from a branch
  that was merged into the base, squashed or not.
- Merge commits are dropped, as `gh stack rebase` and GitHub's own rebase do: GitHub merges a stack only with a linear
  history. A conflict a merge resolved then stops the rebase again.
- `<base>` is the base of the stack as GitHub has it, its `baseRefName`, on the remote: `refs/remotes/origin/main`,
  else the local branch. It does not fetch first; fetching stays a step of its own.
- The rebase also puts the bottom on the base as it is now: one rebase makes the stack what GitHub merges.
- Only a stack on GitHub is rebased (ADR 72): its branches and their order are GitHub's, not a guess.
- Not: one rebase with `--update-refs`, which cannot move a branch that is not built on the one below; nor a rebase
  per branch, which takes a state of its own across rebases.

### A merged branch below

A merged pull request stays in its stack (ADR 72). The first open branch above it is built on where the merged branch
was, whose commits are in the base now: the merge base is taken with that, the first of the local branch, the branch
on the remote, and its `headRefOid`, which the stack entries now read. Without any of them, the rebase refuses: it
would replay commits the base has. GitHub mostly rebases the stack itself once its bottom is merged; this is for one
that did not, or was rebased here before.

### Before it starts

The rebase is planned first, reading for each branch where it meets the one below and its own commits, then asked
about: `Rebase stack #12 onto origin/main: feat/a, feat/b, feat/d`, and, when there are any, the merges it drops:
`feat/b: its merge of main is dropped`.

It refuses, naming the branch and why, when a branch it would move:

- is checked out in another worktree: an `update-ref` would move it under the files there;
- is in a merge queue, which its stack entry says (`isInMergeQueue`): rewriting it would take it out;
- is open on GitHub but not checked out here: it cannot be moved;
- is behind its own branch on the remote, as when GitHub rebased the stack itself, or an agent pushed to it: it is
  pulled first, or the rebase would start from what is no longer there, and pushing would fail or lose that.

### While and after it runs

- The rebase checks out the top. When it is done without stopping, the branch that was checked out before is checked
  out again. One that stopped on a conflict is continued, skipped or aborted with `M`, as any rebase, and ends on the
  top; aborting puts every branch back.
- `--autostash` keeps what is not committed.
- Once it is done, pushing the stack is offered.

### From the link

When making or adding to a stack warns that a branch is not built on the one below (ADR 73), the confirmation also
offers to link and rebase. The rebase is planned once GitHub lists the stack, which it reads again for until it does
(ADR 73).

### Pushing

"Push stack", in the stack menu and offered after a rebase, pushes every open branch of the stack in one push that
GitHub takes whole or not at all:

```
git push --force-with-lease --atomic --set-upstream origin <branch>…
```

- Each lease is the branch on the remote as last fetched, which the rebase refused to start without having.
- A branch without an upstream gets `origin/<branch>`, so the branches of an agent push too.
- A rebase does not push by itself, as `r` does not.

## Consequences

- A diverged stack is made linear here, as GitHub's button does there, in one rebase that `M` continues or aborts.
- The stack entries are read with two fields more, `headRefOid` and `isInMergeQueue`.
- Merge commits in the branches of a stack are dropped by a rebase of it, which the question before says.
- A stack with a branch in another worktree, in a merge queue, not checked out, or behind its remote, is not rebased
  until that changes.
- Rebasing a stack checks out its top for the while it runs.
- The base is taken as it was last fetched.
