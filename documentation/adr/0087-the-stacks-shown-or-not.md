# 87. The stacks shown or not

Date: 2026-11-06

## Context

ADR 72 lists a stack of pull requests on GitHub as one node of the local branches: a header, with its branches under
it in GitHub's order. That reads well while one works on the stack. It reads less well when one is after the branch
one worked on last: a stack sits where the one of its branches that saw activity last does, and its other branches
come with it, also when they have not been touched in weeks. Nor can a branch of a stack be told apart by when it last
saw activity, which is how every other branch is ordered (ADR 41).

## Decision

`S` in the local branches, on a branch or on the header of a stack, switches between showing the stacks and listing
their branches as any other branch.

- Without the stacks, the list is as it was before ADR 72: every local branch is a row of its own, by when it last saw
  activity, with no headers and no rows for the pull requests of a stack whose branch is not checked out.
- A branch of a stack still says it is one, by its `n/m` on line 2 (ADR 45); its parent is still not named, as ADR 72
  has it.
- The stacks show at first. Whether they show is view state, as whether a stack is open is (ADR 72), and is not kept:
  it is in `RepoView`, and goes with the repository screen.
- Which stacks are closed stays while they do not show, so they come back as they were.
- One key for both: on a header the keys of a branch do nothing (ADR 72), and the key has to work there, where the
  stacks are what one wants out of the way.
- Not: a setting. Showing the stacks is what ADR 72 decided on; the switch is for a moment. A setting per repository
  (ADR 63) can come when one turns out to want it off for good.

## Consequences

- The branches of a stack can be listed among the others, by when they saw activity, and back as a stack, with one key.
- `S` is taken in the local branches.
- The content panel, the menu of a stack (`s`) and what a stack does on GitHub do not change: they go by the branch,
  not by how it is listed.
