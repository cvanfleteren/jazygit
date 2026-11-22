# 182. Another worktree of the repository opens with what the worktrees share

Date: 2026-11-22

## Context

Switching to another worktree, from the worktrees pane or by checking out a branch that another worktree has
(ADR 68), opens it as `ctrl+r` opens another repository: the program is stopped, and a new one starts from an empty
model, as if the app had been started there. The forges decide again which of them the repository is on (ADR 177),
each turned off until then, a fetch runs at once, and GitHub and Forgejo read their pull requests, checks and builds
from nothing. Meanwhile the branches pane shows none of it: the badges blank out and come back a second or more later,
and gh and fj are asked everything again.

Most of it holds for the other worktree as it was. The worktrees of a repository share its common git directory: the
remotes and their URLs, the git configuration that names the home of the pull requests (`remote.<name>.gh-resolved`,
`jazygit.forgeHome`), the branches and the remote-tracking branches a fetch brings. What the forges said of the
branches, and the forge the kernel decided from the remotes, are the same there. Only what is the worktree's own,
its HEAD, status, files and log, is to be read afresh.

## Decision

- **Another worktree of the same repository opens with what the worktrees share.** The same repository is one whose
  worktrees share the common git directory the one shown had; one whose common git directory cannot be found is
  another repository. Another repository opens from nothing, as before, and so does the worktree shown when it is
  opened anew, as a setting that only applies to a repository shown anew opens it (the gh executable, GitHub's
  account): it is to be read from nothing, the forges deciding again with what the setting changed.
- **A part says what of it another worktree starts from** (`Part.carrying`), as it says what it reads again or what it
  states (ADR 158): what it read of what the worktrees share, and none of what only the program before had, as the
  reads it was running, whose answers go to that program. A part that says nothing is the worktree's own and starts
  from its initial value. The kernel carries the parts (`Carried`) without naming any.
  - The kernel's forge home (ADR 177) carries once it was decided, with what the forges claimed, so the settings
    panel can still ask; the forges are not asked again, and the switches of the features stay as they were. A
    home not decided yet, or a question the user put off, is not carried: the forges decide again, and the question
    is asked again, as at any opening.
  - GitHub carries what it read; not the reads to start again, nor that it is asking how a merge is doing, so a merge
    still pending is asked about again from the new worktree.
  - Forgejo carries how the builds went and its pull requests; not the builds being read, which are read again.
- **The last fetch carries too, and the other worktree does not fetch as it opens**: the worktrees fetch into the same
  remote-tracking branches, so it waits for the next tick of the fetch, as the line at the bottom says. A worktree of
  a repository never fetched from still fetches at once.
- Not: one program for every worktree, switched from one to the other. A program reads one worktree, and its commands
  run in it; what it knows of the worktree itself would have to be told apart from what it knows of the repository in
  every part of the model.

## Consequences

- Switching worktrees shows the pull requests, checks and builds of the branches at once, and asks gh and fj nothing
  until the next tick, which reads what changed on the forge as at any other tick.
- A forge that would decide otherwise in the other worktree, through git configuration of that worktree alone
  (`extensions.worktreeConfig`), keeps the decision of the one before; the settings panel asks again.
- A part that is added carries nothing until it says so: a repository-wide part that forgets to stays correct, but is
  read again on every switch.
- A read or a merge that was running when the switch came is answered to the program before, which nobody looks at;
  what it would have brought is read again in the new worktree, on the next tick at the latest.
