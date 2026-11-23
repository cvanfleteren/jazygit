# git

What git does that jazygit relies on, learned by testing, reading git's source or measuring. Each fact says when it was
learned, so a fact that has grown old can be checked again; see README.md.

## Repositories and configuration

- git finds the repository of a directory by searching upwards. *Why it matters:* opening a folder inside a repository
  opens that repository. — learned 2026-11-22 · [ADR 0028](../adr/0028-start-outside-a-repository.md)
- A plain `git init` follows the user's git config (initial branch, templates) and makes no commit; on an unborn HEAD,
  the status, log, branches, stashes and worktrees read empty. *Why it matters:* a freshly initialised repository opens
  like any other. — learned 2026-11-22 · [ADR 0028](../adr/0028-start-outside-a-repository.md)
- In a repository without commits, unstaging and discarding likely fail, as they reset and check out against `HEAD`.
  *Why it matters:* those operations need special handling on an unborn HEAD. — learned 2026-11-22 · [ADR
  0028](../adr/0028-start-outside-a-repository.md)
- A repository's git configuration is shared by all its worktrees. *Why it matters:* settings kept in the repo config,
  such as a theme, apply to every worktree. — learned 2026-11-22 · [ADR 0070](../adr/0070-a-theme-per-repository.md)
- git reads a configuration key from the repository (`--local`), the user (`--global`) and the system, and from
  `includeIf "gitdir:~/work/art03/"` blocks for repositories in a directory; for a single-valued key the last value read
  holds. *Why it matters:* jazygit's own keys (`jazygit.newBranchNameTemplate`, `jazygit.newWorktreeTemplate`,
  `jazygit.pullRequestWorktreeTemplate`) live in git config so that they can be scoped per repository, per user or per
  directory with no settings of jazygit's own. — learned 2026-11-22 · [ADR
  0121](../adr/0121-what-the-name-of-a-new-branch-starts-out-as.md), [ADR
  0134](../adr/0134-where-a-new-worktree-starts-out.md)
- `[includeIf "gitdir:~/work/"]` in `~/.gitconfig` applies a config file to every repository under a directory, which
  many users already use to set a work email. *Why it matters:* a per-directory theme needs no matching rules in
  jazygit. — learned 2026-11-22 · [ADR 0070](../adr/0070-a-theme-per-repository.md)
