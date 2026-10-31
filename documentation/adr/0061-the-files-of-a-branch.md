# 61. The files of a branch

Date: 2026-10-31

## Context

The pull request dialog lists the files a pull request changes, with its review remarks, in the files pane (`f`,
ADR 55). Getting there from a branch took two keys and a read of the details of the pull request, which the dialog
waits for: `o`, then `f`. A branch without a pull request had no way to list what it changes at all, though jazygit
knows what it is compared with: the base of ADR 20, which the commits ahead of it (`b`) and the counts in the branches
pane (ADR 46) already go by.

The files of a pull request are not read from GitHub: GitHub gives its base and head commits, and git diffs those,
`<base>...<head>`, fetching them first when they are missing (ADR 55). Those commits are what its review threads, and
the comments made on its lines (ADR 60), are anchored to. The list of the open pull requests the branches show
(ADR 58) did not have them; only the details read by the dialog did.

## Decision

`f` on a local branch, in the branches pane, lists the files the branch changes in the files pane, as `F` does for a
commit, and moves the focus there; Esc gives it back to the branches pane.

### A branch with an open pull request

It lists the files of the pull request, as `f` in its dialog does: at its base and head commits, with its review
remarks and comments on lines.

Those commits come with the list of the open pull requests: `SummaryFragment` also reads `headRefOid` and
`baseRefOid`, and a `PullRequest` keeps them, with its repository and the host of its URL, which the fetch of its
commits needs. So the files open at once, with no dialog in between. The list is read every so often, so the commits
can be behind; that does no harm, as the review threads read with the files tell where the pull request is now, and its
files are then read again at those commits (ADR 55, 60).

When the list has no commits for it, as for a pull request whose branch is gone, the dialog opens and reads the
details, as `o` does, and goes on to the files once they are read. When they cannot be listed, as when reading them
failed, the dialog stays, saying so.

### Any other branch

A branch without an open pull request, also when its pull request was merged or closed, or when the pull requests are
not known, as when reading them failed, lists the files it changes against its base: the base of ADR 20, the branch it
was made from, else the main branch, of each the one on `origin` before the local one, as `b` has it.

- It is diffed as a pull request is, `<base>...<branch>`: the branch against where it forked from its base. Commits on
  the base since do not show; a base that was not fetched for a while changes nothing, unless the base has commits
  of the branch by now, as when a branch it was stacked on was merged.
- Only what is committed is listed: the files of the working tree have the files pane already.
- It stays at the commits the branch and its base were at when it was opened, as the files of a commit do; `f` again
  reads them anew.
- Its title names the base: `Files of feature · vs origin/main`. Then a base that is not the one meant shows.
- It has no review remarks: `t`, `c`, `R` and commenting on lines do nothing.
- The main branch itself has no base to be compared with, nor has a branch without one; that is said, as the divergence
  with the base (`u`) says it. A branch with nothing ahead of its base lists no files.

## Consequences

- The background list of the open pull requests reads two more fields of each.
- A branch without a pull request shows what a pull request of it would change, before it is created; with one, it is
  what GitHub shows, without opening the dialog.
- Neither the remote branches nor the pull requests screen (Ctrl+P) get `f`; the latter could, from the same commits.
- The files of a branch against its base are not warned about when that base is behind its remote; that cannot be
  told without a fetch.
