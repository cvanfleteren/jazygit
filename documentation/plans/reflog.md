# Plan: the reflog and undo

Status: the read-only view is settled in ADR 185 and ADR 186; the actions on an entry, undo and the reflogs of
branches are still a proposal, to be grilled. Written 2026-11-23 against `main` at `a2a6d06`, and lazygit at
`5edbd89`.

This is wanted.md item 5: "A reflog view with 'reset to here', plus an `undo` key that reverts the last operation the
app ran, building on the command log."

## 1. What lazygit does

All references are to the lazygit tree, `pkg/`.

### Reading

- One command, HEAD's reflog only: `git log -g --format=+%H%x00%ct%x00%gs%x00%P`
  (`commands/git_commands/reflog_commit_loader.go:28-34`). Branch reflogs are never shown.
- The time it shows is `%ct`, the *commit's* time, not the time of the reflog entry. Checking out an old commit shows
  that commit's age. The branch "recency" sort inherits the same error.
- No limit and no paging: the first read takes the whole reflog. Later reads are incremental. They stop at the first
  entry equal to the newest one already known, where "equal" means same sha, commit time and subject (`:46-67`).
  Two identical consecutive entries make the read stop too early. A read filtered by path or author is always full.
- git's exit status is ignored. A broken or absent reflog shows as an empty list ("No reflog history").
- The read is not a refresh scope of its own. It runs inside the branches refresh, before it when branches sort by
  recency, beside it otherwise (`gui/controllers/helpers/refresh_helper.go:429-460, 734-759`).

### Showing

- Reflog is a tab beside Commits in the commits window (`gui.sidePanels`, `config/user_config.go:892-898`). It can be
  moved or hidden.
- Each row shows the short sha and the reflog subject (`checkout: moving from a to b`), plus the date in half and full
  screen. The sha is coloured when it is copied for cherry-pick or used as the diff terminal.
- There is no marker for the entry HEAD is at, no grouping of repeated shas, and no sign of which entries point at
  commits that no ref keeps any more. Those "lost" commits are the main reason anyone opens a reflog.
- `/` filters on sha and subject. The global path and author filter rereads the reflog with
  `--follow --name-status -- path` or `--author`.
- The main view shows `git show <sha>`, and lines can go into a custom patch (apply, or apply in reverse).

### Acting on an entry

The reflog has no keys of its own. It shares the commit controllers (`basic_commits_controller.go:51-140`):

| Key | Action |
| --- | --- |
| space | Check out: a menu with "detached at sha" and each local branch whose tip is that sha. Offers to stash on conflict. |
| `g` | Reset soft, mixed or hard. A hard reset confirms only when the tree is dirty. |
| `n` | New branch at the entry. The suggested name is built from the reflog subject. |
| `C` | Copy for cherry-pick, with ranges; `V` pastes in the commits panel. |
| `y` | Copy sha, subject, message, body, URL, diff or author. |
| `o` | Open the commit in the browser. |
| `w` | New worktree at the entry. |
| `b` | Bisect: mark good or bad. |
| Enter | The commits reachable from the entry, then their files. |
| `ctrl+t` | Difftool. |
| `W` | Diffing mode against the entry. |

Two keys show up even though they do not act on the entry: "move commits to new branch" and "select commits of
current branch". lazygit's own comment says this is deliberate.

### Undo and redo

- `z` undoes and `Z` redoes, from every panel (`gui/controllers/undo_controller.go`). Its tooltip warns: "only commits
  are taken into consideration", meaning the working tree is not.
- There is no state of its own. Each press walks HEAD's reflog from the top and classifies the subjects with regexes:
  - `commit…`, `reset: moving to` and `pull` are a ref move.
  - `checkout: moving from X to Y` is a checkout.
  - From `rebase (finish|abort)` down to `rebase (start)` is one rebase. All of lazygit's interactive operations go
    through rebase.
  - Everything else is skipped: merges, plain cherry-picks, reverts, `am`, branch creation.
