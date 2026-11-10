# 73. Making and unmaking a stack

Date: 2026-11-03

## Context

ADR 24 merges stacks of pull requests, and left making them out. A stack on GitHub is made from pull requests that
are open already, each one with the head of the one below it as its base, through the Stacks REST API, which
`gh-stack` uses too:

| Request | What it does |
|---|---|
| `POST repos/{owner}/{repo}/stacks` `{"pull_requests":[96,97]}` | makes a stack of two or more, the bottom one first |
| `POST …/stacks/{n}/add` `{"pull_requests":[98]}` | puts more on its top, and only there |
| `POST …/stacks/{n}/unstack` | takes them out: 204 when none are left, else the stack that is left |

GitHub refuses pull requests whose bases do not make one line from the base of the stack to its top.

Stacks get made in two ways. By hand, a branch at a time: a branch made from a branch, then a pull request for it
on its parent, which the create dialog proposes as base already (ADR 3). And by an agent working in the background,
which makes several branches on each other and opens a pull request for each with `gh pr create --base`. Either way
the pull requests form a line of branches, each the parent of the next (ADR 72), that is not a stack on GitHub until
someone links it.

Which branches of such a line make the stack is not always the whole line. A line goes down to a branch such as
`develop` that is no part of any stack, and up through branches that are not ready to be stacked; and a branch with
two children forks it.

## Decision

### The stack menu

`s` on a branch or on the header of a stack opens a menu of what can be done with its stack, with only the choices
that apply: make a stack, add to the stack, stack on…, unstack, and rebase and push the stack (ADR 74). `s` is free on the branches
pane, and a menu leaves the other free keys for later, as `u` does for the upstream.

### Making a stack

"Make stack…", on a branch that is in no stack on GitHub, lists the branches it could make a stack with, and the user
chooses its bottom and its top.

```
 Make a stack → develop
   feat/a          #96
   feat/b          #97   ← this branch
     feat/c        #98
       feat/d      no pull request
     feat/e        #99
```

- The list is the line of parents of the branch (ADR 72), down to the first root, which is not listed: it is the
  base of the stack. Then the branch; then the branches built on it, and on those, as a tree, indented.
- `b` makes the highlighted branch the bottom, `t` the top; `Enter` makes the stack. The branches between them are
  marked as they are chosen.
- The bottom is the branch or one below it, the top the branch or one above it: from the bottom to the top there is
  then one line, also where the tree forks. A bottom above the branch, or a top below it, is refused with why.
- At first the bottom is as far down as the branches have open pull requests, and the top is the branch: what is
  above it the user adds.
- One `POST stacks` with the pull requests from the bottom to the top.
- The parents can come from `gh-merge-base`, where the base of a pull request is another branch, often the root.
  Before making the stack, the menu says which bases it changes, `#97: develop → feat/a`, and asks; then
  `gh pr edit --base` changes them, from the bottom up, and the stack is made. The base of the bottom stays as it is:
  it is the base of the stack.
- A branch between the bottom and the top without an open pull request stops it: the menu names the branches without
  one, and makes nothing.
- A root is never in the list, so a long-lived branch such as `develop` is never stacked.
- Not: making stacks by itself, of the lines of branches it finds. Jazygit does not write to GitHub while it reads, a
  line of pull requests is not always meant as a stack, and where one ends only the user knows. An agent that wants
  its pull requests linked can do it itself, with `gh stack link` or the same request.

### Adding to a stack

"Add to stack…", on a branch of a stack on GitHub, lists the branches built on its top as "Make stack…" does, with the
top of the stack as the fixed bottom; the user chooses the top. One `POST stacks/{n}/add` puts them on, after the same
check of their bases and pull requests.

- Not: "submit stack", pushing branches and opening a pull request for each of them at once. The create dialog
  asks for a title, a body and a template per pull request; doing that for many is a design of its own. Agents open
  their pull requests themselves.

### Stack on…

