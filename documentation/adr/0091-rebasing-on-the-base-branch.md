# 91. Rebasing on the base branch

Date: 2026-11-07

## Context

`r` in the local branches rebases the checked out branch onto the highlighted one, after asking how, with one option:
`(s) simple rebase onto <highlighted>`. On the checked out branch itself it does nothing, as a branch is not rebased
onto itself. Yet that is where one is when the branch should catch up with what it is built on, and the branch it is
built on has to be looked up and highlighted first.

jazygit already knows what a branch is built on: its parent (ADR 72), else the main branch, which is the base the log
and `⇡`/`⇣` count against (ADR 20, 46).

## Decision

The rebase popup has a second option, on every row, the checked out one too:

```
Rebase 'feat/b'
(s) simple rebase onto feat/x
(b) rebase on base branch (feat/a)
```

- `(b)` rebases the checked out branch, as `(s)` does, also when another row is highlighted: the popup is about the
  checked out branch, and both options are where to put it. Nothing else is checked out.
- The base branch is the parent of the checked out branch (ADR 72), else the main branch. A root, the main branch or a
  branch protected on GitHub, has none.
- It is the local branch, never its branch on the remote: one rule, the same as `(s)`. A base that is behind its
  remote is pulled first; the fetch in the background does not move it.
- Both options are always shown; the one that does not apply is disabled. `(s)` on the checked out row, as a branch
  is not rebased onto itself; `(b)` without a base, labelled `(b) rebase on base branch (none)`; either while an
  operation runs on its branch. The popup opens also when neither applies: it says why `r` does nothing.
- On the checked out row the popup opens on `(b)`.
- When the highlighted branch is the base, both options do the same; both stay enabled.
- The rebase is the one `(s)` runs, `git rebase --autostash --keep-empty --rebase-merges --no-autosquash`, and `M`
  continues, skips or aborts it.
- In a stack, `(b)` rebases only the checked out branch, onto the branch below it. "Rebase stack" (ADR 74) stays in
  the menu of the stack.
- Not: the base on the remote, as "Rebase stack" takes it. That one makes what GitHub merges, and does so on purpose;
  this one puts a branch on what is here, as `(s)` does.

## Consequences

- The checked out branch catches up with its base with `r` `b`, from any row.
- `r` on the checked out branch opens a popup, where it did nothing.
- A base that is behind its remote is rebased on as it is here, until it is pulled.
