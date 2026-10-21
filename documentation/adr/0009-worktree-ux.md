# 9. Working with worktrees

Date: 2026-10-21

## Context

The worktrees can be listed, added for a local branch, switched to and removed, and the branch operations refuse a
branch that another worktree has checked out. Used in practice, it feels off:

- Switching to a worktree starts the app over: `JazygitApp.show()` starts a new program from `Model.initial`, and
  `startRuntime` focuses the files. The branches pane is back on the local branches, so the keys meant for the
  worktrees go to the files: `d` opens "Discard changes". Reopening the repository after setting the GitHub account
  does the same.
- Several keys do nothing and say nothing: `d` on the main worktree or the one shown, space on the one shown, `w` on
  the checked out branch.
- A locked worktree is asked to be removed, and then git refuses. A worktree whose directory is gone can be switched
  to, which fails with "Not a git repository". Neither can be unlocked or pruned.
- After adding a worktree, the worktree shown stays highlighted, not the new one.
- The header names the directory of the worktree (`jazygit-wt-test-a → wt-test-a`), not the repository, and in a
  bare layout (`proj/.bare` with the worktrees next to it) only the worktree (`feat-x → feat-x`). Every worktree that
  was shown is a recent repository of its own.
- In a bare layout no worktree is the main one, so a new worktree starts out without a location.
- A worktree can only be added for an existing local branch.

## Decision

The work is done in the slices below, each a change of its own, in this order.

### 1. Switching keeps the place

`Repositories.switchTo` takes a `Landing`: the tab of the branches pane and the pane that has the focus. The new
program starts with them in its `RepoView` (`branchesTab`, `focusedPane`), and `startRuntime` focuses
`PopupSlot.focusedPane(model)` instead of the files; the focus itself stays in the `FocusManager` (ADR 2).

- Switching to a worktree, from the worktrees or from the popup offering the worktree of a branch to check out,
  lands on the worktrees, focused.
- Reopening the repository after a change of settings keeps the tab and the pane that were shown.
- Switching to a recent repository starts on the files, as now.

### 2. Saying why nothing happens

A key that cannot act on what is highlighted says why, as an `AppError`, as `NoUpstream` does:

- `d` on the main worktree: it holds the repository and cannot be removed.
- `d` on the worktree shown: switch to another one first.
- `d` on a locked worktree: it is locked, with the reason; said before the confirmation, not after git refuses.
- space on the worktree shown: it is shown already.
- `w` on the checked out branch: it is checked out here. Slice 6 gives that key something to do.

### 3. Locked and missing worktrees

- `Worktree` keeps the reason it is locked (`Option<String> locked`, the reason empty when none was given) and the
  panel shows it.
- Space on a worktree whose directory is gone says so, instead of trying to open it.
- `p` on the worktrees runs `git worktree prune`, which forgets the worktrees whose directory is gone. Nothing is
  lost, so it is not confirmed; it does nothing, and says so, when none is missing.
- `L` on the worktrees locks the highlighted one (`git worktree lock`), or unlocks it when it is locked. The main
  worktree cannot be locked.

### 4. After adding a worktree

The worktrees are shown with the new one highlighted, and a popup asks whether to switch to it. Confirming switches
as slice 1 does; cancelling stays.

### 5. Repository identity and the bare layout

The name of the repository is read from `git rev-parse --git-common-dir`, as lazygit does: the name of the directory
holding the common git directory (`…/jazygit/.git` → `jazygit`, `…/proj/.bare` → `proj`). A bare repository named
`app.git` is `app`, where lazygit would take the directory above it.

- The header shows a linked worktree as lazygit does: `jazygit(jazygit-wt-test-a) → wt-test-a`, the worktree name
  in the dim style. The main worktree shows as now, `jazygit → main`.
- Without a main worktree, a new worktree starts out next to the one shown, named after the branch only
  (`…/proj/feature-login`), as worktrees are laid out next to a bare repository.
- The recent repositories hold one entry per repository: opening a worktree replaces the entry of any other
  worktree of the same repository, which is told from the files as `HeadFile` reads HEAD (the `.git` file, then the
  `commondir` of the git directory it points to). The list names the entry as the header does. Removing the worktree
  an entry names makes it name the worktree shown instead, so the repository does not drop off the list.

### 6. A new branch in a new worktree

`W` on a local or remote branch asks for the name of a new branch starting there, as `n` does, then where its
worktree goes, and runs `git worktree add -b <name> <location> <start>`; from a remote branch the new branch
follows it, as checking it out does. `w` on the checked out branch starts the same, from that branch.

### Along the way

- Checking out a pull request whose branch another worktree has checked out offers to switch to that worktree, as
  checking out the branch does.
- A default location that is taken already gets a number appended (`app-a-b-2`), as `a/b` and `a-b` map to the same
  one.

## Consequences

- Each slice has its update tests next to the existing `WorktreeUpdateTest`; the git commands (prune, lock, unlock,
  `add -b`, the common git directory) are tested against a real repository, as `CliWorktreeTest` does.
- `Repositories.switchTo` changes signature; its three callers say where to land.
- The recent repositories file keeps holding paths; which repository a path belongs to is read when it is opened,
  not stored.
- The model gains nothing for the focus beyond `focusedPane`, which it has already.
