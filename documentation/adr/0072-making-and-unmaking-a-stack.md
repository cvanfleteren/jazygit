# 72. Making and unmaking a stack

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
the pull requests form a line that ADR 71 shows as a chain, and that is not a stack on GitHub until someone links it.

## Decision

### The stack menu

`s` on a branch or on the header of a stack opens a menu of what can be done with its stack, with only the choices
that apply: link the stack, stack on…, unstack, and rebase and push the stack (ADR 73). `s` is free on the branches
pane, and a menu leaves the other free keys for later, as `u` does for the upstream.

### Linking a chain

"Link stack" makes a stack on GitHub of a whole chain at once, from its bottom to its top.

- One `POST stacks` with the pull requests of the chain; or, for a chain on the top of a stack on GitHub (ADR 71),
  one `POST stacks/{n}/add` with only the ones on top.
- The chain is what ADR 71 shows: its parents can come from `gh-merge-base`, where the base of a pull request is
  another branch, often `main`. Before linking, the menu says which bases it changes, `#97: main → feat/a`, and asks;
  then `gh pr edit --base` changes them, from the bottom up, and the stack is linked.
- A branch of the chain without an open pull request stops it: the menu names the branches without one, and links
  nothing.
- Not: linking by itself. Jazygit does not write to GitHub while it reads, and a line of pull requests is not always
  meant as a stack. An agent that wants its pull requests linked can do it itself, with `gh stack link` or the same
  request.
- Not: "submit stack", pushing a chain and opening a pull request for each of its branches at once. The create dialog
  asks for a title, a body and a template per pull request; doing that for many is a design of its own. Agents open
  their pull requests themselves.

### Stack on…

"Stack on…" puts the pull request of the branch on another one: it lists the branches with an open pull request that
is in no stack or on the top of one, takes the chosen one's branch as the base of the pull request, and links it:
a new stack of the two, or added to the top of the stack.

- A pull request in a stack already is not stacked again: it is unstacked first.
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

- The pull requests and their bases stay as they are: the line stays a chain, and is linked again with "Link stack".
- Merged and queued pull requests stay in the stack on GitHub; the menu says so when there are any.

### When GitHub refuses

A repository without stacks, as one where the preview is off, or GitHub Enterprise Server, answers these requests
with an error. It shows as any failed request does: in the command log and in the error popup, with what GitHub
says. The menu does not ask GitHub first whether it has stacks: that would be a request more for a rare case.

## Consequences

- A chain of pull requests, also one an agent made, becomes a stack on GitHub with one choice of a menu.
- Jazygit changes the bases of pull requests on GitHub when linking, after saying which.
- The Stacks REST API is used through `gh api`, as `merge-async` is (ADR 24).
- `.git/gh-stack` is neither read nor written: a stack made with `gh-stack` shows through GitHub, and one made in
  jazygit is not in `gh-stack`'s own list until `gh stack checkout` reads it from GitHub.
