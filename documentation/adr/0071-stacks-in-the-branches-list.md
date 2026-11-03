# 71. Stacks in the branches list

Date: 2026-11-03

## Context

A stack of pull requests (ADR 24) is a line of branches, each one built on the one below it, the bottom one on a
base such as `main`. The branches pane shows that a branch is in a stack only by `2/4` on line 2 of its row (ADR 45);
the branches of a stack are listed apart from each other, by when they last saw activity (ADR 41), between the
branches that have nothing to do with them.

Stacks are also made before GitHub knows of them: a branch made from a branch records its parent in
`branch.<name>.gh-merge-base` (ADR 3), and an agent working in the background opens pull requests with
`gh pr create --base <the branch below>`, which GitHub does not link into a stack until it is asked to (ADR 72).

GitHub's stacks are linear: every pull request has one below it and at most one above it. `gh-stack`, GitHub's
extension for them, keeps its own list of the branches of a stack in `.git/gh-stack`, a JSON file of its own.

This is the first of three ADRs on stacks: how they show (this one), making and unmaking them (ADR 72), and
rebasing and pushing them (ADR 73).

## Decision

### The parent of a branch

A local branch has at most one parent, the first of:

1. the branch below it in its stack on GitHub, when its pull request is in one;
2. the base of its open pull request, `baseRefName`, which the read of the pull requests of the branches now asks
   for;
3. `branch.<name>.gh-merge-base`;

and only when that names another local branch that is not the main branch. A branch made from a colleague's branch
that is not checked out has no parent, and stops a stack there.

- The parent on GitHub comes first: once a pull request is open, it says what the branch is built on, also when it
  was retargeted since, by hand or by an agent.
- The same parent is the base of ADR 20 and 46, through `BranchRef.bases`: the log of a branch counts the commits
  ahead of it, and `⇡`/`⇣` how far a branch is from it. In a stack, `⇣1` then says that the branch below moved on and
  the stack needs rebasing.
- Not: reading `.git/gh-stack`. It is the private format of an extension in preview, and it says what the bases of
  the pull requests and the stacks on GitHub say. Who uses both stays in line through GitHub.

### What a stack is

- A stack on GitHub with a local branch: all its pull requests, also the ones whose branch is not checked out.
- Two or more local branches, each the parent of the next, that are not in a stack on GitHub: a chain, a stack that
  GitHub does not know of yet.
- A chain on the top of a stack on GitHub is part of that stack: its branches are leaves above the linked ones,
  without a `n/m`, until they are linked too (ADR 72).
- A stack is linear, as GitHub's are. A branch that is the parent of two ends a chain: each of its children starts a
  stack of its own, on it. A chain is never shown as a tree of branches.

### How it shows

A stack is one node of the list: a header of one line, with its branches under it, indented, as its leaves.

```
 ▾ stack #12 → main  3 PRs
     feat/a
       4d  ✓ #96  +  1/3
     feat/b
       2d  ⇣1  ● #97  ?  2/3
     feat/c
       1d  ✓ #98  3/3
 ▸ feat/x … → main  2 branches
 main
   9d  ↓3
```

- The header names a stack on GitHub by its number, `stack #12`, and a chain that is not one by its bottom branch,
  `feat/x …`, dimmed; then the base of the stack after `→`, and how many pull requests or branches it has.
- The leaves come in GitHub's order: the bottom one, `1/n`, first, as GitHub lists them, so the list reads in the order
  they merge. Each is a branch row as ADR 45 has it, two lines.
- A pull request of a stack on GitHub whose branch is not checked out is a leaf of one dimmed line,
  `#97 not checked out`; a merged one `#95 ◆ merged`. The stack then shows as it is on GitHub, with no gaps that look
  like none.
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
- A chain of branches an agent made shows as a stack before anything links it on GitHub.
- ADR 20's log of a branch and ADR 46's `⇡`/`⇣` go by the base of a branch's pull request on GitHub before
  `gh-merge-base`.
- The pull requests of the branches are read with one more field, `baseRefName`.
- The list has rows of one line next to rows of two.