- `git config --show-scope --show-origin --get-all <key>` lists every value of a key with its scope and the file it came
  from, includes resolved; `--local` reads only the repository's own configuration, and `git config --local --unset-all
  <key>` removes the repository's values. *Why it matters:* one call tells a repository's own theme apart from an
  inherited one and its file. — learned 2026-11-22 · [ADR 0070](../adr/0070-a-theme-per-repository.md)
- `git var GIT_EDITOR` resolves the editor in this order: `$GIT_EDITOR`, `core.editor`, `$VISUAL`, `$EDITOR`, else `vi`.
  *Why it matters:* every user has an editor for the settings file without configuring jazygit. — learned 2026-11-22 ·
  [ADR 0111](../adr/0111-the-settings-file-opened-from-the-settings.md)
- A GUI editor set as git's editor must be set to wait (e.g. `code --wait`), as git needs that for commit messages. *Why
  it matters:* jazygit can rely on git's editor blocking until the file is saved and closed. — learned 2026-11-22 · [ADR
  0111](../adr/0111-the-settings-file-opened-from-the-settings.md)

## Revisions and arguments

- A branch name passed bare where git also accepts options is read as an option: renaming a branch to `--force`
  overwrote another branch. *Why it matters:* no revision, branch name or remote handed to git may start with `-`. —
  learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)
- git reads a worktree start argument beginning with `-` as an option (e.g. `--no-checkout`, `--force`) and quietly does
  something else. *Why it matters:* such starts are refused in the dialog. — learned 2026-11-22 · [ADR
  0010](../adr/0010-worktrees-from-anywhere.md)
- `git rev-parse --verify --end-of-options <text>^{commit}` resolves user-typed text to a commit safely, even text
  starting with `-`. *Why it matters:* a typed start point becomes a commit only after git resolves it. — learned
  2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)
- `git merge` and `git rebase` given a bare branch name resolve a tag of the same name in preference to the branch. *Why
  it matters:* revisions are passed as full refs (`refs/heads/<name>`, `refs/remotes/<r>/<name>`), a full sha or `HEAD`.
  — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)
- Where git takes a branch name rather than a revision, a full ref means something else: `git checkout refs/heads/x`
  checks out no branch (detached), and `git branch -d refs/heads/x` looks for a branch literally named `refs/heads/x`.
  *Why it matters:* `checkout <branch>`, `-b <name>` of `checkout`/`worktree add`, `branch --delete/--move/--force
  <name>`, `worktree add <path> <branch>`, `--exclude=<name>` with `--branches` and `branch.<name>.*` config keys get
  the bare name; everything else gets a full ref. — learned 2026-11-22 · [ADR
  0040](../adr/0040-typed-revisions-and-remotes.md) · git 2.56
- Commands that take a revision (start of `checkout -b`, `worktree add`, `branch --force`; `merge`, `rebase`, `reset`,
  `cherry-pick`, `revert`, `show`, `log`, `rev-list`, `rev-parse`, `merge-base`; both sides of a push/fetch refspec; the
  branch `push --delete` deletes) accept a full ref or sha. *Why it matters:* this is the boundary `GitArgs` encodes
  between name and revision arguments. — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md) ·
  git 2.56
- An abbreviated sha can be shadowed by a ref of the same name, while git prints every sha jazygit reads in full (40 hex
  digits, or 64 with SHA-256). *Why it matters:* only full object ids are treated as commits; shortening is display
  only. — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)

## Branches and upstreams

- git does not record which branch a branch was made from, since a branch is only a name for a commit. The reflog (`git
  log --walk-reflogs`) tells it only while the reflog exists, only in the clone the branch was made in, and only when
  git was given a branch to start from: `git checkout -b topic` from a checked-out branch records `Created from HEAD`.
  *Why it matters:* the parent of a branch has to be recorded by jazygit (via gh's convention) or guessed from history,
  so jazygit ranks candidates and puts the reflog's answer first when there is one. — learned 2026-11-22 · [ADR
  0003](../adr/0003-pr-base-defaults-to-parent-branch.md), [ADR 0143](../adr/0143-setting-the-base-of-a-branch.md)
- After the base has been merged into a branch, the branch it forked from last can be a different one, and a squash
  merge upstream leaves no shared commit to find. *Why it matters:* the ranking of base candidates is a guess that can
  be wrong, so the counts are shown beside each row. — learned 2026-11-22 · [ADR
  0143](../adr/0143-setting-the-base-of-a-branch.md)
- `branch.<name>.gh-merge-base` is the config key `gh` writes to record the branch a branch was made from, and it stays
  in the clone: on another machine a branch made elsewhere has none until someone runs `git config` by hand. *Why it
  matters:* jazygit writes the same key when setting a base, so that gh and jazygit agree, and offers `b` to restore it
  on other machines. — learned 2026-11-22 · [ADR 0143](../adr/0143-setting-the-base-of-a-branch.md)
- git moves or deletes the `branch.<name>` config section along with the branch on rename and delete. *Why it matters:*
  keys stored there, like `gh-merge-base`, need no handling on rename or delete. — learned 2026-11-22 · [ADR
  0003](../adr/0003-pr-base-defaults-to-parent-branch.md)
- `branch.autoSetupMerge` defaults to `true`: a branch created from a remote-tracking branch (e.g. `git switch -c
  feature origin/main`) follows it; `false` disables this, `always` and `inherit` follow what git says. *Why it
  matters:* jazygit passes neither `--track` nor `--no-track` on branch creation so the user's setting decides. —
  learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md), [ADR
  0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md), [ADR 0043](../adr/0043-nuke-a-branch.md)
- `git switch -c feature origin/main` makes `feature` track `origin/main` until it is pushed, so several local branches
  can follow one remote branch. *Why it matters:* "the first local branch that follows the remote branch" is the wrong
  way to find the branch of a remote branch's checks; jazygit prefers the one with the same name, else the only one. —
  learned 2026-11-22 · [ADR 0161](../adr/0161-a-failed-build-handed-to-an-agent.md)
- Renaming a branch with `git branch --move` leaves it following its old upstream: a branch that followed
  `origin/feature` and is renamed to `topic` still follows `origin/feature`. *Why it matters:* a plain local rename
  leaves the push pointed at the old remote name, so renaming has to offer to rename the upstream too. — learned
  2026-11-22 · [ADR 0090](../adr/0090-renaming-a-branch-on-the-remote.md)
- `git branch --set-upstream-to=<remote>/<branch>` needs the remote-tracking branch to exist, and a push does not create
  one when the remote's fetch refspec leaves it out, as in a `--single-branch` clone; `git config --local
  branch.<name>.merge refs/heads/<name>` sets the upstream without it. *Why it matters:* the upstream after a remote
  rename is written as config, not with `--set-upstream-to`. — learned 2026-11-22 · [ADR
  0090](../adr/0090-renaming-a-branch-on-the-remote.md)
- `git fetch --prune` removes remote-tracking branches deleted on the remote; `git rev-parse --verify --quiet
  refs/remotes/<remote>/<branch>` then tells whether one is still there. *Why it matters:* an upstream deleted on the
  remote is detected before pushing, so the error can say there is nothing to rename. — learned 2026-11-22 · [ADR
  0090](../adr/0090-renaming-a-branch-on-the-remote.md)
- Before git 2.48, `origin/HEAD` is not updated after the default branch of `origin` is renamed, and a remote need not
  have `refs/remotes/<remote>/HEAD` at all. *Why it matters:* the default branch is only reliably known for `origin`,
  with a fallback to a local `main`/`master`. — learned 2026-11-22 · [ADR 0043](../adr/0043-nuke-a-branch.md) · git <
  2.48
- Since git 2.41, `git for-each-ref --format=%(ahead-behind:<commit>)` counts commits ahead and behind of every listed
  ref against one commit (a base or a tip) in a single process; it took 5 ms for the branches of jazygit's own
  repository. *Why it matters:* the behind-base count per branch is affordable on every read of the branches, and all
  candidate bases are ranked with one git call per key press. — learned 2026-11-22 · [ADR
  0046](../adr/0046-how-far-a-branch-is-behind-its-base.md), [ADR 0143](../adr/0143-setting-the-base-of-a-branch.md) ·
  git ≥ 2.41
- `git for-each-ref` accepts any number of `%(ahead-behind:<commit>)` atoms in one format and counts them all in one
  process; one base it cannot find fails the whole command with no output. *Why it matters:* all bases are counted in
  one process, with a per-base fallback when that fails. — learned 2026-11-22 · [ADR
  0049](../adr/0049-fewer-git-processes-per-read.md)
- A branch name can contain `)`, which ends a `%(ahead-behind:<name>)` atom. *Why it matters:* bases are passed to the
  atom by commit sha, not by name. — learned 2026-11-22 · [ADR 0046](../adr/0046-how-far-a-branch-is-behind-its-base.md)
- `git for-each-ref --format=%(ahead-behind:…)` counts for all branches in one process but walks the whole history
  without a commit-graph: 4.4 s on nixpkgs; `git rev-list --count <base>..<branch>` only walks the branch's own commits,
  a few milliseconds each. *Why it matters:* the cost of ahead/behind counting depends on the commit-graph; capping with
  `--max-count` bounds a branch far from a base. — learned 2026-11-22 · [ADR
  0003](../adr/0003-pr-base-defaults-to-parent-branch.md)
- `git for-each-ref` names the commits of any number of refs in one process and leaves out refs that do not exist. *Why
  it matters:* it replaces one `rev-parse` per candidate base. — learned 2026-11-22 · [ADR
  0049](../adr/0049-fewer-git-processes-per-read.md)
- `%(objectname)` in `git for-each-ref` is the object the ref points to, not its commit as `rev-parse <ref>^{commit}`
  gives; the two agree for branches, which point to commits. *Why it matters:* branch tips can be read via
  `for-each-ref` instead of a `rev-parse` each. — learned 2026-11-22 · [ADR
  0049](../adr/0049-fewer-git-processes-per-read.md)
- A `git for-each-ref` pattern also matches refs below it: `refs/heads/a` also matches `refs/heads/a/b`. *Why it
  matters:* results must be filtered to the refs named exactly. — learned 2026-11-22 · [ADR
  0049](../adr/0049-fewer-git-processes-per-read.md)
- `git for-each-ref --contains <commit>` lists the refs whose history contains a commit. *Why it matters:* "Add to
  stack…" uses it to find local branches built on the top of a stack that have no recorded parent. — learned 2026-11-22
  · [ADR 0148](../adr/0148-adding-a-branch-without-a-pull-request-to-a-stack.md)

## The reflog

- The reflog's "Created from X" entry often says `HEAD`, is local only, and expires. *Why it matters:* it is unusable
  for finding a branch's parent. — learned 2026-11-22 · [ADR 0003](../adr/0003-pr-base-defaults-to-parent-branch.md)
- The oldest reflog entry of a branch made by `git branch`, `checkout -b`, `switch -c` or `worktree add -b` reads
  `branch: Created from <start>`, at the time it was made, and git writes it untranslated. *Why it matters:* it is how
  jazygit learns when a branch was created. — learned 2026-11-22 · [ADR
  0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md) · git 2.56
- Checking out a branch from a remote with `git switch <name>` or `git checkout --track -b` also creates its reflog with
  `branch: Created from refs/remotes/origin/<name>` (or `origin/<name>` as typed). *Why it matters:* a branch created
  from the same-named remote branch it follows is treated as checked out, not made. — learned 2026-11-22 · [ADR
  0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md) · git 2.56
- The `main` of a clone starts its reflog with `clone: from <url>` at clone time, and remote-tracking branches have
  `fetch` entries. *Why it matters:* only a `branch: Created from` entry counts as a creation time. — learned 2026-11-22
  · [ADR 0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md)
- Renaming a branch keeps its reflog; deleting a branch removes it. *Why it matters:* the creation time survives a
  rename. — learned 2026-11-22 · [ADR 0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md)
- A branch has no reflog when a clone did not create it, when entries have expired (90 days by default), or with
  `core.logAllRefUpdates` off; the oldest remaining entry can then be a pull or a reset. *Why it matters:* without a
  creation entry the tip's commit time is the age. — learned 2026-11-22 · [ADR
  0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md)
- A branch whose reflog entries have expired no longer shows that it once had its remote tip. *Why it matters:* such a
  branch is told to pull instead of being offered a force push, which errs on the side of the commits on the remote. —
  learned 2026-11-22 · [ADR 0116](../adr/0116-a-force-push-only-over-what-the-branch-had.md)
- `git log --walk-reflogs --glob=refs/heads/ --date=unix` lists the reflogs of every branch in one call, each newest
  first, with selectors `refs/heads/<name>@{<time>}`; a ref name cannot contain `@{`. It took 11 ms for the 440 entries
  of jazygit's own repository. *Why it matters:* all creation times come from one process per branches read. — learned
  2026-11-22 · [ADR 0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md)
- `GIT_COMMITTER_DATE` can give a new commit an older committer date than its branch's creation. *Why it matters:* a
  test can make the creation time later than the tip time. — learned 2026-11-22 · [ADR
  0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md)
- With `--date=unix`, `%gd` of `git log --walk-reflogs` gives the time of the entry, `HEAD@{1795463129}`, and not its
  place; no placeholder gives both, so the place is counted. `--skip` counts the entries git shows, so pages read with
  `--skip` and `--max-count` join up. *Why it matters:* one process reads a page of HEAD's reflog with the entry's time
  and its whole commit. — learned 2026-11-23 · [ADR 0186](../adr/0186-the-reflog-of-head-read-only.md) · git 2.56
- An entry of HEAD's reflog whose commit object is gone is left out of `git log --walk-reflogs` without a word, and
  `%gd` numbers the rest as before (`HEAD@{1}`, `HEAD@{3}`). gc counts reflog entries as reachable, so only a prune
  that ignores the reflogs does this. *Why it matters:* the counted place of an entry is git's own but in that case. —
  learned 2026-11-23 · [ADR 0186](../adr/0186-the-reflog-of-head-read-only.md) · git 2.56
- `git log --walk-reflogs HEAD` fails with `bad revision 'HEAD'` (exit 128) when HEAD has no commit; with
  `core.logAllRefUpdates` false it succeeds with no output, as it does when every entry has expired.
  `core.logAllRefUpdates` is unset in a bare repository, which then keeps no reflogs. *Why it matters:* an empty
  reflog says why from the status of git and one look at the config, only when it is empty. — learned 2026-11-23 ·
  [ADR 0186](../adr/0186-the-reflog-of-head-read-only.md) · git 2.56
- The subjects of HEAD's reflog read `checkout: moving from <from> to <to>`, `commit: <subject>` (or
  `commit (amend)`, `(initial)`, `(merge)`, and `(cherry-pick)` when a cherry-pick that stopped on conflicts is
  committed, by `git commit` or `cherry-pick --continue`), `reset: moving to <revision>`,
  `rebase (start|pick|finish|…): <detail>`, `merge <what>: <how>`, `cherry-pick: <subject>`, `revert: <subject>`, and
  `pull <arguments>: Fast-forward` or, for a pull that rebases, `pull <arguments> (start|pick|finish): <detail>`, the
  arguments as typed (`pull -q --rebase`). `git worktree add` gives the first entry of the new worktree's HEAD an empty
  subject. *Why it matters:* the reflog view reads what was done from the subject. — learned 2026-11-23 · [ADR
  0186](../adr/0186-the-reflog-of-head-read-only.md) · git 2.56
- Each worktree has a reflog of HEAD of its own, at `git rev-parse --git-path logs/HEAD`
  (`.git/worktrees/<name>/logs/HEAD` for a linked one), and a reftable repository keeps it in its tables, where
  `git log --walk-reflogs` reads it the same. *Why it matters:* the reflog view is of the worktree it is opened in. —
  learned 2026-11-23 · [ADR 0186](../adr/0186-the-reflog-of-head-read-only.md) · git 2.56
- A page of 200 entries of HEAD's reflog, with each whole commit, took 7 ms from the top and 32 ms after skipping 2800
  of a reflog of 3000. *Why it matters:* reading a page ahead costs nothing noticeable. — learned 2026-11-23 · [ADR
  0186](../adr/0186-the-reflog-of-head-read-only.md) · git 2.56

## Fetch, pull and push

- Every `git fetch` empties `.git/FETCH_HEAD` when it starts and appends what it fetched when it is done; `FETCH_HEAD`
  has no lock, so two concurrent fetches both append, and for the checked-out branch both append a line to be merged.
  *Why it matters:* a background fetch running alongside a pull corrupts what the pull reads. — learned 2026-11-22 ·
  [ADR 0027](../adr/0027-guard-the-remote-refs.md)
- `git pull` reads `FETCH_HEAD` after its own fetch; when another fetch appended to it in between there are two lines to
  merge, and a rebasing pull fails with `fatal: Cannot rebase onto multiple branches.` With plain git, a fetch started
  together with a pull made the pull fail this way a few times in a hundred. *Why it matters:* pulls must not overlap
  any other fetch. — learned 2026-11-22 · [ADR 0027](../adr/0027-guard-the-remote-refs.md)
- Two concurrent fetches can both update `refs/remotes/origin/<branch>`, and one of them then fails with `cannot lock
  ref`: a ref that is locked makes the other command fail instead of waiting. *Why it matters:* commands writing
  remote-tracking refs must be serialised by jazygit, as git does not wait. — learned 2026-11-22 · [ADR
  0027](../adr/0027-guard-the-remote-refs.md)
- `git fetch --no-write-fetch-head` fetches without touching `FETCH_HEAD`. *Why it matters:* the background fetch uses
  it so a pull reads only what its own fetch wrote. — learned 2026-11-22 · [ADR
  0027](../adr/0027-guard-the-remote-refs.md)
- `git fetch --no-write-fetch-head --refmap= <remote> <ref> <sha>` fetches without applying any fetch refspec, so it
  writes no ref, not even `FETCH_HEAD` or a remote-tracking branch, while fetching `refs/heads/<base>` would update
  `refs/remotes/<remote>/<base>` through the remote's refspec. *Why it matters:* a pull request's commits are fetched
  without touching refs or needing the remote-refs guard. — learned 2026-11-22 · [ADR
  0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- `git fetch origin <sha>` can fetch a single commit by id, but it adds objects to the user's repository. *Why it
  matters:* jazygit reads a missing workflow file from GitHub instead, so as not to change the repository behind the
  user's back. — learned 2026-11-22 · [ADR 0138](../adr/0138-workflow-files-from-github.md)
- `git fetch --no-write-fetch-head <remote> +refs/heads/<b>:refs/remotes/<remote>/<b>…` fetches only the named branches
  and touches only remote-tracking refs. *Why it matters:* cancelling a stack checkout after the fetch leaves nothing to
  undo. — learned 2026-11-22 · [ADR 0099](../adr/0099-checking-out-a-stack.md)
- `git fetch --atomic --no-write-fetch-head . refs/remotes/<r>/<b>:refs/heads/<b>…` creates or fast-forwards local
  branches from remote-tracking ones in one all-or-nothing step; without a leading `+` git refuses any update that is
  not a fast-forward, and refuses to update a branch checked out in any worktree. *Why it matters:* checking out a
  stack, or fast-forwarding a branch before `worktree add`, moves every branch or none without touching the working
  tree. — learned 2026-11-22 · [ADR 0099](../adr/0099-checking-out-a-stack.md), [ADR
  0109](../adr/0109-a-remote-branch-checked-out-in-a-new-worktree.md)
- A running `git fetch` cannot be stopped, and on a network that hangs it runs until its timeout. *Why it matters:*
  putting the fetch on the thread of the user's operations would block staging and committing behind it. — learned
  2026-11-22 · [ADR 0027](../adr/0027-guard-the-remote-refs.md)
- When offline, a `git fetch` fails at once. *Why it matters:* being offline needs no switch of its own; only the fetch
  line reports it. — learned 2026-11-22 · [ADR
  0125](../adr/0125-reading-the-status-every-3-s-and-turning-the-timers-off.md)
- `git fetch --porcelain` prints one line per remote-tracking ref it updated, created or pruned, and nothing when there
  was none; `--quiet` silences that output too. It needs git 2.41. *Why it matters:* a fetch that changed nothing
  triggers no re-read of the repository. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md) · git ≥ 2.41
- `git push` without a refspec under each `push.default`: `simple` (default) pushes the checked-out branch to the same
  name and refuses when its upstream has another name; `current` pushes to the same name; `upstream`/`tracking` pushes
  to the upstream; `matching` pushes every branch with a same-named branch on the remote; `nothing` refuses. *Why it
  matters:* jazygit reads `push.default` and reproduces this when it builds an explicit refspec; after a local rename
  the next push fails with an error that seems unrelated to the rename. — learned 2026-11-22 · [ADR
  0040](../adr/0040-typed-revisions-and-remotes.md), [ADR 0090](../adr/0090-renaming-a-branch-on-the-remote.md) · git
  2.56
- `git push` fails when `push.default` holds a value git does not know. *Why it matters:* jazygit refuses such a push
  too rather than assume `simple`. — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)
- A plain `git push` pushes only the checked-out branch, and under `matching` pushes every matching branch. *Why it
  matters:* jazygit names the branch in a full refspec `refs/heads/<name>:refs/heads/<target>` to push any branch, and
  only that one. — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)
- `git push <remote> --delete refs/heads/<branch>` with the full ref keeps a remote tag of the same name from making git
  refuse or being deleted instead. *Why it matters:* remote branch deletion always uses the full ref. — learned
  2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md), [ADR 0043](../adr/0043-nuke-a-branch.md)
- `git push --atomic` updates every ref or none. A bare `--force-with-lease`, with no explicit expected value, leases
  against the local remote-tracking ref of the branch's upstream (the last-fetched remote ref), so after a background
  fetch the lease holds and a force push silently drops commits someone else pushed; `--force-if-includes` additionally
  refuses when the remote ref was fetched to a commit that the local branch's reflog never had. *Why it matters:* a
  force-push is only as safe as the last fetch, and since jazygit fetches in the background a lease alone would
  overwrite what an agent pushed meanwhile; every force push passes both flags, and a force push is offered only when
  the reflog shows the branch once had the remote commit. — learned 2026-11-22 · [ADR
  0001](../adr/0001-move-commits-to-branch.md), [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md), [ADR
  0116](../adr/0116-a-force-push-only-over-what-the-branch-had.md)
- `git push --atomic` applies all refspecs or none; `--force-with-lease=refs/heads/<new>:` (empty expected value)
  refuses when `<new>` already exists on the remote, and `--force-with-lease=refs/heads/<old>` refuses when `<old>`
  moved since the last fetch. A new name already on the remote at the very same commit is no conflict, and git renames.
  *Why it matters:* a rename on the remote is a push of the new name plus a delete of the old one, all-or-nothing and
  safe against a teammate's push. — learned 2026-11-22 · [ADR 0090](../adr/0090-renaming-a-branch-on-the-remote.md)

## Credentials and ssh

- Besides push, pull and fetch, git needs credentials to delete a branch on the remote and to fetch the refs of a pull
  request. *Why it matters:* the operations that pass credentials are grouped in the `Remotes` role. — learned
  2026-11-22 · [ADR 0069](../adr/0069-git-operations-in-roles.md)
- git runs ssh as configured by `GIT_SSH_COMMAND`, else the worktree's `core.sshCommand`; that command (not a bare
  `ssh`) is the one whose OpenSSH version decides what ssh supports. *Why it matters:* the askpass version probe (`-V`)
  must target the command git will actually run, cached per command. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- git asks for https user names and passwords through `GIT_ASKPASS`, else `core.askPass`, else `SSH_ASKPASS`. *Why it
  matters:* setting `SSH_ASKPASS` to jazygit's helper would also capture git's https prompts, so jazygit sets
  `GIT_ASKPASS` to whatever the user had (or empty) and keeps `GIT_TERMINAL_PROMPT=0`. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- `gh pr checkout` runs git, and so ssh, without `BatchMode`, so ssh opens `/dev/tty` and prompts on the terminal the
  TUI owns. *Why it matters:* jazygit must give it an askpass helper or a batch-mode `GIT_SSH_COMMAND` so that it never
  prompts under the TUI. — learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- git sends its credentials over a plain `http://` remote. *Why it matters:* this precedent is why fj's token is also
  sent over plain HTTP when the remote says so. — learned 2026-11-22 · [ADR
  0179](../adr/0179-fjs-token-goes-where-the-remote-points.md)
- A push over SSH authenticates as the SSH key's account, whatever `GH_TOKEN` says; only a push over HTTPS through gh's
  credential helper uses gh's token. *Why it matters:* telling an agent which gh account to use does not control the
  account of an SSH push. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)

