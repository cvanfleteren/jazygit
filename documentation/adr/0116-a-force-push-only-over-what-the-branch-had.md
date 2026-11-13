# 116. A force push only over what the branch had

Date: 2026-11-13

## Context

Pushing a branch (`P`) decides against its upstream of the same name (ADR 40):

- ahead only: it pushes;
- diverged: it asks to force push, with `--force-with-lease`;
- behind only: it says the branch is behind, so there is nothing to push, and to pull it instead.

Dropping the last commit of a pushed branch leaves it behind only. Pulling brings the commit back, so jazygit had no
way to push what the user meant: the user had to leave for the shell.

Offering the force push on the counts alone is not safe either. A branch is behind just as much when someone else,
or an agent, pushed to it. jazygit fetches in the background, so by the time the user pushes, `origin/<branch>` is
where the remote is: the lease of `--force-with-lease` holds, and the force push drops their commits. That was already
so for a diverged branch: one with a commit of its own, behind what someone else pushed and jazygit fetched, was
offered a force push that dropped theirs.

ADR 74 settled this for stacks: git is asked whether the branch's reflog has the commit on the remote, as
`--force-if-includes` asks, and the stack push passes `--force-if-includes`. A branch rewritten here, by dropping,
amending or rebasing, once had what it was pushed as; one behind what someone else pushed never had that.

## Decision

- A branch behind its upstream of the same name, diverged or behind only, is force pushed only when it once had the
  commit its upstream is at. Before anything is offered, git is asked (`History.everHad(branch, upstream)`, the
  question of ADR 74).
  - It had it: the force push is offered, as for a diverged branch before. For a branch behind only, the popup says
    there is nothing to push, and that a force push drops the commits of the remote that the branch does not have.
  - It never had it: no force push is offered. The error says the upstream has commits the branch never had, and to
    pull it: instead, when it is behind only; first, when it diverged, so that a force push does not drop them.
  - Not: offering a force push on the counts alone, with a warning, which drops what someone else pushed once it is
    fetched; nor refusing a branch behind only, as before, which leaves a dropped commit no way out.
- The answer is checked against the branch as the model has it then: when a refresh changed the branch meanwhile, git
  is asked again. Confirming checks it again too: a popup that no longer says what the force push drops is asked
  anew.
- Every force push of a branch (`PushMode.FORCE_WITH_LEASE`) passes `--force-if-includes` with `--force-with-lease`:
  git refuses what was fetched after the question was answered, and the force push after moving commits off a
  pushed branch is held to the same rule. Not: the lease alone, which a background fetch makes hold.
- A branch whose upstream has another name stays as ADR 40 says: it is not offered a force push.

## Consequences

- Dropping or amending a pushed commit, then pushing, is a force push the user confirms, as rewriting it with a
  rebase was.
- What someone else pushed is not overwritten from the branches pane, whether it was fetched or not: the user pulls
  first, or goes to the shell to force it anyway.
- A branch whose reflog expired since it had its remote tip no longer shows that it had it, and is told to pull. That
  errs on the side of the commits on the remote.
- Asking costs one more git command before the popup, run only for a branch behind its upstream.
- ADR 40 is amended: a branch behind only is no longer always told to pull.
