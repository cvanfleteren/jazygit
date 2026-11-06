# Acceptance tests: a critical review

2026-11-06, at `fa72e13` on main. This replaces the review of 2026-10-30, which was taken at 148 tests. Since then
the suite has grown to 228 tests, and most of what that review asked for has been done. This version checks those
"Done" notes against the code, and says what is still open and what is new.

The tests are judged on the same two things, in this order:

1. **Do they test what the git operations actually leave behind?** This is the main goal.
2. **Is the API correct?** Here that means the page-object API the tests are written in (`pages.py`): whether it
   drives the app faithfully, waits for the right thing, and lets a wrong app fail a test instead of hiding the bug.

## Verdict

| Criterion | 2026-10-30 | Now | In short |
|---|---|---|---|
| Testing the result of git operations | 7/10 | **8.5/10** | Merge, rebase, cherry-pick and revert now have tests where they succeed. `test_safety.py` covers the undo paths and the fixed bugs from `review.md`. Merge commits, detached and unborn HEADs, and unusual names are covered too. What is left: one open data-loss hole (see the force-push item under "Still open"), the read-only queries, and no check that nothing else changed. |
| Correctness of the API | 6.5/10 | **7/10** | `merge`, `new`, `new_from_remote`, `new_branch_worktree` and both moves now wait for their last git command. `try_to_*` and the "asks nothing" pattern make refusals testable. But dialogs are still found by text anywhere on screen, `checkout_bringing_changes` still races, and the fetch the app now runs at start opens a new race. |
| GitHub | 2/10 | **5/10** | Up from 2 tests to 14. The fake gh is used well: tests check the exact argv of stack, edit and review-thread calls, and one stack test checks the git result. Checking out, creating, merging and closing a PR are still untested. |

## Verified as done

I checked each "Done" note in the earlier review against the tests and `pages.py`. They hold, with the exceptions
noted under "Still open".

- **Successful paths.**
  - `test_merge_and_rebase.py` covers all four merge modes. It checks parents, `HEAD^1`/`HEAD^2`, files, and the
    order of the squash message. It also covers a squash with nothing to squash.
  - Rebase onto is tested with `merge-base`, `ORIG_HEAD` and no merges left, and also with uncommitted changes.
  - `test_cherry_pick_and_revert.py` covers a clean cherry-pick (same diff, source branch untouched), a failure
    that is undone with the copy kept, the merge-commit refusal, letting go of the copy with Esc, a clean revert
    with git's exact message, and the revert of a merge against its first parent.
- **Safety.** `test_safety.py` covers:
  - the lease refusing a push made after the last fetch
  - no force push offered for a branch that is only behind its upstream
  - a rename to `--force` refused
  - no push onto `develop` from a branch made from `origin/develop`
  - "delete anyway?" answered no keeps the branch
  - a merge or rebase onto a branch that has a tag of the same name
  - declining to stash on checkout, and a stash that cannot be popped staying in the stash
  - the undo of both moves and of a pull rebase

  A `prepare-commit-msg` hook that fails reaches those undo paths, and the test checks that it did fail
  (`failed.exists()`), so a test cannot pass without reaching the path it means to test. This is a good technique.
- **Documented behaviour.**
  - The parent recording is asserted with `repo.parent(...)`, including "a commit is no branch to come from".
  - Reword, drop and amend below a merge keep the merge. The merge refusals are tested.
  - Reword keeps the body, the author and the date.
  - Amend with nothing staged.
  - Rename keeps the upstream.
  - Reset onto the upstream, mixed and hard.
  - Pull with uncommitted changes, with `pull.ff=only`, and of a branch checked out in another worktree.
  - Stash: a pop that conflicts, nothing to stash, only untracked files, before the first commit.
  - Prune keeps a locked worktree; the worktree shown is not removed; a lock with a reason.
  - Unborn and detached HEADs; branch and file names with `/`, accents, quotes and leading dashes.
- **A bug the new tests found.** A file with a newline in its name breaks the files pane. It is pinned as a strict
  `xfail`, so the test will start failing, as a reminder, the day it is fixed. This is the right way to record a
  known bug.

## Still open: testing the result of git operations

### High