## Rebase, cherry-pick and revert

- `git rebase --update-refs` only moves branches whose tips are in what the rebase replays: a branch not built on the
  one below it (e.g. linked onto it afterwards) is not moved, and a branch checked out in another worktree is skipped
  without warning. *Why it matters:* `--update-refs` cannot rebase a diverged stack, so jazygit writes its own todo. —
  learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- An `update-ref refs/heads/<branch>` line in an interactive rebase todo moves that branch even when it is not built on
  the one below, and even when it is checked out in another worktree, under that worktree's files. Tested with `git
  rebase -i --onto main main d`. *Why it matters:* one rebase can restack a whole stack, but jazygit must refuse
  branches checked out elsewhere. — learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- One interactive rebase keeps its own state across conflicts, while several rebases in a row (one per branch, `--onto`
  the new tip below, as `gh stack rebase` does) need a state of their own to carry on after a conflict. *Why it
  matters:* restacking is one rebase that `continue`/`abort` handle as usual. — learned 2026-11-22 · [ADR
  0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- `git merge-base --fork-point` reads the reflog to find where a branch forked from another, even after that other
  branch was amended. *Why it matters:* commits of a branch below that were amended later are not replayed as the upper
  branch's own. — learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- `-c rebase.missingCommitsCheck=ignore` stops git warning about commits left out of a hand-written todo; `--empty=drop`
  drops a commit that is empty once picked; `git log --topo-order --reverse` lists parents before children whatever
  their dates. *Why it matters:* these are the flags of the stack rebase and of listing each branch's own commits. —
  learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- A rebase without `--rebase-merges` drops merge commits and replays only the other commits, and a conflict that a
  dropped merge had resolved then comes up again. *Why it matters:* the stack rebase says which merges it drops before
  it runs. — learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- `git cherry-pick` refuses to start when the index has any staged change, whatever files it is in, and refuses when
  unstaged changes touch the files the picked commits change; untracked files only stop it when they are in the way.
  *Why it matters:* copying commits onto another branch must be refused up front with a dirty tree, because by the time
  cherry-pick refuses, the checkout has already happened. — learned 2026-11-22 · [ADR
  0001](../adr/0001-move-commits-to-branch.md)
- `git cherry-pick --empty=drop` leaves out commits whose changes are already on the target instead of failing the whole
  pick. *Why it matters:* moving commits onto a branch that already has one of them (e.g. cherry-picked before) still
  succeeds. — learned 2026-11-22 · [ADR 0001](../adr/0001-move-commits-to-branch.md) · git ≥ 2.45
- `git cherry-pick --allow-empty` copies commits that were empty to begin with as they are, instead of stopping on them.
  *Why it matters:* a move of commits keeps originally empty commits. — learned 2026-11-22 · [ADR
  0001](../adr/0001-move-commits-to-branch.md)
- A range that contains a merge commit cannot be cherry-picked (cherry-pick refuses merges without `-m`). *Why it
  matters:* a range holding a merge can only be moved by re-pointing branches (new branch at the parent), never copied.
  — learned 2026-11-22 · [ADR 0001](../adr/0001-move-commits-to-branch.md), [ADR 0013](../adr/0013-revert-commit.md)
- A stash popped right after a checkout makes a later cherry-pick refuse if the changes are staged or touch the same
  files; popped after the copy, a cherry-pick that stops on conflicts leaves the changes in the stash, and the pop can
  still conflict with the copied commits. *Why it matters:* carrying uncommitted changes along through a stash was
  rejected for moving commits. — learned 2026-11-22 · [ADR 0001](../adr/0001-move-commits-to-branch.md)
- `git revert --no-edit <sha>` takes the message git writes; `-m 1` reverts a merge against its first parent (the branch
  it was merged into). *Why it matters:* reverting a merged pull request, the usual reason to revert, works only with
  `-m 1`. — learned 2026-11-22 · [ADR 0013](../adr/0013-revert-commit.md)
- `git revert` refuses when local changes would be overwritten, but unrelated local changes are fine; reverting a commit
  that was already reverted fails with "nothing to commit". *Why it matters:* nothing needs checking up front; git's own
  failure is reported. — learned 2026-11-22 · [ADR 0013](../adr/0013-revert-commit.md)
- A revert stopped on conflicts leaves git's message in `MERGE_MSG`, which `git revert --continue` commits; `git revert
  --abort` undoes a revert left in progress. *Why it matters:* a conflicted revert can be finished or undone with the
  in-progress actions. — learned 2026-11-22 · [ADR 0013](../adr/0013-revert-commit.md)
- `git revert --continue` strips lines starting with `#` from the message and ignores `commit.cleanup`. *Why it
  matters:* editing a revert's message before committing loses `#` lines after a conflict, so the message is reworded
  afterwards instead. — learned 2026-11-22 · [ADR 0013](../adr/0013-revert-commit.md)

