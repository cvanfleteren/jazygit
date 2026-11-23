# 186. The reflog of HEAD, read only

Date: 2026-11-23

## Context

The reflog is where lost work is found: a commit dropped by a rebase, a branch reset too far, a branch deleted after
its tip was checked out. wanted.md asks for a reflog view and an undo. This decision is the view alone, and only
reading it; what can be done to an entry, and undo, come later (`documentation/plans/reflog.md`).

lazygit shows the reflog of HEAD in a tab beside its commits. It shows the time of the *commit*, not of the entry, so
checking out an old commit looks old. It reads the whole reflog, and on each change reads until it meets the newest
entry it knows, comparing sha, commit time and subject, which stops too early on two equal entries. It does not mark
which entry HEAD is at, nor which commits no ref keeps any more. It keys its rows by sha, though one commit is often
in a reflog many times. It ignores git's exit status, so reflogs that are off look like an empty reflog.

Nothing in jazygit reads HEAD's reflog. `CliBranches` reads the reflogs of the branches for their creation (ADR 41),
`CliStashes` the reflog of the stash, and `History.everHad` a branch's shas (ADR 116); each for its own end.

## Decision

- **The reflog of HEAD only**, of the worktree it is opened in: each worktree has its own HEAD, and so its own reflog
  (ADR 182). The reflogs of branches can use the same view later.
- **A tab of the commits pane**, "Reflog", after "Commits" (ADR 185), from `features-changes`, with a part of its
  own that is not carried to another worktree.
- **Read only.** Selecting an entry shows its commit's diff in the content panel, Enter opens its files and `y` copies
  its sha (which the commits get too). Nothing that changes the repository is bound in its scope, `Scopes.REFLOG`.
- **One process a page.** `History.reflog` in `core-git` reads 200 entries with `git log --walk-reflogs --date=unix`,
  the reflog's selector and subject in front of the fields of a commit, so that each `ReflogEntry` (in `core-repo`)
  carries its time, its subject and a whole `Commit`. `%gd` with `--date=unix` gives the time of the entry, not of the
  commit.
- **A page ahead.** The first page is drawn as soon as it is read, and the next is read at once; whenever the cursor
  enters the last page read, the next is read. One read runs at a time, and a page shorter than 200 ends it.
- **The subject is parsed into an `Action`**: checkout from and to, commit (plain, amend, initial, merge), reset,
  rebase and its phase, merge, pull, cherry-pick, and the rest as `Other`. git writes these subjects untranslated.
- **A row** shows, newest first: `◆` when the entry is at HEAD's commit and `✗` when its commit is lost, the time of
  the entry, the action, its subject made short (`main → feature-x`), and the short sha. Where the pane is narrow, the
  subject is cut first, then the sha left out. A `Legend` explains the marks.
- **Lost** means no branch, tag or remote reaches the commit. It is found by one more process per page, run after the
  page is drawn, so the list never waits for it.
- **A row is an entry, not a commit**: it is known by its place and time. A reread reads as many entries as are read
  already, in one process, so the cursor stays on the entry it was on when new ones arrive at the top.
- **Read only while shown**: again after an operation of the app and after the log is read, and, for what changes
  outside the app, when the stamp of `logs/HEAD` of the worktree changes (or of the reftable, when there is one). That
  stamp is looked at only while the tab is shown, so the read of the branches does not change (ADR 56).
- **The content panel** shows the commit as for any commit, and among its properties (ADR 170) the selector
  (`HEAD@{3}`), the time of the entry, the whole subject, and whether the commit is lost or at HEAD.
- **An empty reflog says why**: no commit yet; reflogs off (`core.logAllRefUpdates`, off by default in a bare
  repository); or the error git gave.
- **Not yet**: filtering with `/`, folding the picks of a rebase into one row, and marking the entries jazygit made.

## Consequences

- Lost work can be found and looked at in the app; getting it back (a branch at an entry, a reset to it) is the next
  step, and binds in `Scopes.REFLOG`.
- A long reflog costs no more than its pages the cursor reaches, and a hidden one costs nothing.
- What git does with a reflog entry whose commit was pruned, with reftable, and how long a page and the lost check
  take, is learned while building this and goes into the facts.
