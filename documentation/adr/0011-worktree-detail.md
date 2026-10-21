# 11. More detail in the worktrees list

Date: 2026-10-21

## Context

A worktree row shows its directory, what it has checked out, and whether it is the main one, locked or gone. It
does not show whether a worktree has work in it that is not committed, nor whether its branch is ahead of or
behind its upstream, which are what one looks at the list for. Ahead and behind are already known: they come with
the local branches. Whether a worktree has changes is not: it takes a `git status` in each of them.

## Decision

### Ahead and behind

A worktree on a branch shows that branch's ↑n ↓n, or "gone", after its name, as the branch rows do (`sync`). They
are those of its upstream, taken from the loaded local branches: no git command is added. A worktree on no branch
shows nothing.

### Whether a worktree has changes

- A worktree with changes shows a `●` in the `unstaged` style after its directory name:
  `* app-login ● feature/login ↑2 ↓1 main locked`. The marker belongs to the directory, ↑/↓ to the branch.
- Changes are what makes `git worktree remove` refuse without `--force`: staged, unstaged and untracked files, not
  ignored ones. They are read with `git status --porcelain -z --untracked-files=normal`; any output is a change. No
  counts are shown.
- The worktree shown takes this from the status already loaded. A worktree that is gone (prunable) is not read.
- `Cmd.LoadWorktreeChanges` reads the others, one after the other in one job, into `Repo.worktreeChanges`, a map
  from a worktree's path to whether it has changes, beside the list of worktrees, which stays one cheap
  `git worktree list`. A worktree missing from the map is not known and shows no marker, so the list shows at
  once and the markers follow. A worktree whose status cannot be read is left out quietly: no error, no retry.
- A worktree that cannot be read in a read that otherwise succeeds is left out, so it loses what was known of it.
  When the whole read fails, what was read before stays.
- It is read only while the branches pane shows the worktrees, whether the pane has the focus or not: right after
  switching to them, and then by a tick at least 3 s after the last read finished, failed or not. It is not
  started again while one is still running.
- It runs in the background, beside fetching and the GitHub loads rather than on the thread of the other loads
  and of the user's operations. In a big repository, a git status per worktree can take longer than a tick, and
  it would hold up staging, committing and diffs in the worktree shown.
- The branch rows of branches checked out in another worktree get no marker, as the changes are only read while
  the worktrees are shown. Removing a worktree goes as before: git's refusal, not the marker, which may be a tick
  behind, decides whether removing it anyway is offered.

## Consequences

- While the worktrees are shown, one `git status` per other worktree runs every few seconds, in the background.
  A marker can be a few seconds behind.
- Comparing a worktree's branch with the default branch, rather than its upstream, is left for later.