## Worktrees and checkout

- A linked worktree's `.git` is a file pointing to its git directory, and only a linked worktree's git directory has a
  `commondir` file pointing to the common git directory. *Why it matters:* the repository a worktree belongs to is read
  from these files without running git. — learned 2026-11-22 · [ADR 0009](../adr/0009-worktree-ux.md)
- The worktrees of a repository share its common git directory: the remotes and their URLs, the git configuration
  (`remote.<name>.gh-resolved`, `jazygit.forgeHome`), the branches, and the remote-tracking branches a fetch brings.
  Each worktree has its own HEAD and its reflog (so the log of HEAD), the index and so the status, the files, an
  operation in progress (`rebase-merge/`, `MERGE_HEAD`, `CHERRY_PICK_HEAD`, `BISECT_LOG`) and the per-worktree refs
  (`refs/bisect/*`, `refs/worktree/*`, `refs/rewritten/*`, gitglossary(7) "per-worktree ref"); and a configuration of
  its own only where `extensions.worktreeConfig` is on. *Why it matters:* another worktree of the same repository opens
  with what was read of the shared part, and does not fetch again, while what is in progress there is read afresh.
  — learned 2026-11-23 · [ADR 0182](../adr/0182-another-worktree-opens-with-what-the-repository-shares.md)
- Next to a bare repository there is no main worktree: every worktree is a linked one. *Why it matters:* a "check out in
  the main worktree" choice cannot exist there. — learned 2026-11-22 · [ADR
  0068](../adr/0068-asking-where-to-check-out-in-a-linked-worktree.md)
- `git worktree add -b <name> <path> <start>` creates a branch and its worktree in one go; `git worktree add --detach
  <path> <start>` checks out on no branch. *Why it matters:* every way of adding a worktree maps onto these two forms
  plus checking out an existing branch. — learned 2026-11-22 · [ADR 0009](../adr/0009-worktree-ux.md), [ADR
  0010](../adr/0010-worktrees-from-anywhere.md)
- `git worktree add -b <name>` refuses when a local branch `<name>` already exists, even if that branch follows the
  wanted remote branch. *Why it matters:* the new-branch worktree key can never reuse a branch that is already here, so
  checking out a remote branch in a worktree needs its own key. — learned 2026-11-22 · [ADR
  0109](../adr/0109-a-remote-branch-checked-out-in-a-new-worktree.md)
- `git worktree add` creates any missing parent directories of the new worktree's path. *Why it matters:* a worktree
  location template may point into directories that do not exist yet. — learned 2026-11-22 · [ADR
  0134](../adr/0134-where-a-new-worktree-starts-out.md)