- **A force push with `P` can still overwrite a teammate's commits.** The earlier review's own "Done" note found
  this. It is still there. `PushUpdate` and `MoveUpdate` push with `PushMode.FORCE_WITH_LEASE`, which is plain
  `--force-with-lease` (`CliRemotes.push`, line 147). Only the stack push (`pushAll`) adds `--force-if-includes`.
  The app fetches when it starts (`RepositorySession`, line 162), and a teammate's push that this fetch brings in
  passes the lease. So pressing `P` and confirming the force push drops their commits.

  The test only covers a push made after the app's fetch: that is why `test_a_force_push_does_not_overwrite...`
  waits for "fetched". Add the other case as a strict `xfail`, the way the newline bug is pinned: the teammate
  pushes, the app starts, and `origin.subjects("feature")` must still have their commit. Then the bug is in the
  suite, not only in prose.

  This also applies to the force push that moving commits off a pushed branch now runs (`MoveUpdate`, line 229),
  which has no acceptance test at all.

- **A rebase loses what was staged.** `test_rebase_brings_the_uncommitted_changes_back_afterwards` asserts that
  what was staged comes back unstaged, because `git rebase --autostash` pops without `--index`. The help text says
  the changes are "brought back". The app's own stash for checkout does bring the staging back (see
  `test_checkout_brings_the_changes_across_through_a_stash`). Rebase, drop and reword all autostash this way. Is
  this what you want? If not, the test pins a bug as the expected behaviour.

### Medium

- **No check that nothing else changed.** The new tests are better about this: many check `status() == {}`,
  `stashes() == []` and the other branch's SHA by hand. But there is still no shared post-condition. A helper
  called at the end of each operation test would catch leaks no single test thinks of:
  - no stash left over unless expected
  - nothing in progress
  - no `index.lock`
  - no leftover `refs/original` or temporary refs
  - the branches the test did not touch at their SHAs
- **Moving commits off a pushed branch force-pushes it** (`MoveUpdate`, line 229). This is new behaviour, and no
  test checks what reaches origin.
- **Checking out a PR in a worktree.** The earlier "Done" note leaves this to the GitHub section. See there.

### Low: read-only views whose queries are git logic

All still untested:

- **Commit search (`/`):** `author:`, `path:`, `-S:`, quoted values, smart case.
- **"Only the commits not on the base" (`b`),** in both panes. The parent recording that feeds it is now asserted,
  but no test checks which commits the view then lists.
- **The divergence view** (upstream options → view divergence).
- **The whitespace toggle.**

A wrong query here shows the wrong commits without any error, so a test per view still pays off.

### Not testable as things stand: the fetch every minute

The interval is still a constant (`Timers.FETCH_INTERVAL`). A `-Djazygit.fetchInterval`, like
`jazygit.refreshInterval`, would make it testable. It would also let the tests turn off the fetch at start, which
causes the race below.

## Still open: correctness of the page-object API

### 1. New: the fetch at start races with the first operation

