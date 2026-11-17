# 143. Setting the base of a branch

Date: 2026-11-17

## Context

What a branch is counted against, how far it is ahead and behind (ADR 46), which of its commits are dimmed (ADR 20),
and what it is rebased on (ADR 91), is its base: what GitHub says it is built on, when it has an open pull request;
else the branch it was made from, as `branch.<name>.gh-merge-base` records it (ADR 72); else the main branch.

jazygit records that config when it makes a branch, and `gh` does too. But the config stays in the clone: on another
machine, a branch made elsewhere is counted against `main` until someone runs `git config` by hand. And a recorded
base counted only when it was a local branch, so a branch built on a colleague's branch that is not checked out had
none.

git does not know which branch a branch was made from; a branch is a name for a commit. It can be told fairly well:
the branch it forked from last, of those that do not have its tip, is the likeliest. Its reflog says which it was made
from, while the reflog is there, in the clone it was made in, and when git was given a branch to start from:
`git checkout -b topic` from a checked out branch says `Created from HEAD`.

## Decision

### The key

`b` on a local branch in the branches pane (free since ADR 142) offers the branches it could be built on, to pick one:

```
┌ Base of ART03DEV01-6185-e2e ─────────────────────────────────────┐
│ ● staging/ART03DEV01-5783-customer-sync   26 ahead     made from │
│   ART03DEV01-5783-prepare-customer-for…   26 ahead, 7 behind     │
│   development                             32 ahead               │
│   main                                    32 ahead     main bra… │
│   None: compare with main                                        │
└──────────────────────────────────────────────────────────────────┘
```

- Picking one records it in `branch.<name>.gh-merge-base`, as `gh` does; `None` removes it, so the main branch is the
  base. The branches are read again after, which counts and dims against the new base.
- `●` marks the base recorded now. The highlight starts on it, else on the first.
- Each row says how many commits of the branch it does not have, and how many of its own the branch does not have:
  the branch's `N ahead, M behind` against it, as its title would say.
- `/` filters, as in every pick list (ADR 38).

It is refused, saying why, where it would change nothing:

- for the main branch and a protected one, roots, which are built on no other branch;
- for a branch with an open pull request: its base is what GitHub says, which comes before what is recorded. It is
  changed on GitHub. Not: changing the base of the pull request too, which is a change on GitHub for a key that only
  set a config before.

### What is offered, and in what order

One `git for-each-ref --format=%(refname) %(ahead-behind:<tip>)` over the local branches and those on `origin`
counts every branch against the tip at once, as ADR 46 counts the bases, and one `git log --walk-reflogs` of the branch
reads where it was made from.

- Left out: the branch itself, and a branch that has its tip, which is built on it or a copy of it.
- One row per name. Of a branch that is both local and on `origin`, the one on `origin` is counted, as the base is
  looked up there first (ADR 20). The config records the name either way.
- First the branch the reflog says it was made from, if it is offered, tagged `made from`.
- Then by how many commits of the branch it does not have: the fewest is the one it forked from last.
- Then by how many commits of its own it has: of two that forked at the same commit, the one without commits of its own
  is the one it is built on, the other one is beside it.
- Then a root last: a branch made from `staging` forks at the same commit from `main`, when `staging` has nothing of its
  own yet.

### A base on origin

A base, recorded or GitHub's, now counts when it is a local branch or one on `origin`. A branch built on a colleague's
branch, or on one that is not checked out on this machine, is counted and dimmed against it.

- The rebase onto the base (ADR 91) still takes only a local branch: for a base that is only on `origin` there is no
  base to rebase onto, as for one that is gone.
- Stacks are made of local branches; a parent on `origin` joins none.

## Consequences

- A branch made on another machine gets its base with one key, the likeliest offered first, rather than with
  `git config`.
- Reading the offers takes two git processes, each once per key press.
- The ranking guesses: after the base was merged into the branch, the base forked off last can be another one, and a
  squash merge upstream leaves no shared commit to find. The counts show why each is where it is.
- A pull request's base on a branch that is only on `origin` is now the parent, where ADR 72 had none.