- git checks a branch out in only one worktree at a time, and refuses to check out a branch that another worktree has
  checked out. *Why it matters:* a pull request whose branch is checked out here cannot be added in another worktree;
  jazygit offers to switch to that worktree instead, and when the follower is the branch checked out here, a new branch
  has to be asked for. — learned 2026-11-22 · [ADR 0010](../adr/0010-worktrees-from-anywhere.md), [ADR
  0068](../adr/0068-asking-where-to-check-out-in-a-linked-worktree.md), [ADR
  0109](../adr/0109-a-remote-branch-checked-out-in-a-new-worktree.md)
- git does not delete a branch that is checked out in a worktree. *Why it matters:* nuking a branch removes its worktree
  first. — learned 2026-11-22 · [ADR 0043](../adr/0043-nuke-a-branch.md)
- While a worktree rebases or bisects, its HEAD is detached: `git worktree list` says `detached` and `%(worktreepath)`
  of `for-each-ref` is empty for the branch. Git still treats the branch as in use there and refuses to check it out
  elsewhere (`'feat' is already used by worktree at …`). *Why it matters:* the branch a worktree works on can't be taken
  from HEAD alone. — learned 2026-11-22 · [ADR 0110](../adr/0110-a-worktree-opened-in-an-editor.md)
- During a rebase, the branch being rebased is named as `refs/heads/<b>` in `rebase-merge/head-name` or
  `rebase-apply/head-name` in the worktree's own git directory (e.g. `.git/worktrees/<wt>`); during a bisect,
  `BISECT_START` holds the branch it started from, or a commit when started on a detached HEAD. *Why it matters:* these
  files are read without running git to find which branch a detached worktree works on. — learned 2026-11-22 · [ADR
  0110](../adr/0110-a-worktree-opened-in-an-editor.md)
- A rebase with `--update-refs` (`rebase.updateRefs`) lists the branches it moves along in `rebase-merge/update-refs`,
  and git treats those as in use in that worktree too. *Why it matters:* jazygit ignores them, so deleting or checking
  out one of them is left to git to refuse. — learned 2026-11-22 · [ADR
  0110](../adr/0110-a-worktree-opened-in-an-editor.md)
- `git for-each-ref --format='%(objectname) %(worktreepath)'` gives each branch's commit and the worktree that has it
  checked out, in one call. *Why it matters:* branch positions and checkouts can be re-checked just before a rebase
  starts. — learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)
- `git worktree list` reads what each worktree has checked out from its HEAD, and the commit it reports is the one thing
  that moves with a commit on that branch. *Why it matters:* the worktree list only needs re-reading when a HEAD
  changes. — learned 2026-11-22 · [ADR 0056](../adr/0056-fewer-git-processes-after-a-commit.md)
- `git worktree remove` refuses without `--force` when the worktree has staged, unstaged or untracked files, not ignored
  ones; `git status --porcelain -z --untracked-files=normal` producing any output detects exactly that. *Why it
  matters:* the "has changes" marker matches what makes removal refuse. — learned 2026-11-22 · [ADR
  0010](../adr/0010-worktrees-from-anywhere.md), [ADR 0011](../adr/0011-worktree-detail.md)
- In a big repository, a `git status` per worktree can take longer than a refresh tick. *Why it matters:* worktree
  change detection runs in the background, not on the operations thread. — learned 2026-11-22 · [ADR
  0011](../adr/0011-worktree-detail.md)
- `git worktree lock`/`unlock` lock a worktree (with an optional reason), and `git worktree remove` refuses a locked
  worktree. *Why it matters:* removal of a locked worktree is refused up front rather than after git fails. — learned
  2026-11-22 · [ADR 0009](../adr/0009-worktree-ux.md)
- `git worktree prune` forgets worktrees whose directory is gone; opening a worktree whose directory is gone fails with
  "Not a git repository". *Why it matters:* missing worktrees are detected before switching and can be pruned without
  loss. — learned 2026-11-22 · [ADR 0009](../adr/0009-worktree-ux.md)
- `git worktree remove --force --force` removes a worktree that is locked, has changes, or whose directory is gone; `git
  worktree prune` leaves a gone worktree when it is locked. *Why it matters:* nuking a branch uses the double force. —
  learned 2026-11-22 · [ADR 0043](../adr/0043-nuke-a-branch.md)
- `git checkout --detach <sha>` checks out a commit with HEAD detached. git only warns when you leave a detached HEAD
  that has commits no branch holds; checking out a commit by itself loses nothing. *Why it matters:* jazygit checks out
  commits detached without asking first, and adds no guard of its own. — learned 2026-11-22 · [ADR
  0137](../adr/0137-checking-out-a-commit.md)

## Status and the index

- `git status --porcelain=v2 --branch` reports the commit HEAD is at as `# branch.oid`, and says when there are no
  commits. *Why it matters:* HEAD is resolved once from the status instead of `rev-parse HEAD` per load. — learned
  2026-11-22 · [ADR 0049](../adr/0049-fewer-git-processes-per-read.md)
- Before `git status` can tell what changed, git refreshes the index: it compares each file's `stat` with the index and
  reads and hashes the whole file where they differ, or where the file was written in the same second as the index
  ("racily clean"). A plain `git status` writes the result back, but one run with `--no-optional-locks` never does, so
  every later status hashes those files again. *Why it matters:* the once-a-second status reads leave the index stale,
  so jazygit has to rewrite it itself. — learned 2026-11-22 · [ADR
  0093](../adr/0093-the-index-written-anew-now-and-then.md)
- git does not trust a file in its index whose modification time is too close to when it was recorded (racy-git). *Why
  it matters:* jazygit applies the same rule to its own file stamps. — learned 2026-11-22 · [ADR
  0015](../adr/0015-check-metadata-before-loading.md)
- Files written by a clone, checkout, rebase, reset or pull are racily clean, and git writes the index in all of those,
  so the index changes. Files a tool rewrites without changing them (a formatter, a build, an editor) become stat-dirty
  without any change in the git directory. *Why it matters:* a change to the index tells when to refresh it, and a
  periodic fallback is needed for files that are only touched. — learned 2026-11-22 · [ADR
  0093](../adr/0093-the-index-written-anew-now-and-then.md), [ADR
  0097](../adr/0097-the-index-written-anew-once-it-changed.md)
- A refresh in the same second as the checkout leaves the files racily clean. *Why it matters:* the refresh waits until
  the index has settled (2 s) before running. — learned 2026-11-22 · [ADR
  0097](../adr/0097-the-index-written-anew-once-it-changed.md)
- `git update-index -q --unmerged --refresh` writes refreshed stat information into the index and leaves the rest alone;
  `-q` and `--unmerged` stop it failing on changed or conflicted files, so a failure means the index was locked. *Why it
  matters:* this is the command used to rewrite the index, and its failure is treated as a lock, not an error. — learned
  2026-11-22 · [ADR 0093](../adr/0093-the-index-written-anew-now-and-then.md)
- git takes the index lock for writes. *Why it matters:* repository writes stay on one thread, in order. — learned
  2026-11-22 · [ADR 0012](../adr/0012-background-pool.md)
- A git command that needs the index while another process holds it fails with an `index.lock` exists error. *Why it
  matters:* the refresh runs only while the terminal has the focus, to make that collision unlikely. — learned
  2026-11-22 · [ADR 0093](../adr/0093-the-index-written-anew-now-and-then.md)
- Measured idle for 60 s on a repository with three changed files, reading the status every second cost `git` 0.38 s of
  CPU per minute. *Why it matters:* the 1 s refresh was most of what an idle jazygit cost, which motivated the 3 s
  interval. — learned 2026-11-22 · [ADR 0125](../adr/0125-reading-the-status-every-3-s-and-turning-the-timers-off.md)
- On a clone of jazygit (1,245 files) with a stale index, `git status` took 75 ms instead of 6 ms, and an idle app's git
  processes used 8 % of a core instead of 1 %. *Why it matters:* this measures the cost of never writing the index back.
  — learned 2026-11-22 · [ADR 0093](../adr/0093-the-index-written-anew-now-and-then.md)
- On a clone of jazygit (2,315 tracked files), `git --no-optional-locks status --porcelain=v2 --untracked-files=all`
  takes about 12 ms on a fresh index and 70 to 80 ms on a stale one (every file racily clean, or stat dirty after a
  `touch`). Of the 12 ms, about 10 is the walk for untracked files (`git ls-files --others` alone); with
  `--untracked-files=no` it takes 5 ms on a fresh index and 75 ms on a stale one. ADR 52's 50 to 60 ms a call was taken
  before ADR 93 found that the index is never written back, so on a stale index. *Why it matters:* a stale index, not
  the walk of the working tree, is what makes the idle status expensive, which is why the index is written anew.
  — learned 2026-11-23 · [ADR 0052](../adr/0052-the-status-is-read-every-second.md),
  [ADR 0093](../adr/0093-the-index-written-anew-now-and-then.md) · git 2.56
