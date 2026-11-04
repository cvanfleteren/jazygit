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
git -c rebase.missingCommitsCheck=ignore rebase -i --keep-empty --no-autosquash --empty=drop --onto <base> <base> <top>
```

with, for each open branch from the bottom up:

```
pick <each of its own commits, parents first>
update-ref refs/heads/<branch>          # not for the top, which the rebase moves itself
```

- The own commits of a branch are those that are not merges, that are on it and not on any place the branch below
  is known to be or to have been, nor on the base:
  `git log --topo-order --reverse ^<branch below> ^origin/<branch below> ^<fork point> ^<base> <branch>`. The places
  of the branch below are its local branch and its branch on the remote, as they were before the rebase, and where it
  was when the branch forked from it, `git merge-base --fork-point`, which reads its reflog: a commit of the branch
  below that was amended since is still not the branch's own, and is not replayed on top of its amended self.
- The commits of the branches below, and of the base, are left out; so is what a branch below brought in from a branch
  that was merged into the base, squashed or not. `rebase.missingCommitsCheck` is ignored for it: the todo leaves
  those out on purpose.
- `--empty=drop` drops a commit that has nothing left once picked, as the base has it already.
- `--topo-order`: the commits of a branch are picked parents first, also when their dates say otherwise.
- Merge commits are dropped, as `gh stack rebase` and GitHub's own rebase do: GitHub merges a stack only with a linear
  history. A conflict a merge resolved then stops the rebase again.
- A closed pull request under the first open one is left as it is: its commits that are on the bottom branch are the
  bottom's own, and are replayed with it.
- `<base>` is the base of the stack as GitHub has it, its `baseRefName`, on the remote the branches of the stack are
  pushed to, as `refs/remotes/origin/main`; never the local branch, which can be far behind, and whose commits a
  branch built on the newer one would then replay as its own. It does not fetch first; fetching stays a step of its
  own, and a base that was never fetched from that remote refuses the rebase.
- The rebase also puts the bottom on the base as it is now: one rebase makes the stack what GitHub merges.
- Only a stack on GitHub is rebased (ADR 72): its branches and their order are GitHub's, not a guess.
- Not: one rebase with `--update-refs`, which cannot move a branch that is not built on the one below; nor a rebase
  per branch, which takes a state of its own across rebases.

### A merged branch below

A merged pull request stays in its stack (ADR 72). The first open branch above it is built on where the merged branch
was, whose commits are in the base now: all that is known of where it was is left out, its `headRefOid`, which the
stack entries now read, the branch on the remote, and the local branch. Without any of them, the rebase refuses: it
would replay commits the base has. GitHub mostly rebases the stack itself once its bottom is merged; this is for one
that did not, or was rebased here before.

### Before it starts

The rebase is planned first, reading for each branch where it meets the one below and its own commits, then asked
about: `Rebase stack #12 onto origin/main: feat/a, feat/b, feat/d`, and, when there are any, the merges it drops:
`feat/b: its merge of main is dropped`.

It refuses when the worktree has changes, committed or not, untracked files too: the rebase checks out the top, and
what is not committed would come back there, on another branch than it was made on, with conflicts where the stack
changes the same files. It is committed or stashed first; `--autostash` is not used.

It refuses, naming the branch and why, when a branch it would move:

- is checked out in another worktree: an `update-ref` would move it under the files there;
- is in a merge queue, which its stack entry says (`isInMergeQueue`): rewriting it would take it out;
- is open on GitHub but not checked out here: it cannot be moved;
- is pushed to another remote than the other branches of the stack: they are pushed in one push;
- never had what is on its branch on the remote, as when GitHub rebased the stack itself, or an agent pushed to it:
  it is pulled first, or the rebase would start from what is no longer there, and pushing would fail or lose that.
  Git is asked before the rebase, for each branch that is on the remote at another commit, upstream or not, whether
  its reflog has that commit, as `--force-if-includes` asks. A branch amended or rebased here after it was pushed is
  behind its upstream, but had what is there, and is rebased.

### When it starts

The question can stay open a while, and an agent can work meanwhile. Right before the rebase, git is asked again where
each branch is and where it is checked out (`git for-each-ref --format=%(objectname) %(worktreepath)`). It does not
start, saying so, when a branch moved since its commits were read, as the todo would leave out what it got and move
the branch without it, or when it is now checked out in another worktree.

### While and after it runs

- The rebase checks out the top. When it is done without stopping, the branch that was checked out before is checked
  out again. One that stopped on a conflict is continued, skipped or aborted with `M`, as any rebase, and ends on the
  top; aborting puts every branch back.
- Once it is done without stopping, the stack is pushed (below), unless the question was answered with `l`.

### From the link

When making or adding to a stack warns that a branch is not built on the one below (ADR 73), the confirmation also
offers to link and rebase, with `r`. The rebase is planned once GitHub lists the stack, which it reads again for until it
does (ADR 73), and asked about as from the menu: rebased and pushed, or rebased only. When GitHub does not list it
before that is given up on, the user is told it was not rebased, to do it from the menu.

### Pushing

A rebase of the stack pushes it too, and so does "Push stack" in the stack menu: every open branch of the stack, in one
push that GitHub takes whole or not at all:

```
git push --force-with-lease --force-if-includes --atomic --set-upstream <remote> refs/heads/<branch>:refs/heads/<head>…
```

- Each branch is pushed to the branch of its pull request, its `headRefName`, which is not always its own name: a
  local `x` that follows `origin/feat/x`, whose pull request it is, goes to `feat/x`, not to a new `x`. `<remote>` is
  the one the branches are all pushed to: their upstream, else `origin`.
- Each lease is the branch on the remote as last fetched, which the rebase refused to start without having.
- `--force-if-includes` refuses a branch whose remote was fetched where the local branch never was. jazygit fetches
  in the background, so a lease alone would take on what an agent pushed while the question was open, or between
  `l` and "Push stack", and push over it.
- A branch without an upstream gets the branch it is pushed to as its upstream, so the branches of an agent push too.
- "Push stack" refuses, naming it, a pull request of the stack that is not checked out here, as the rebase does: a
  push of some of its branches is not the stack GitHub merges.
- The question before the rebase is the only one: `(enter) rebase and push   (l) rebase only   (esc) cancel`. GitHub
  sees a stack rebased only once it is pushed, so pushing is what is meant nearly always; `l` keeps it here, to look
  at or test first, and "Push stack" pushes it after.
- A rebase that stopped on a conflict pushes nothing, also once `M` finished it: what was resolved by hand is looked
  at before it goes anywhere, and "Push stack" pushes it then.
- Not: asking again before pushing, once the rebase is done: two questions for what is one step.

## Consequences

- A diverged stack is made linear here, as GitHub's button does there, in one rebase that `M` continues or aborts.
- The stack entries are read with two fields more, `headRefOid` and `isInMergeQueue`.
- Merge commits in the branches of a stack are dropped by a rebase of it, which the question before says.
- A stack with a branch in another worktree, in a merge queue, not checked out, pushed elsewhere, or behind what
  someone else pushed to it, is not rebased until that changes; nor while the worktree has changes.
- A branch below amended here is restacked onto without replaying what it had before.
- Rebasing a stack checks out its top for the while it runs.
- The base is taken as it was last fetched, from the remote the stack is pushed to.