- Its own steps carry `GIT_REFLOG_ACTION=[lazygit undo]` or `[lazygit redo]`. A counter over these markers finds the
  step to undo next, so pressing `z` repeatedly walks back.
- How a step is undone:
  - A commit is undone by a soft reset to the entry before it, so its changes stay staged.
  - A rebase is undone by a hard reset with an automatic stash: stash, `reset --hard`, `stash pop`.
  - A checkout is undone by checking out X by name.
  - Each one asks to confirm.
- Redoing a commit is a *hard* reset.
- Any new action after an undo silently ends redo.
- It refuses while a rebase, merge, cherry-pick or revert is unfinished. A dirty tree is stashed, not refused.
- It cannot undo anything that does not move HEAD: push, fetch, deleting or renaming a branch, tags, stash
  operations, the index, the working tree. Undoing a checkout fails if the branch was deleted since.

### What to take, and what to do better

| lazygit | jazygit |
| --- | --- |
| Commit time shown as entry time | Show the entry time, from `%gd` with `--date=unix`. |
| Whole reflog, every time it changes | Read the top N entries. Read more at the end of the list. Read again only when the reflog changed. |
| No "lost" marking | Mark entries whose commit no ref reaches. Recovering them is the point. |
| No HEAD marker | Mark the entries at HEAD's commit. |
| Undo guessed from English subjects | Undo what the app did, from a snapshot of the refs taken before each operation (§4). |
| Undo blind to branch delete, rename, stash drop | Every ref an operation touches is in the snapshot, so deleting a branch can be undone. |
| Hard reset plus stash pop to undo a rebase | `reset --keep`, which refuses instead of risking a conflicting pop. |
| Redoing a commit is a hard reset | A redo restores the snapshot taken after the step, the same way an undo does. |
| Rewriting keys listed on entries | A reflog scope of its own, holding only the keys that make sense for an entry (§3.4). |
| Errors look like an empty reflog | Say "reflogs are off (`core.logAllRefUpdates`)" or show the error. |

## 2. The features, by priority

**P1, the first release**

1. A reflog of HEAD in the commits pane, newest first.
   - Each row shows the time of the entry, what it was (checkout, commit, reset, rebase, merge, pull,
     cherry-pick…) with its subject, and the short sha.
   - A row is marked when it is at HEAD, and when its commit is lost.
2. Selecting an entry shows that commit's diff in the content panel. Enter opens its files.
3. Actions on an entry: reset (soft, mixed, hard) to it, check it out detached, make a new branch at it, copy it for
   cherry-pick, copy its sha.
4. Undo (`z`) of the last operation the app ran, and redo. Each confirms, and shows exactly which refs move where.
5. jazygit's own reflog entries are marked, by `GIT_REFLOG_ACTION=jazygit: <operation>`, so the reflog tells what the
   app did.

**P2**

1. The reflog of one branch: "history of this branch" from the branches pane. It shows where the tip has been: before
   a force pull, before a rebase, the remote tip it once had.
2. Restore a deleted branch.
   - HEAD's reflog still holds `checkout: moving from X to Y` after X is deleted, and the snapshot in the command log
     holds X's sha.
   - The branches pane offers "recently deleted" while one is known.
3. Filter the reflog with `/`, as commit search does, on subject, sha and action kind.
4. Group repeated runs (`rebase (pick)` × 12) under their `rebase (start)` … `(finish)`, folded by default.
5. Undo across restarts: rebuild the undo list from the reflog's `jazygit:` entries when the command log is empty
   (§4.6).

**P3, or not at all**

- A reflog of the stash. The stash pane already shows it; dropped stashes are covered by undo (§4.3).
- Bisect from an entry, difftool, diffing mode. These belong to commits generally, not to the reflog.
- Expiring or deleting entries (`git reflog expire/delete`). They destroy the safety net; leave them to the CLI.
- The reflog of remote-tracking branches. It is useful only for the force-push guard, which already reads it.