- `git update-index -q --refresh` run in the same second as the files were written leaves them racily clean, so the
  next status hashes them all again: a refresh right after a clone, or right after touching every file, left the status
  at 75 ms; one a few seconds later took it to 12 ms. *Why it matters:* writing the index anew in the same second as
  something wrote the files does not help; it does once that second has passed. — learned 2026-11-23 · [ADR
  0097](../adr/0097-the-index-written-anew-once-it-changed.md) · git 2.56
- Running `git status`, branches, stashes and worktrees every 500 ms (some 8 git processes a second) cost about 3% of a
  core in git itself and 1% in reading its output. *Why it matters:* only the status needs to run on every tick. —
  learned 2026-11-22 · [ADR 0015](../adr/0015-check-metadata-before-loading.md)
- `git ls-files -z -t --cached --others --exclude-standard` lists a conflicted file once per stage, and with `-t`,
  `--deduplicate` has no effect; a submodule is one entry. *Why it matters:* paths are de-duplicated and sorted by
  jazygit itself. — learned 2026-11-22 · [ADR 0108](../adr/0108-all-files-of-the-working-tree.md)
- `ls-files -t` tags skip-worktree files `S`: a sparse checkout leaves them off disk, while `git update-index
  --skip-worktree` leaves them on disk and git does not report their edits. *Why it matters:* `S` files missing from
  disk are left out of the all-files tree, and those on disk are kept. — learned 2026-11-22 · [ADR
  0108](../adr/0108-all-files-of-the-working-tree.md)
- Listing every file of a repository of 100,000 files with `git ls-files` is a few megabytes of output and tens of
  milliseconds. *Why it matters:* this is what the all-files mode costs on each status change. — learned 2026-11-22 ·
  [ADR 0108](../adr/0108-all-files-of-the-working-tree.md)
- git accepts pathspecs on stdin with `--pathspec-from-file`. *Why it matters:* staging tens of thousands of filtered
  paths as arguments can exceed the OS command-line limit, and this is the planned fix (issue #410). — learned
  2026-11-22 · [ADR 0105](../adr/0105-filtering-the-files-of-a-tree.md)

## Files git writes, and reftable

- git writes a loose ref to a lock file and renames it into place, which changes the modification time of the directory
  the ref is in. *Why it matters:* stamping only the directories under `refs/heads` and `refs/remotes` detects ref
  changes cheaply. — learned 2026-11-22 · [ADR 0015](../adr/0015-check-metadata-before-loading.md)
- Since git 2.48 every `git fetch` takes and releases the lock of `refs/remotes/<remote>/HEAD`, which changes the
  modification time of the directory holding it; turning `remote.<name>.followRemoteHEAD` off stops the fetch from
  setting the remote's HEAD. *Why it matters:* the branch stamp watches loose ref files under `refs/remotes` rather than
  their directories. — learned 2026-11-22 · [ADR 0058](../adr/0058-less-reading-in-the-background.md) · git ≥ 2.48
- git packs refs when it collects garbage, so a repository normally has few loose remote-tracking refs. *Why it
  matters:* a stat per loose ref under `refs/remotes` each tick is usually cheap. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md)
- What the branches depend on is stored in files: each worktree's `HEAD`, `config` (upstreams), `packed-refs`,
  `reftable/`, and `refs/heads`, `refs/remotes`; stashes in `refs/stash`, its reflog, `packed-refs` and
  `reftable/tables.list`; worktrees in `worktrees/` with each one's `gitdir` and `locked`. *Why it matters:* these are
  the files stamped to skip reading branches, stashes and worktrees when nothing changed. — learned 2026-11-22 · [ADR
  0015](../adr/0015-check-metadata-before-loading.md)
- A commit on a branch moves the branch ref but leaves the HEAD files, `.git/config` and `.git/logs/refs/heads` with
  their modification times, in the main worktree and in a linked one. *Why it matters:* stamps of those files tell that
  a commit changed nothing else. — learned 2026-11-22 · [ADR 0056](../adr/0056-fewer-git-processes-after-a-commit.md)
- A commit appends to the reflog of its branch without changing the directory `logs/refs/heads`, while creating,
  renaming or deleting a branch, or expiring reflogs, does change it. *Why it matters:* a stamp of that directory could
  guard the reflog read. — learned 2026-11-22 · [ADR 0056](../adr/0056-fewer-git-processes-after-a-commit.md)
- The reftable backend does not keep reflogs under `.git/logs`, so reading reflogs from files there does not work. *Why
  it matters:* reflogs are read through `git log --walk-reflogs`. — learned 2026-11-22 · [ADR
  0041](../adr/0041-a-branch-is-as-old-as-its-last-activity.md)
- In a reftable repository the HEAD files are stubs git never rewrites; the main worktree's HEAD lives in the tables
  with the branches, and a linked worktree's in tables of its own. *Why it matters:* worktree stamps must stat the
  tables, not the HEAD files. — learned 2026-11-22 · [ADR 0058](../adr/0058-less-reading-in-the-background.md)

## Diff

- `git diff <base>...<head>` (three dots) diffs the head against where it forked from the base (their merge base), so
  commits added to the base since the fork do not show, and a base not fetched for a while changes nothing unless it has
  since taken commits of the branch. *Why it matters:* the files of a branch or pull request are listed this way and
  match what GitHub shows. — learned 2026-11-22 · [ADR 0061](../adr/0061-the-files-of-a-branch.md)
- `--src-prefix=a/ --dst-prefix=b/` forces git's `a/`/`b/` path prefixes whatever `diff.noprefix`,
  `diff.mnemonicPrefix`, `diff.srcPrefix` or `diff.dstPrefix` say; `--default-prefix` does the same but needs git 2.41.
  *Why it matters:* every parsed diff is read with these two flags. — learned 2026-11-22 · [ADR
  0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md), [ADR
  0047](../adr/0047-the-rich-view-for-the-working-tree.md) · git ≥ 2.41
- With `diff.mnemonicPrefix`, `git diff` and `git diff --cached` put `c/`, `i/` and `w/` before paths. *Why it matters:*
  without forced prefixes the diff parser misreads the working tree's diffs. — learned 2026-11-22 · [ADR
  0047](../adr/0047-the-rich-view-for-the-working-tree.md)
