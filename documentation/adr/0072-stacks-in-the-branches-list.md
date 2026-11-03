# 72. Stacks in the branches list

Date: 2026-11-03

## Context

A stack of pull requests (ADR 24) is a line of branches, each one built on the one below it, the bottom one on a
base such as `main`. The branches pane shows that a branch is in a stack only by `2/4` on line 2 of its row (ADR 45);
the branches of a stack are listed apart from each other, by when they last saw activity (ADR 41), between the
branches that have nothing to do with them.

Stacks are also made before GitHub knows of them: a branch made from a branch records its parent in
`branch.<name>.gh-merge-base` (ADR 3), and an agent working in the background opens pull requests with
`gh pr create --base <the branch below>`, which GitHub does not link into a stack until it is asked to (ADR 73).
What a branch is built on does not show anywhere in the list: ADR 46's `⇣` counts against a base it does not name.

Not every branch that others are built on is the bottom of a stack. Many repositories develop against a long-lived
branch such as `develop`, while their main branch is `main`; such a branch can even have a pull request of its own,
a release into `main`. Telling it apart from a branch that is the bottom of a stack takes knowing how the repository
is worked in.

GitHub's stacks are linear: every pull request has one below it and at most one above it. `gh-stack`, GitHub's
extension for them, keeps its own list of the branches of a stack in `.git/gh-stack`, a JSON file of its own.

This is the first of three ADRs on stacks: how they show (this one), making and unmaking them (ADR 73), and
rebasing and pushing them (ADR 74).

## Decision

### Roots

The main branch and the branches that are protected on GitHub are roots: long-lived branches that others are built
on, and that are never built on another one themselves.

- The protected branches are read with `gh api repos/{owner}/{repo}/branches?protected=true --paginate`, once per
  repository while jazygit runs: they seldom change. Reading them needs no more than read access.
- A repository on no GitHub, or one that protects nothing, has the main branch as its only root.
- Not: a setting listing the long-lived branches of a repository. Such branches are protected nearly always; a
  setting per repository (ADR 63) can come when that turns out not to hold.

### The parent of a branch

A local branch that is not a root has at most one parent, the first of:

1. the branch below it in its stack on GitHub, when its pull request is in one;
2. the base of its open pull request, `baseRefName`, which the read of the pull requests of the branches now asks
   for;
3. `branch.<name>.gh-merge-base`;

and only when that names another local branch. A branch made from a colleague's branch that is not checked out has
no parent.

- The parent on GitHub comes first: once a pull request is open, it says what the branch is built on, also when it
  was retargeted since, by hand or by an agent.
- The same parent is the base of ADR 20 and 46, through `BranchRef.bases`: the log of a branch counts the commits
  ahead of it, and `⇡`/`⇣` how far a branch is from it. In a stack, `⇣1` then says that the branch below moved on and
  the stack needs rebasing.
- A root has no parent, also when it has a pull request: the release pull request of `develop` into `main` does not
  make `main` the parent of `develop`.
- Not: reading `.git/gh-stack`. It is the private format of an extension in preview, and it says what the bases of
  the pull requests and the stacks on GitHub say. Who uses both stays in line through GitHub.

### What a stack is

A stack is a stack on GitHub with a local branch: all its pull requests, also the ones whose branch is not checked
out.

- Not: branches that are each other's parent, that GitHub does not know of as a stack. Which of them make a stack,
  and where it ends, is not for jazygit to guess: a stack is made by choosing its branches (ADR 73).

### The parent on line 2

A branch whose parent is not a root shows it on line 2 of its row, as `on feat/a`, dimmed, in the group of `⇣`
(ADR 46): after it, as `⇣1 on feat/a`, or alone when the branch is not behind.

```
 feat/b
   2d  ↑1  ⇣1 on feat/a  ✓ #97  ?
 feat/x
   1d  ✓ #98
```

- A branch built on a root shows nothing: that is what most branches are, on `main` or on `develop`, and it is
  what one expects.
- A branch in a stack on GitHub shows nothing: its `n/m` says it, and its parent is the leaf above it.
- The name is cut with an ellipsis at 20 columns, so the groups after it stay in view.
- `⇣` then counts against a base that is named, where it is not the one one expects.

### How a stack shows

A stack is one node of the list: a header of one line, with its branches under it, indented, as its leaves.

```
 ▾ stack #12 → main  3 PRs
     feat/a
       4d  ✓ #96  +  1/3
     feat/b
       2d  ⇣1  ● #97  ?  2/3
     feat/c
       1d  ✓ #98  3/3
 main
   9d  ↓3
```

- The header names the stack by its number, `stack #12`, then the base of the stack after `→`, and how many pull
  requests it has.
- The leaves come in GitHub's order: the bottom one, `1/n`, first, as GitHub lists them, so the list reads in the order
  they merge. Each is a branch row as ADR 45 has it, two lines.
- A pull request of the stack whose branch is not checked out is a leaf of one dimmed line, `#97 not checked out`; a
  merged one `#95 ◆ merged`. The stack then shows as it is on GitHub, with no gaps that look like none.
- A branch built on the top of a stack that is not in it is listed apart, with `on feat/c` on line 2: it is not in the
  stack until it is added (ADR 73).
- A stack starts open. `Enter` on its header closes or opens it; `space` does nothing there. Whether it is open is
  view state, as the cursors are, and is not kept.
- The content panel shows the log of the whole stack while the header is highlighted: from its base to its top.
- The header is one line, against two for every branch: it is a row of another kind.
- `/` matches the branches, and keeps the header of the stacks that have one that matches.
- The remote branches and the worktrees tabs do not change.

### Where it sits

- A stack is ordered among the branches by the one of its branches that saw activity last (ADR 41).
- The checked out branch still comes first: when it is in a stack, its stack comes first, whole.

## Consequences

- A stack reads as one thing, in the order GitHub has it, with its base named once.
- A long-lived branch such as `develop` is never part of a stack, and the branches built on it show nothing more.
- Branches an agent built on each other show as such, by `on`, but not as a stack until they are made one.
- ADR 46's `⇣` names its base when that is not a root, where ADR 46 named none.
- ADR 20's log of a branch and ADR 46's `⇡`/`⇣` go by the base of a branch's pull request on GitHub before
  `gh-merge-base`.
- The pull requests of the branches are read with one more field, `baseRefName`, and the protected branches with one
  request more per repository.
- The list has rows of one line next to rows of two.