## 3. The reflog view

The read-only view is decided: tabs in panes in [ADR 185](../adr/0185-a-pane-has-tabs-the-features-add.md), and
the reflog of HEAD in [ADR 186](../adr/0186-the-reflog-of-head-read-only.md). What follows is still open.

### 3.4 Keys on an entry

- Bind them under a new `Scopes.REFLOG`, a `Scope<ReflogEntry>`. Hosting `Scopes.COMMITS` would bring amend,
  squash, drop and reword onto entries that are not on the branch.
- Each action adds a binding in its own feature, as `CheckoutAction` already does for `BRANCH_COMMITS`:

| Key | Action | Feature | Note |
| --- | --- | --- | --- |
| `g` | Reset soft, mixed or hard to the entry | changes / reset | Reuses `ResetMsg.Requested(sha)`. A hard reset confirms when work would be lost. |
| space | Check out detached | branches / checkout | `CheckoutMsg.CommitRequested(sha)`. Also offers the branches whose tip is this sha. |
| `n` | New branch at the entry | branches / newbranch | The recovery path. Suggest a name from the subject; for `checkout: moving from X`, suggest `X`. |
| `C` | Copy for cherry-pick | changes / cherrypick | Pasting stays in the commits pane. |
| Enter | Files of the commit | changes / diff | `Changes.OfCommit(sha)` |
| `y` | Copy the sha | new | There is no "copy sha" for commits either, so add it for both. |
| `L`, Escape | Back to the log | changes / reflog | |

- Disabled, with a reason, in the reset dialog:
  - "Hard" while a rebase or merge is unfinished.
  - Every mode when the entry *is* HEAD already.
- Not on entries: drop, squash, fixup, reword, revert, move to branch. They act on the branch's commits.

## 4. Undo and redo

### 4.1 Approach: snapshots, not reflog parsing

Every operation the app runs goes through `OperationCmd` and `OperationUpdate.done`, and ends up in `CommandLog` (at
most 100 entries). So the app knows exactly what it ran. What it does not know is what each operation changed. Add
that as data:

- `RefSnapshot(head, refs)`:
  - `head` is either `OnBranch(name)` or `Detached(sha)`.
  - `refs` is a `Map<RefName, Sha>` over `refs/heads/*`, `refs/tags/*` and `refs/stash`, the last as the list of
    stash shas.
- Before an operation runs, the command takes `before`. It is IO and it lives in the command, not the update, because
  pure code reads no state. Afterwards it takes `after`. Both go onto the `LogEntry`.
- One process each: `git for-each-ref --format=%(refname)%00%(objectname) refs/heads refs/tags` plus
  `git symbolic-ref -q HEAD || git rev-parse HEAD`. Or a single `git for-each-ref` that includes `HEAD`, through
  `--include-root-refs` on git ≥ 2.46. Measure it; the target is under 10 ms. Every operation already rereads state
  afterwards, so `after` may come from that read instead of a process of its own.
- Not every operation snapshots. `Operation` gains an `undo` field (data, not code) saying how it is undone:
  - `Refs` restores refs only.
  - `Soft` restores the checked-out branch with `reset --soft`. Use it for commit and amend, so the changes stay
    staged.
  - `Keep` restores the checked-out branch with `reset --keep`. Use it for rebase, drop, squash, fixup, reword,
    cherry-pick, revert, merge, pull, hard reset and the reset modes.
  - `Checkout` checks the branch or sha in `before.head` back out.
  - `None` is not undoable. Use it for push, fetch, PR operations, stage, unstage and discard of files, stash apply,
    and editing config. Say so in its help, and show it disabled in the undo dialog with the reason.

This covers what lazygit cannot:

- deleting a branch: recreate the ref at its sha;
- renaming a branch: rename it back;
- creating a branch or tag: delete it, but only if it still points where it was made;
- dropping a stash: `git stash store -m <msg> <sha>`, while the commit still exists;
- any combination of these with HEAD moving.

It also needs no English subject parsing.

### 4.2 Marking jazygit's entries

Run every operation's git commands with `GIT_REFLOG_ACTION=jazygit: <operation title>`, through
`GitCommand.withEnvironment` (`core-git/.../process/GitCommand.java:55-57`), and the undo itself with
`jazygit: undo <title>`.

- The reflog then reads `jazygit: drop commit: …` instead of `rebase (finish): …`, and the view can show ⚙ (§3.3).
- Check which commands honour it, and write the answers into facts.
  - `reset`, `rebase`, `cherry-pick`, `merge`, `pull`, `checkout` and `commit` do.
  - `update-ref -m` and `branch` take a message instead.
- Check that rebase's own subjects survive with it set. lazygit relies on `rebase (start)` / `(finish)` together with
  a custom action.

### 4.3 Undoing a step

`UndoUpdate`, pure, turns `(LogEntry, current RefSnapshot, worktree status)` into a plan, or into an error value:

1. **Guard: nothing changed since.**
   - Every ref the step moved must still be where `after` put it, and HEAD where it was.
   - If not, refuse: "main moved since the drop: undo would lose 1 commit". Offer the reflog view instead.
   - This replaces lazygit's counting and its silent redo loss.
2. **Guard: nothing unfinished.** Refuse while a rebase, merge, cherry-pick, revert or bisect is in progress
   (`Unfinished`).
3. **Plan.**
   - For refs other than the checked-out branch: one `git update-ref --stdin` transaction of
     `update <ref> <before> <after>`, `create` and `delete` lines. git checks the old values itself, so a race fails
     whole, never halfway.
   - For the checked-out branch: `reset --soft`, `reset --keep` or `checkout`, as the operation's `undo` says.
   - With `Keep` and `Checkout`, a dirty tree that would conflict makes git refuse, and the error says which files.
     There is no automatic stash. A hard reset whose discarded changes cannot come back says so, and is `None`
     unless the tree was clean before it.
4. **Confirm.** A dialog lists the moves: "main: a1b2c3d → 9f8e7d6 (restores 2 commits)", "feature-x: recreated at
   4c5d6e7", "checkout main". Confirm with Enter.
5. **Record.** The undo is itself a `LogEntry`, with `before` and `after` and a link to the step it undid. That makes
   redo restore the undo's `before` under the same guard, and makes repeated `z` walk further back.

### 4.4 Undo and redo as a stack

- `z` undoes the newest entry that is undoable and not yet undone. `Z` would redo, but it is taken (global maximize).
  Use `ctrl+z` / `ctrl+y`, or `z` with a dialog offering "undo" and "redo". Decide this in the grilling.
- Any new operation after an undo clears the redo list, as lazygit does. Here it is visible: the undo dialog says
  "nothing to redo".
- An undo dialog also lists the last few undoable steps, so that undoing three steps back is one choice. Each later
  step is undone too, newest first, under one confirmation.

### 4.5 Where undo lives

- The command log and `LogEntry` are in kernel-state, so the snapshot field and `Operation.undo` go there too.
  `RefSnapshot` goes in core-repo.
- The undo feature (keys, dialog, `UndoUpdate`, `UndoCmd`) goes in features-changes. Every group sees that.
  Operations in branches, github and forgejo set their own `undo` kind.
- `z` is used by the fold in a diff scope (`FoldAction`). Make undo a global key; the diff scope keeps `z` while it
  has focus. Or pick another key; settle it in the grilling.

### 4.6 Across restarts (P2)

The command log is in memory, so undo ends when the app quits. Two ways to carry it over:

- **(a)** Persist the last N `LogEntry` snapshots per repository and worktree, next to `config.toml`'s state. The
  guard in §4.3 makes stale entries harmless.
