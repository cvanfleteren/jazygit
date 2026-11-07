# 90. Renaming a branch on the remote

Date: 2026-11-07

## Context

`R` renames a local branch with `git branch --move`, and nothing else. A branch that followed `origin/feature` and is
renamed to `topic` still follows `origin/feature`. Under git's default `push.default=simple`, a push of `topic` is then
refused, as the upstream has another name (ADR 40), and jazygit asks where to push it instead, offering `origin topic`.
Confirming that makes a new `origin/topic` and leaves `origin/feature` behind.

Most users keep a local branch and its remote branch under the same name, and take renaming a branch to mean renaming
both. By the time they push, the rename is forgotten and the refusal reads as an unrelated error. Renaming is when what
they want is known.

GitHub can rename a branch so that its pull requests follow, but jazygit only talks to GitHub through `gh`, which has
no command for it, and `gh pr edit` cannot change the head of a pull request. On the remote, a rename can only be a
push of the new name and a delete of the old one, and GitHub closes a pull request whose head branch is deleted. That
is left for later: a branch with a pull request is treated as any other.

## Decision

### When it is asked

- Renaming a branch asks whether to rename its upstream too only when that upstream is on a remote, has the branch's
  own name, and is there (`Branch.hasRemote`), and the branch is not the default one, whose branch on the remote every
  clone has (`RenameBranchUpdate.renamableUpstream`).
- Every other branch is renamed as before, without asking: one without an upstream, one whose upstream is gone or is a
  local branch, and one that follows a branch of another name, like `feature` made from `origin/main`. Not: renaming
  such an upstream, which would rename `main` on the remote. This is the rule nuking follows for the remote branch it
  deletes (ADR 43).

### The dialog

- The new name is typed first, as before. Then a `ChoiceDialog`, `RenameUpstreamPopup`, titled `Rename feature to
  topic`, names both branches on the remote:
  - `l`: rename only the local branch, which keeps following `origin/feature`;
  - `r`: rename on the remote too: `origin/feature` becomes `origin/topic`.

  Not: choosing first, whose options could not name the new branch on the remote.
- `l` comes first, so a confirmation without a look renames only locally: renaming on the remote is seen by everyone
  who has the branch, and by what refers to it by name, like CI.
- Escape cancels the whole rename, the local one too.
- Not: a third option to rename locally and stop following the upstream, which is rare and the upstream menu does.

### Renaming on the remote

- `r` runs `RenameBranchCmd` with the upstream, one operation (`Remotes.renameUpstream`):
  1. `git branch --move -- feature topic`, as for `l`;
  2. `git fetch --prune --no-write-fetch-head origin`, so that what is renamed is what is there now;
  3. `git push --atomic --force-with-lease=refs/heads/topic: --force-with-lease=refs/heads/feature origin
     refs/remotes/origin/feature:refs/heads/topic :refs/heads/feature`;
  4. `git branch --set-upstream-to=refs/remotes/origin/topic -- topic`.
- The push pushes the commit of the remote-tracking branch, not of the local branch: the branch on the remote is
  renamed as it is, and commits only the local branch has stay unpushed, for the next push. Not: pushing the local
  branch under the new name, which would push them along, or be refused when the local branch is behind or diverged.
- `--atomic` renames both or neither. The first lease refuses a new name that is taken on the remote; the second
  refuses an old one that moved since the fetch, as when a teammate pushed to it. Either way the remote stays as it was.
  A new name already on the remote at the very same commit is no conflict, and git renames.
- The local branch is renamed first. When the remote steps fail, it is renamed and still follows the old upstream, as
  with `l`, and the error (`GitError.UpstreamNotRenamed`) says so, with the commands that rename it there. Not: the
  remote first, which a refused local rename, of a name that is taken, would leave with a new branch on the remote
  and none here.
- Confirming checks that the upstream is still the one the dialog named; when not, nothing starts.
- The operation guards the remote refs (`Guard.REMOTE_REFS`), as a push or a fetch does, and reads the pull requests
  again afterwards (`changesGitHub`).

## Consequences

- A rename that also renames on the remote is one keystroke more than before, and only for the branches it applies to.
- After a failed rename on the remote, there is no way in jazygit to try again: renaming the branch to the name it has
  does nothing. The error gives the commands. Not yet: an action on the upstream menu that renames the upstream after
  the fact, which would also serve branches renamed with `l`.
- Renaming on the remote closes a pull request whose head branch it deletes. Not yet: treating a branch with a pull
  request otherwise, or warning when deleting its head branch.
