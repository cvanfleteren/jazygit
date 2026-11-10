# 99. Checking out a stack

Date: 2026-11-10

## Context

A stack on GitHub shows in the branches pane once one of its branches is here (ADR 72); its pull requests whose
branch is not show as `#97 not checked out`. Rebasing and pushing a stack (ADR 74) refuse such a stack: every open
branch has to be here. A stack someone else made has none of its branches here, and shows only on the pull requests
screen.

Getting its branches here took checking out each pull request in turn (`space`, `gh pr checkout`), which moves HEAD
and changes the files each time; and fast-forwarding each branch that fell behind its branch on the remote, one by one.
A worktree has one HEAD: checking out a stack means having a branch here for each of its pull requests, and one of
them checked out.

`gh stack checkout`, of the `gh-stack` extension, does that; ADR 72 does not rely on that extension.

## Decision

### What it does

"Check out the stack", from the stack menu, makes a local branch for each open pull request of the stack that has none
here, following its branch on the remote, and fast-forwards those that are behind it; then checks out the one the user
chooses, or none. It does not touch the working tree until that checkout, and never rebases, merges or pushes.

- Once every open pull request has a branch here, the same entry reads "update the stack": it fast-forwards what is
  behind. It is one action under two names, and the help says both.

### Where

- The stack menu (`s`, ADR 73) on the header of a stack or on any of its branches.
- `s` on a pull request of the pull requests screen opens the same menu for its stack: only for a pull request of the
  repository shown, as checking one out is (ADR 68), and in a stack. That is where a stack with no branch here shows.
  The menu, and what it asks about a stack, give the focus back to where it was.
- The entry is never hidden: it is disabled, with why, when there is nothing to do. Whether there is is told without
  reading anything: every open pull request has its branch here, at the commit GitHub has its branch at
  (`headRefOid`, read since ADR 74). A branch whose commit on GitHub is not known may be behind: the entry is then
  enabled.

### The branches

- One `git fetch --no-write-fetch-head <remote> +refs/heads/<branch>:refs/remotes/<remote>/<branch>…` fetches only the
  branches of its open pull requests, before anything is asked. It touches only remote-tracking branches, so cancelling
  after leaves nothing to undo. The remote is the one the branches here of the stack follow, as they are pushed to one
  (ADR 74); else `origin`.
- The branch of a pull request is the local branch that follows its branch on the remote, also under another name;
  else the one of its name.
- A pull request whose branch is in a fork (`isCrossRepository`, now read for the pull requests of a stack) is left
  out: its branch is not on that remote.
- Merged and closed pull requests are left out, and named on one line.

### What is asked

Once fetched, a dialog says, per open pull request from the bottom up, what happens to its branch:

```
 #96  feat/a  fast-forwarded by 2 commits
 #97  feat/b  new, following origin/feat/b
 #98  feat/c  kept: 1 commit not on origin/feat/c
 Left out, merged or closed: #95

 Then check out:
 (t) the top, feat/c
 (h) #97, feat/b  ← enter
 (n) nothing, stay where you are
```

- **new**: none is here; it is made, following its branch on the remote, with the base of its pull request recorded as
  its parent in `branch.<name>.gh-merge-base` (ADR 72), which is kept when the stack is taken apart.
- **fast-forwarded**: it is behind, and has nothing of its own.
- **up to date**.
- **kept**, with why: it has commits that are not on the remote; it is behind but checked out here, to be pulled as any
  checked out branch is; it is behind but checked out in another worktree, under whose files it would move. The other
  branches go ahead.
- **refused**: a local branch of the name of the pull request's branch follows nothing, or follows another branch. It
  may be another branch altogether: nothing is done until it is renamed or deleted. Not: taking it for the pull
  request's.

`t` checks out the top, `h` the pull request the menu was opened on, when it was opened on one, `n` nothing; Enter the
pull request it was opened on, else the top. On the header of a stack, `h` is not offered.

### Moving them

- One `git fetch --atomic --no-write-fetch-head . refs/remotes/<remote>/<branch>:refs/heads/<branch>…` makes and
  fast-forwards them, all of them or none: without `+`, git refuses what is no fast-forward, and it refuses a branch
  checked out in any worktree. A branch that got commits of its own since the dialog opened refuses all of it; nothing
  is half done. Then `git branch --set-upstream-to` and the parent for each branch made.
- Not: `git update-ref --stdin` with the old value of each, which jazygit has no way to write a command's input to; the
  fetch gives the same all-or-nothing, and checks fast-forwards itself.
- Not: `gh pr checkout` per pull request, which moves HEAD and changes the files each time.

### Checking one out

The branch chosen is checked out as one from the branches pane is: through the checkout of ADR 68, which asks where in
a linked worktree, offers to stash what is in the way, and offers to switch to the worktree that has it checked out.
When that checkout is refused or cancelled, the branches made stay.

### Afterwards

A notice says what was done: `Stack #12: 2 new, 1 fast-forwarded, 1 kept as they were.` As any notice, it gives way
to what comes next, the checkout that follows included; the command log keeps the commands. A failed fetch or a
refused move shows as any failed operation does.

## Consequences

- A stack someone else made is here, its branches each following theirs, in one go from the pull requests screen.
- A stack can be rebased and pushed (ADR 74) once it is checked out.
- Fetching the branches of a stack is asked for, not done in the background; the dialog then tells what is on the
  remote now.
- The menu of a stack opens from the pull requests screen too, with rebasing, pushing and unstacking in it.
- The pull requests of a stack are read with one field more, `isCrossRepository`.
- Amends ADR 73: the stack menu has an entry that is disabled where it does not apply, where its other entries are
  left out.