The app now fetches as it starts. `acceptance/README.md` still says it does not ("as the app does every minute, but
not at its start", and "the app fetches only once a minute").

`changing_repo` snapshots `_state()`, which includes `refs/remotes`, and returns once that changes. In a test whose
teammate pushed without `repo("fetch")`, the first operation can see this happen:

1. `caught_up()` passes before the fetch lands: the app and git still agree on the old refs.
2. The snapshot is taken.
3. The fetch lands, and that change alone satisfies the wait.

The operation then "returns" before its own git command ran. These tests run that way:

- `test_pull_fetches_what_was_not_fetched_yet`
- `test_a_pull_whose_rebase_fails_is_undone`, which uses `try_to_pull`, but its later `caught_up()` can still
  catch it mid-way
- the stack tests

`test_safety` already waits for "fetched" before it goes on. Do that in `caught_up()` itself: wait until the
startup fetch is done (the status line says so), or turn it off with a property in the tests that don't want it.
Also fix the README.

### 2. `checkout_bringing_changes` still has no `done`

It runs `stash push`, `checkout` and `stash pop`, and returns once the first of them changed something.
`test_safety` works around it for the error case by using `try_to_checkout`. The happy path in
`test_checkout_brings_the_changes_across_through_a_stash` can still be read half-way. Make it wait until it is on
`branch` and the stash count is back to where it was.

### 3. Dialogs are still matched by text anywhere on screen

`confirm` and `enter_text` are unchanged, and the new ones lean on generic titles too: `"Commit all files?"`,
`"push.default="`, `"anyway?"`, `" added"`. The new tests read more of the command log as well (`"git cherry-pick
--abort" in ui.app.text()`), so log text and dialog titles share the screen more than before. Scope `wait_for` for
dialogs to the dialog's box.

### 4. Negative assertions read the screen

Many of the new refusal tests are good and check git. But a few rest only on what is *not* on the screen:

- `test_a_squash_merge_with_nothing_to_squash_commits_nothing`: `"git commit" not in ui.app.text()`
- `asks_nothing`
- `test_a_merge_commit_cannot_be_copied`

The "keys are handled in turn, so once the focus moved on…" reasoning is sound, and it is written down where it is
used. But a command log that scrolls, or a dialog drawn one frame late, makes such a test pass for the wrong reason.
Where git can say it, add git's word too. For the squash case, that is `git reflog -1` unchanged.

### 5. Unchanged from the earlier review

- **`_state()` cannot see config-only changes.** The page objects now work around this with `done`
  (`repo.parent(...)`), which is fine.
- **Page objects encode the app's confirmation rules** (`unique_commits`, `discards`, `stage_all`). There are now
  dedicated tests for the "anyway?" and "hard reset" questions, which removes most of the concern. The oracle
  failing as a 15-second timeout remains.
- **Real `gh` runs in every test that does not ask for the `gh` fixture.** `isolated_env` keeps `PATH`, and the app
  asks gh for pull requests at start. Make an always-failing fake the default (autouse).
- **The fake gh does not say which call went unanswered.** It is still matched by substring, first match wins. Now
  that 14 tests depend on it, print unmatched calls on the test failure, so that a renamed query fails with a clear
  message instead of a timeout.

## GitHub

### What is covered now

14 tests in three files:

- **`test_pull_request_files.py` (8):** the files of a PR; review threads that are shown, collapsed, filtered,
  resolved, replied to, started, deleted and edited; opening a remark in the browser through `$BROWSER`. The
  mutations are checked by their argv and variables (`thread=`, `comment=`, `body=`, `commit_id=`, `line=`,
  `side=`). One test checks that a slow answer is not sent twice, using the new delay in the fake. Another checks
  that the threads are not read again after an edit or a delete.
- **`test_pull_request_edit.py` (1):** an edited title is saved with exactly
  `pr edit 1 --title "Add b and c"`, and nothing else is sent.
- **`test_stacks.py` (5):** making, unstacking, adding to and stacking on, each asserted by its exact `gh api` call.
  One of them checks that no `pr edit` is sent. **The stack rebase and push is checked with git:** the history on
  origin, the local branch matching its remote, and the branch that was checked out being checked out again.

This is the first approach from the earlier review (test the argv contract), and it is done well.

### Still not covered

| Feature | Changes git? | How to test it |
|---|---|---|
| Check out a PR (`gh pr checkout N`) | **Yes** | Let the fake do what gh does: `git fetch origin refs/pull/N/head:<branch>`, check it out, set `branch.<b>.remote/merge`. Then assert with git as usual. `test_pull_request_files.pull_request` already pushes `refs/pull/1/head`. |
| Check out a PR in a new worktree | **Yes** | The same, plus log the working directory in `fake_gh.py`: that pins that gh runs in the new worktree, which is what this variant is most likely to get wrong. |
| Create a PR (push first, then `gh pr create`) | **Yes** (the push) | Assert `origin.branches()` before the create call, and the exact `--head/--base/--title/--body/--draft` argv. |
| Merge a PR (GraphQL mutation, then polling) | No | Check the mutation's variables (number, expected head, method). Use the delayed answer to step through queued, merged and failed. |
| Close, draft/ready, top-level comment | No | Check the argv, as with `pr edit`. |
| Checks dialog, branch row indicators | No | Read-only rendering; lower priority. |

### Keeping the fake honest

This is still open. All the GraphQL answers are dicts written by hand in the tests. Recording them once from the
real `gh` against a sandbox repository, and loading them from files, would show when GitHub's shape drifts. An
optional live smoke test, skipped without `GH_TOKEN`, could run nightly.

## Suggested next steps, in order

1. Close the force-push hole: `--force-if-includes` for `P` and for the force push after a move. Pin it first with
   a strict `xfail` acceptance test for a push that the fetch at start brought in.
2. Make `caught_up()` wait for the fetch at start, or turn that fetch off for the tests, and fix the README.
3. Add a `done` to `checkout_bringing_changes`.
4. Decide whether rebase, drop and reword should keep what was staged, and fix the test or the app.
5. Check out a PR, in place and in a worktree, through a fake gh that does the git work and logs its directory.
6. Create a PR: push first, then the exact `pr create` argv. Then merge, close, ready and comment.
7. Make a failing fake gh the default for every test, and have the fake report calls it could not answer.
8. Add a post-condition helper that checks nothing else changed.
9. Scope the dialog waits to the dialog's box.
10. Test the queries: commit search, only-ahead and divergence.
