# 148. Adding a branch without a pull request to a stack

Date: 2026-11-18

## Context

"Add to stack…" (ADR 73) lists the branches built on the top of a stack, through their parent (ADR 72), and puts
those with an open pull request on it. A branch an agent made with `git checkout -b` on the top, and pushed, has
neither: no pull request, and no `gh-merge-base` naming the top. The dialog then lists the top alone, and says
`Choose a top above the top of the stack`, while the branch to add is right there in the branches pane.

What does add it is another dialog: creating its pull request with the top as its base, where `Stack on #549` is ticked
(ADR 73). Nothing in "Add to stack…" leads there.

The dialog also starts with the top of the stack as the chosen top, which adds nothing: `t` has to be pressed every
time, also when there is one branch above it.

ADR 73 left out opening pull requests from the stack menu: the create dialog asks for a title, a body and a template
per pull request, and doing that for many at once is a design of its own.

## Decision

### The branches built on the top, by their history

"Add to stack…" also lists the local branches that are built on the top by their history: those that have its last
commit and commits of their own, that are no root, and that have no parent. It reads them when it is chosen, where
any local branch has no parent, with one `git for-each-ref --contains` for the top, then one per branch found, which
places a branch on the nearest of them it has, as the parents place the others. When they cannot be read, the dialog
lists the branches placed by their parents.

- A branch that has a parent stays where its parent puts it, also when its history says otherwise: the parent is what
  the user or GitHub said (ADR 72, ADR 143).
- A branch at the same commit as the top has nothing to add yet, and is not listed.
- Not: the tree of the branches pane built from history too. It is read for every branch on every refresh there; here
  it is read once, when it is asked for.

### The top chosen at first

The top chosen at first is as far up as the branches above the top of the stack go in one line, while they have open
pull requests; when the first branch above has none, it is that one. Where the line forks, the top of the stack stays
chosen, and the user chooses one. Enter then adds what is there to add, where it used to say nothing was chosen.

### A branch without a pull request

When the lowest branch added has no open pull request, the dialog says `blame-state has no pull request: Enter
creates one on blame-read` in place of a refusal, and Enter asks, titled `Add blame-state to stack #550`, to create
it on `blame-read` and put it on the top of the stack. Confirming it records the top as the parent of the branch, in
`branch.<name>.gh-merge-base`, and opens the create dialog of ADR 18 for it, pushing it first where that is needed
(ADR 25): its base is the top, offered first as its parent, and `Stack on #549` is ticked. Creating it puts it on the
stack.

- One at a time: a branch without a pull request above another one that is added is refused, as before, naming it.
  The branches below it are added first, then it is the lowest.
- Recording the parent keeps it where the user put it: it shows built on the top in the branches pane before its pull
  request is open, and the create dialog offers the top first, also after pushing it.
- The question comes from the `github` group: the stack menu is in `branches`, which cannot see the create dialog
  (ADR 136). The stack menu opens the question; the `github` group draws it and does what confirming it says. It also
  says what is about to happen before a pull request is made on GitHub.
- Not: creating the pull request without asking for its title and body. That is ADR 73's "submit stack", which stays
  out.

### From the branch itself

"Stack on…" is offered on a branch without an open pull request too, when it is no root and in no stack. It lists the
branches it can be stacked on as before, its parent first when that is one of them. Choosing one asks, as above, to
create its pull request on that branch: on the top of its stack, or a new stack of the two.

### When nothing is built on the top

When no branch is built on the top, the dialog says so, naming it: `No branch is built on blame-read`. The refusal of
a top that is the top of the stack says what to choose: `Choose a branch above blame-read as the top`.

## Consequences

- A branch made with plain git on the top of a stack is added from "Add to stack…" or "Stack on…", in two dialogs:
  the question, then the pull request's title and body.
- Opening "Add to stack…" takes a git process for the top and one per branch found, before the dialog shows, where
  a branch has no parent.
- Enter adds a line of branches without `t` first.
- A branch is placed by history only in this dialog; elsewhere it still shows no parent until its pull request is
  open, or the question recorded it.

This amends ADR 73: "Add to stack…" lists branches by their history, starts with a top chosen, and leads to creating a
pull request; "Stack on…" is offered on a branch without one.