"Stack on…" puts the pull request of the branch on another one: it lists the branches with an open pull request that
is in no stack or on the top of one, takes the chosen one's branch as the base of the pull request, and links it:
a new stack of the two, or added to the top of the stack.

- A pull request in a stack already is not stacked again: the menu does not offer "Stack on…" for it, and the user
  unstacks it first, with "Unstack". Unstacking it by itself would take its whole stack apart, which the user asks
  for, not a side effect of stacking.
- A branch built on the branch, through its parent or its parent's (ADR 72), is not listed: stacking on it would make
  the pull request the base of its own base, which GitHub takes for `gh pr edit --base` before it refuses the stack.
- Not: putting a pull request in the middle of a stack, or changing their order. GitHub only adds on top; it would
  take unstacking the stack and making it again.

### When creating a pull request

The create dialog gets a line `Stack on #96`, on when the base is a branch with an open pull request.

- Creating the pull request then links it: a new stack of #96 and it when #96 is in none, added on top when #96 is
  the top of one.
- When #96 is in a stack but not its top, the line shows, off, with why: `#96 is not the top of stack #12`. A line
  that is hidden leaves one wondering where it went.

### Unstacking

"Unstack" takes a stack apart on GitHub, whole, as `unstack` does, after asking:
`Unstack #12: 3 pull requests stay open, unlinked`.

- The pull requests and their bases stay as they are: the branches stay built on each other, and are made a stack
  again with "Make stack…".
- Merged and queued pull requests stay in the stack on GitHub; the question counts them apart, `2 merged or queued
  pull requests stay in the stack on GitHub`, and not among those that stay open, unlinked. Once GitHub lists the
  others out of the stack, it is taken as unstacked, while a queued one is still in it.

### Diverged branches

GitHub links a branch on top of one whose commits it does not have, but does not merge the stack then: it says the
branches have diverged, and offers to rebase the stack on its web page. Neither that state nor that rebase is in its
API. Each branch having all the commits of the one below is what ADR 46's `⇣` counts, against the parent of ADR 72,
as on the remote.

- A stack with a branch behind another branch of it shows `diverged: rebase it` on its header, and that branch shows
  its parent again, `⇣2 on feat/c`. A stack only behind its base is not diverged: GitHub merges that.
- "Make stack…" and "Add to stack…" mark such a branch, `⇣2 not on feat/c`, and ask before linking it, saying GitHub
  does not merge the stack until it is rebased onto the branch below.
- Linking does not rebase: rebasing rewrites the branches, can stop on conflicts and needs a force push, where linking
  only changes GitHub. Rebasing the stack is ADR 74's, which has to move such a branch onto the one below first.

### When GitHub refuses

A repository without stacks, as one where the preview is off, or GitHub Enterprise Server, answers these requests
with an error. It shows as any failed request does: in the command log and in the error popup, with what GitHub
says. The menu does not ask GitHub first whether it has stacks: that would be a request more for a rare case.

GitHub can refuse the stack after `gh pr edit --base` changed bases already. Those stay changed: undoing them could
fail as well, and the branches are built on each other as the new bases say. The command log lists the bases that
were changed, and the pull requests are read again, as after any failed operation that changes GitHub, so the
branches pane shows them.

## Consequences

- A line of pull requests, also one an agent made, becomes a stack on GitHub from a menu, with the user choosing where
  it starts and ends.
- Jazygit changes the bases of pull requests on GitHub when making a stack, after saying which.
- Nothing becomes a stack that the user did not choose.
- The Stacks REST API is used through `gh api`, as `merge-async` is (ADR 24).
- `.git/gh-stack` is neither read nor written: a stack made with `gh-stack` shows through GitHub, and one made in
  jazygit is not in `gh-stack`'s own list until `gh stack checkout` reads it from GitHub.

Amended by ADR 99: the stack menu checks out a stack, or updates its branches, also from the pull requests screen; that
entry is disabled, with why, where it does not apply.