- `-U<n>` sets the lines of context of a diff, and git's own `diff.context` config sets the default; a context larger
  than any file (e.g. `-U1000000`) makes git show each changed file as one hunk. *Why it matters:* jazygit always passes
  `-U<n>` itself (3 by default) and reads a "whole file" diff this way, and a hunk filter hides such a file only when
  every change in it matches. — learned 2026-11-22 · [ADR 0076](../adr/0076-the-amount-of-context-in-a-diff.md), [ADR
  0077](../adr/0077-showing-more-of-one-hunk.md), [ADR 0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- A hunk header gives where the hunk starts in the old and the new file and how many lines it has on each side, but git
  never says how long the file is, so the lines after the last hunk cannot be counted from a diff. *Why it matters:*
  gaps between and above hunks can be counted without a read, the tail cannot. — learned 2026-11-22 · [ADR
  0077](../adr/0077-showing-more-of-one-hunk.md)
- Git gives a block of changed lines as all its removed lines followed by all its added ones; no option of git
  interleaves them, and even with `-U0` a hunk holds every changed line that is next to another. *Why it matters:*
  pairing removed and added lines has to be done by jazygit. — learned 2026-11-22 · [ADR
  0083](../adr/0083-changed-lines-are-shown-in-pairs.md)
- git notes `\ No newline at end of file` after a changed last line, inside the block of changed lines. *Why it
  matters:* such a note must not end a block of paired removed/added lines. — learned 2026-11-22 · [ADR
  0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
- With whitespace ignored (`-w`), a line whose whitespace only changed is a context line, and git prints context lines
  as they are in the new file (`  b` that became `\tb` shows as ` \tb`). *Why it matters:* a whole file rebuilt from a
  `-w` diff is not the original, so versions of a file are always read without `-w`. — learned 2026-11-22 · [ADR
  0076](../adr/0076-the-amount-of-context-in-a-diff.md), [ADR 0077](../adr/0077-showing-more-of-one-hunk.md), [ADR
  0102](../adr/0102-the-original-or-the-new-version-of-a-file.md)
- `git diff --ignore-all-space` leaves out changes to whitespace only. *Why it matters:* this is how jazygit ignores
  whitespace in diffs. — learned 2026-11-22 · [ADR 0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- `git diff -I<regex>` drops a hunk whose changed lines all match, but it takes one regex for every file, in POSIX ERE,
  applied only when the diff is read. A hunk containing an added or removed blank line still shows, because the blank
  line does not match. *Why it matters:* jazygit applies its own per-language and per-path hunk filters at draw time
  instead, and counts blank lines as matching when another line of the hunk matched. — learned 2026-11-22 · [ADR
  0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- `--name-status` does not leave out files with whitespace-only changes when whitespace is ignored. *Why it matters:*
  such files stay listed and the diff says only whitespace changed. — learned 2026-11-22 · [ADR
  0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
- `--diff-merges=first-parent` diffs a merge against its first parent, while the default for `git show` is a combined
  diff. *Why it matters:* the rich view shows what a merge brought into its branch. — learned 2026-11-22 · [ADR
  0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
- `git show --name-status -z -M --diff-merges=first-parent` and `git stash show --include-untracked --name-status -z -M`
  list the changed files of a commit and a stash. *Why it matters:* the file tree of a commit or stash is read from
  these. — learned 2026-11-22 · [ADR 0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
- In a combined diff (a conflicted file or a merge commit) the hunk header has more `@` signs (`@@@`), and each line
  keeps one prefix column per parent: the number of `@` signs minus one. *Why it matters:* search strips that many
  prefix columns so that a query matches the same code in plain, rich and combined hunks. — learned 2026-11-22 · [ADR
  0126](../adr/0126-searching-the-content-panel.md)
- During a conflicted merge or rebase, `git diff` shows an unmerged path as a combined diff (`diff --cc <path>`, `index
  a,b..0000000`, `@@@ … @@@` headers, two-column markers like `++<<<<<<< HEAD`), and `git diff --cached` shows it as one
  line `* Unmerged path <path>`. *Why it matters:* the diff parser must also start a file at `diff --cc ` and `*
  Unmerged path `. — learned 2026-11-22 · [ADR 0047](../adr/0047-the-rich-view-for-the-working-tree.md)
- Untracked files marked intent-to-add in a copy of the index show in `git diff` as `new file`. *Why it matters:*
  untracked files are drawn as added ones. — learned 2026-11-22 · [ADR
  0047](../adr/0047-the-rich-view-for-the-working-tree.md)
- A diff with the whole file as context holds both versions: its context lines plus its removed lines make the original,
  its context lines plus its added lines make the new file. Reading a file of about 3,000 lines whole takes git a few
  milliseconds. *Why it matters:* both versions of a file come from one diff read, with no `git show` and no separate
  cache. — learned 2026-11-22 · [ADR 0077](../adr/0077-showing-more-of-one-hunk.md), [ADR
  0102](../adr/0102-the-original-or-the-new-version-of-a-file.md)
- A binary file in a diff, also one read with the whole file as context, is a single line saying the files differ, with
  no content. *Why it matters:* no version or image bytes can be made from a diff of a binary file, so images need their
  own read. — learned 2026-11-22 · [ADR 0077](../adr/0077-showing-more-of-one-hunk.md), [ADR
  0103](../adr/0103-a-version-of-markdown-rendered.md)
- A submodule's version in a diff is a single `Subproject commit` line. *Why it matters:* showing a submodule's "file"
  is pointless. — learned 2026-11-22 · [ADR 0077](../adr/0077-showing-more-of-one-hunk.md)
- A submodule change shows in a diff as `-Subproject commit …` and `+Subproject commit …` lines in a hunk. *Why it
  matters:* the rich view draws it as a normal hunk. — learned 2026-11-22 · [ADR
  0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
- A submodule's diff is the commit it is at, and a commit inside it moves that without changing its directory's metadata
  or its status. *Why it matters:* a submodule's diff cannot be guarded by a file stamp and is read with every status. —
  learned 2026-11-22 · [ADR 0058](../adr/0058-less-reading-in-the-background.md)
- Honouring `.gitattributes` (`diff=`, `linguist-language`) takes a `git check-attr` per diff read. *Why it matters:*
  the language of a file is told from its path instead. — learned 2026-11-22 · [ADR
  0053](../adr/0053-syntax-in-the-rich-view.md)
- `.gitattributes` can mark files `linguist-generated`, but finding out which files are marked takes a read of the
  attributes. *Why it matters:* generated files are filtered by glob instead, for now. — learned 2026-11-22 · [ADR
  0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- git limits rename detection by itself (`diff.renameLimit`), so `-M` does not blow up on very large commits. *Why it
  matters:* reading the files of each highlighted commit with `--name-status -M` stays bounded. — learned 2026-11-22 ·
  [ADR 0141](../adr/0141-the-files-follow-the-highlight.md)
- On nixpkgs (50 000 files, 910 000 commits, warm cache), `--name-status` against the full diff took: an ordinary commit
  5 ms vs 6 ms; a commit of 1 500 files 40 ms vs 1 430 ms; a merge of 17 000 files against its first parent 126 ms vs
  698 ms. A branch against its base touching 67 000 files took 331 ms with `--name-status`. *Why it matters:* listing a
  commit's files on every highlight move costs a fraction of the diff already being read, except for a branch far from
  its base. — learned 2026-11-22 · [ADR 0141](../adr/0141-the-files-follow-the-highlight.md)
- A diff of 50 000 lines read with `-U0` has tens of thousands of hunks. *Why it matters:* a hunk parser that recurses
  once per hunk overflows the stack. — learned 2026-11-22 · [ADR
  0079](../adr/0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md)
- A large file added by mistake, such as a database dump, gives a diff of hundreds of MB or more; read whole into memory
  it ran past a 30 s timeout after a memory spike, or ran out of memory. *Why it matters:* git's output has to be read
  only up to a limit, with git stopped there. — learned 2026-11-22 · [ADR
  0079](../adr/0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md)
- Once git is stopped partway through printing a diff, its exit code says nothing about whether the diff succeeded. *Why
  it matters:* a diff cut at the limit cannot be judged by its exit code. — learned 2026-11-22 · [ADR
  0079](../adr/0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md)
- Reading `git diff` output measured: 50 000 lines of code (3.3 MB) in 92 ms; an added file of 50 000 lines (4.0 MB) in
  46 ms; 5000 lines of 2000 characters (9.4 MB) in 302 ms; 50 000 lines of 2000 characters (94.5 MB) in 1.7 s. *Why it
  matters:* the cost of reading a diff grows with its text, not its line count, so the limit is in characters as well as
  lines. — learned 2026-11-22 · [ADR 0079](../adr/0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md)
- A large file with a small change gives a small diff, while many files that are each small can give a large one. *Why
  it matters:* looking at file sizes before diffing does not bound the diff. — learned 2026-11-22 · [ADR
  0079](../adr/0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md)

## Log, history and trees

- Repeated `--author` values and repeated pathspecs are OR'd by `git log`, while different kinds of filters (`--grep`,
  `--author`, paths, `-S`) are AND'd. *Why it matters:* a query language with repeatable `author:`/`path:` maps directly
  onto git flags. — learned 2026-11-22 · [ADR 0004](../adr/0004-search-commits.md)
- `git log -S<text>` (pickaxe) can be given only once and finds commits that change how often the text occurs; over a
  long history it takes seconds. *Why it matters:* only one `-S:` per query, and search runs on Enter, not as you type.
  — learned 2026-11-22 · [ADR 0004](../adr/0004-search-commits.md)
- git's `-i`/`--regexp-ignore-case` makes `-S` ignore case too, so `-S` cannot keep its case apart from
  `--grep`/`--author`. *Why it matters:* case sensitivity is one setting for the whole search. — learned 2026-11-22 ·
  [ADR 0004](../adr/0004-search-commits.md)
- `git log --follow` is only allowed with a single path. *Why it matters:* path search does not follow renames. —
  learned 2026-11-22 · [ADR 0004](../adr/0004-search-commits.md)
- `git log` fails on a bad pathspec. *Why it matters:* a failed search shows like any failed load. — learned 2026-11-22
  · [ADR 0004](../adr/0004-search-commits.md)
- `git log` does not list commits in topological order by default; `git rev-list --parents --topo-order` gives each
  commit's parents in topological order. *Why it matters:* first-parent line checks must use the topo-ordered range
  read, not the loaded log. — learned 2026-11-22 · [ADR
  0030](../adr/0030-move-commits-back-onto-the-branchs-own-line.md)
- `git log` can list a parent before its child, for example when commit clocks were off. *Why it matters:* the graph
  arrangement pass has to walk in an order where each commit comes before its parents, not in log order. — learned
  2026-11-22 · [ADR 0130](../adr/0130-the-log-reads-3000-commits.md)
- git.git has a history where 10 000 commits contain 2 693 merges and the graph is up to 512 rails wide. Reading that
  many commits from git is cheap; laying out the graph is what costs. *Why it matters:* this is the stress case behind
  raising `MAX_COMMITS` to 3000 and not to 10 000. — learned 2026-11-22 · [ADR
  0130](../adr/0130-the-log-reads-3000-commits.md)
- `git rev-list main ^<sha>^` holds every commit reachable from `main` and not from the parent of `<sha>`, including
  commits on the other side of a merge; when `<sha>` is on a merged-in side branch, its parent is a commit of that side
  branch that was never `main`'s tip. *Why it matters:* resetting the branch to the parent of a commit that came in
  through a merge leaves it on a commit of the merged branch. — learned 2026-11-22 · [ADR
  0030](../adr/0030-move-commits-back-onto-the-branchs-own-line.md)
- `git log --boundary` also lists the boundary commits on the base where the walk stopped, marked `-` by `%m`; when
  `--max-count` did not cut the walk short, every merge-base of tip and base is among them, but not once `--max-count`
  cuts it short. *Why it matters:* a single boundary commit is the fork point, read in the same process. — learned
  2026-11-22 · [ADR 0049](../adr/0049-fewer-git-processes-per-read.md), [ADR
  0056](../adr/0056-fewer-git-processes-after-a-commit.md)
- `git log --left-right ours...theirs` lists both sides in one process, but `--max-count` limits the two sides together,
  not each. *Why it matters:* a divergence keeps one `log` per side. — learned 2026-11-22 · [ADR
  0049](../adr/0049-fewer-git-processes-per-read.md)
- `git ls-tree -r -z --full-tree <sha>` lists every file of a commit's tree, and `git cat-file` reads a blob from the
  object database, whatever is checked out and in whichever worktree. A submodule is a single entry, and a symbolic link
  is a single file. *Why it matters:* all files of any commit or branch can be shown read-only without touching the
  working tree. — learned 2026-11-22 · [ADR 0128](../adr/0128-all-files-of-a-commit.md)
- `git ls-tree <sha> -- <path>` gives a path's mode and object id, so a blob, a symlink target or a submodule commit can
  be told apart before reading. *Why it matters:* a file without changes at a commit is read correctly as a file, a link
  (`Symbolic link to <target>`) or a submodule (`Submodule at abc1234`). — learned 2026-11-22 · [ADR
  0128](../adr/0128-all-files-of-a-commit.md)
- A commit's tree does not hold the files the commit deleted, nor the untracked files a stash keeps (those are in the
  stash's third parent). *Why it matters:* "all files" has to merge the changed files that are missing from `ls-tree`
  into the listing. — learned 2026-11-22 · [ADR 0128](../adr/0128-all-files-of-a-commit.md)
- Listing the tree of a commit of 100 000 files with `ls-tree -r` produces a few megabytes and takes tens of
  milliseconds. On nixpkgs (50 000 files, warm cache) it took 120 ms and produced 2.3 MB. *Why it matters:* listing all
  files once per view is fine, but doing it on every highlight move is too costly. — learned 2026-11-22 · [ADR
  0128](../adr/0128-all-files-of-a-commit.md), [ADR 0141](../adr/0141-the-files-follow-the-highlight.md)

## Blame

- `git blame --porcelain <rev> -- <path>` gives, for each line of the file at `<rev>`, the commit that last changed it,
  its author, author time, summary, the file's path in that commit (`filename`) and the commit and path before it
  (`previous`). *Why it matters:* blame of a whole version maps one-for-one onto its lines, and Enter can open the
  commit with the file highlighted at its old path. — learned 2026-11-22 · [ADR
  0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git blame` follows a file renamed whole. A line moved within a file, or from another file, is blamed on the commit
  that moved it unless `-M` (moved blocks in the same file, about one file comparison per commit) or `-C` (also the
  other files the commit changed) is given. `-C -C -C` looks in every file of every commit, which on a large repository
  can take minutes. *Why it matters:* jazygit blames with `-M` but never with `-C`, because a git read cannot be stopped
  short of its 30 s timeout. — learned 2026-11-22 · [ADR 0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git blame -w` ignores whitespace when comparing, which changes whom a line is blamed on but never the line's text.
  *Why it matters:* blame is read with `-w` while whitespace is ignored, and a blame read with the other setting is
  dropped. — learned 2026-11-22 · [ADR 0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git blame --porcelain` output is not in line order. *Why it matters:* a blame cut off by a timeout is not a blame of
  the first lines, so a partial result is discarded as failed. — learned 2026-11-22 · [ADR
  0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git blame -L 1,<last>` blames only a range of lines. *Why it matters:* a version cut at the size limit is blamed only
  up to its last line shown, rather than paying for the whole file. — learned 2026-11-22 · [ADR
  0147](../adr/0147-blame-on-a-version-of-a-file.md)
- git blames lines that are not committed yet on a sha of all zeros. *Why it matters:* jazygit labels those lines
  `uncommitted`. — learned 2026-11-22 · [ADR 0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git blame --contents <file> HEAD` blames text that is not at any revision, such as the index or a file added with
  `git add -N`. `--contents -` reads it from stdin instead. *Why it matters:* jazygit gives git no stdin, so it writes
  the file first. — learned 2026-11-22 · [ADR 0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git -c core.symlinks=false checkout-index --prefix=<dir>/` writes the index's version of a file into a temporary
  directory byte for byte. Reading the blob with `cat-file` into a string and writing it back loses the bytes of a
  non-UTF-8 file, so its unchanged lines are then blamed as not committed yet. *Why it matters:* this is how the index
  is blamed when it differs from HEAD. — learned 2026-11-22 · [ADR 0147](../adr/0147-blame-on-a-version-of-a-file.md)
- If a symbolic link is written for `--contents` as a file holding its target, git blames every line as not committed
  yet wherever HEAD holds a link. *Why it matters:* the file is written for `--contents` only when the index holds a
  change, and otherwise the index is blamed at `HEAD`. — learned 2026-11-22 · [ADR
  0147](../adr/0147-blame-on-a-version-of-a-file.md)
- On a branch without commits there is no `HEAD` to blame the index or the working files against. *Why it matters:*
  every line there is shown as not committed yet, without reading blame. — learned 2026-11-22 · [ADR
  0147](../adr/0147-blame-on-a-version-of-a-file.md)

## Stash

- `git stash show` output depends on the user's `stash.showPatch`, `stash.showStat` and `stash.showIncludeUntracked`
  unless every flag is given explicitly (`--include-untracked --stat=1000 --patch --no-color --no-ext-diff`). *Why it
  matters:* the stash diff is read with all flags spelled out so user config cannot change it. — learned 2026-11-22 ·
  [ADR 0016](../adr/0016-stash-diff.md)
- `git stash show --include-untracked` needs git 2.32. *Why it matters:* covered by the git 2.45 the README requires. —
  learned 2026-11-22 · [ADR 0016](../adr/0016-stash-diff.md) · git ≥ 2.32
- git writes stash messages as `On <branch>: …` or `WIP on <branch>: …`, with `(no branch)` in place of the branch on a
  detached HEAD. *Why it matters:* the stash's branch is parsed from both forms and `(no branch)` means none. — learned
  2026-11-22 · [ADR 0016](../adr/0016-stash-diff.md)
- `stash@{n}` names change as stashes are added and dropped: dropping a stash renumbers the ones after it, so
  `stash@{n}` then names another stash; a stash's sha stays. *Why it matters:* stashes are identified by sha, and a
  stash's file tree closes when its stash is dropped. — learned 2026-11-22 · [ADR 0016](../adr/0016-stash-diff.md), [ADR
  0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
- A stash is three commits: the stash itself, the index it held (`<stash>^2`) and its untracked files (`<stash>^3`). Its
  first parent `<stash>^1` is the commit it was made on. *Why it matters:* each version of a stash's file is blamed at
  the right one of these, and a line blamed on any of the three counts as part of the stash. — learned 2026-11-22 · [ADR
  0147](../adr/0147-blame-on-a-version-of-a-file.md)
- `git stash show` takes no paths; a stash's tracked changes for some paths are `git diff <stash>^1 <stash> -- <paths>`
  and its untracked files `git diff <empty tree> <stash>^3 -- <paths>`. *Why it matters:* per-file diffs of a stash need
  these forms. — learned 2026-11-22 · [ADR 0044](../adr/0044-a-nicer-commit-diff-with-a-file-tree.md)