- **(b)** Rebuild undo of HEAD moves from `jazygit:` reflog entries, as lazygit does. This is weaker: HEAD only, and
  no deleted branches.

Recommend (a), or neither, since the reflog view covers recovery after a restart.

## 5. Branch reflogs (P2)

- From the branches pane, a key (`H`, for history?) fills the commits pane with `History.reflog("refs/heads/x")`,
  using the same view.
- That branch's moves show: commits, resets, pulls, rebases.
- The keys of §3.4 apply. Reset only makes sense when the branch is checked out; for another branch, offer "move
  branch here", which is `update-ref` with the old value, and is undoable.
- A deleted branch has no reflog (facts: "deleting a branch removes it"). Restoring one (P2 §2.2) uses HEAD's
  `checkout: moving from X` entries and the command log's snapshots.

## 6. Delivery: PRs in order

Each step is a PR of its own, with unit, Pilot and acceptance tests, and facts entries for whatever git behaviour it
learned.

1. **ADR 185 and ADR 186**: done.
2. **Read the reflog in core-git.**
   - `ReflogEntry`, `Action` and its parser, and `History.reflog` with paging.
   - Unit tests for every subject kind, and integration tests against real repositories: empty, logAllRefUpdates
     off, a pruned commit, reftable, a worktree.
   - Facts on timing and on the edge cases.
3. **Tabs in panes, and the reflog tab** (ADR 185, 186): the tab key, the selected tab in the model, the reflog's
   part, paging ahead, `Scopes.REFLOG`, the HEAD marker, rereading, copy sha for commits too. A Pilot test of the tabs
   and the rows.
4. **Lost entries.** The second load and the `✗` marker, with a `Legend`.
5. **Stamp `logs/HEAD`** so external commits show while the reflog is open.
6. **Actions on an entry**, after their own grilling: reset, checkout, new branch, cherry-pick copy, bound in
   `Scopes.REFLOG`, with an acceptance test that a branch at an entry recovers a dropped commit.
7. **`GIT_REFLOG_ACTION` on every operation**, and the `⚙` marker. Acceptance tests check the subjects git writes.
8. **Snapshots on the command log.**
   - `RefSnapshot`, before and after, `Operation.undo` on every operation.
   - An `ArchUnit` or `FeaturesTest` check that no operation leaves `undo` unset.
9. **Undo.**
   - `UndoUpdate` (pure, exhaustively unit tested): the guards, the plans, the stack.
   - `UndoCmd` (an update-ref transaction, then reset or checkout), the dialog, and the key.
   - Acceptance tests: undo each of commit, amend, drop, squash, reword, rebase onto, merge, pull, checkout, branch
     delete, branch rename, stash drop, hard reset on a clean tree. Undo after an outside commit must be refused.
     Then undo, undo, redo.
10. **Redo**, and the multi-step undo dialog.
11. **P2:** branch reflogs, restore a deleted branch, filtering, grouping rebases.

## 7. Questions for the grilling

1. Placement: a listing fill now and tabs later, or general pane tabs first (§3.3)?
2. Keys: `L` for the reflog. `z` for undo, given the fold in diffs. Redo with `ctrl+y`, or inside the undo dialog?
3. Undo scope: is a snapshot-based undo of the app's own operations enough, or must `z` also undo git commands run
   outside the app? lazygit does the latter, through reflog parsing.
4. Hard reset: is it undoable only from a clean tree? Or is it never undoable, and only shown?
5. Should the undo of a commit keep its changes staged (soft), as lazygit does? Or restore the index exactly?
6. Is undo persisted across restarts (§4.6), or does it end with the session?
7. Is the lost-commit check worth its process on every page, or only on request?
8. Do remote ops ever take part, e.g. undo of a force push by pushing back with `--force-with-lease=<ref>:<after>`?
   Recommendation: no, never touch the remote.
