# Acceptance tests: a critical review

2026-10-30. Covers the 148 tests in `acceptance/` and the harness they use (`harness.py`, `pages.py`, `conftest.py`, `fake_gh.py`).

The tests were judged on two things, in this order:

1. **Do they test what the git operations actually leave behind?** This is the main goal.
2. **Is the API correct?** Here that means the page-object API the tests are written in (`ui.files.commit(...)`, `ui.branches.delete(...)`): whether it drives the app faithfully, waits for the right thing, and lets a wrong app fail a test instead of hiding the bug. It also means the app's contract as the tests pin it: which dialogs it asks, what it refuses and what it says.

## Verdict

| Criterion | Score | In short |
|---|---|---|
| Testing the result of git operations | **7/10** | What is tested is tested well: almost every assertion is on git state, in a hermetic environment. But for merge, rebase, cherry-pick and revert only the *conflict* path is tested, and the successful path never is. Several promises in the help text have no test. |
| Correctness of the API | **6.5/10** | The design is good: keys live in one file, waits are state-based, and failures show the screen. But `changing_repo` returns after the *first* change it sees, and several operations run more than one git command without a `done` check, so those tests can race. Dialogs are found by text anywhere on screen, and `_state` cannot see config-only changes. |
| GitHub | **2/10** | 2 tests out of 148. Both are read-only views. Nothing tests the gh operations that change something: checking out, creating, merging, closing, marking a draft, commenting. Most of this *can* be tested; see the GitHub section. |

## What is good

- **The tests assert on git, not on the screen.** Most tests end with `repo.subjects()`, `repo.status()`, `repo.branches()`, `origin.subjects(...)`, `repo.worktrees()`, `repo.stashes()`, `repo.in_progress()` or `repo.read(...)`. A layout change cannot break them, and a bug cannot hide behind a screen that looks right.
- **The environment is hermetic.** Each test gets its own `HOME`, `GIT_CONFIG_GLOBAL=/dev/null`, fixed author, committer and dates, and `GIT_EDITOR=false`. Results are reproducible, and an accidental editor launch fails loudly.
- **Real remotes.** The `origin` fixture is a bare repo and `clone` gives a teammate, so push, pull, force push, gone upstreams and remote deletes are tested against real git transport, not mocks. The test that a pre-receive hook refusing a nuke is reported (`test_when_the_remote_refuses_...`) is a good example.
- **The conflict tests are thorough.** `test_conflicts.py` covers continue, abort and skip for merge, rebase, cherry-pick, revert, drop, amend and pull, including a rebase that stops twice and a skip that stops again on the same file. They also check what comes back after an abort: `rev-parse` equals what it was before, the working tree is restored, and no stash is left behind.
- **Refusals are tested,** and the tests check that the state stayed the same afterwards: the default branch is not deleted, a locked worktree is not removed, continue is refused while conflicts remain, a pop that would overwrite changes is refused.
- **Edge cases that matter:** file names with spaces and accents, a partly staged file, a deleted file, a rename, staging a directory vs. all files, a pop whose staged change no longer applies staged, and worktrees that are locked, missing or detached.
- **The scenarios (`test_scenario.py`)** chain operations the way a person would, and so catch state that leaks from one operation into the next.
- **The harness reports failures well:** the screen at timeout, the exit status and output tail when the app dies, and the last git error when a read kept failing.

## Gaps in coverage of git results

Ordered by how much a bug there would cost a user.

### High: operations whose successful path is never tested

| Operation | Tested | Not tested |
|---|---|---|
| **Merge** (`m`) | Only merges that stop on conflicts (`fast-forward` mode, `merge commit`, `squash and commit`) | A clean merge in any mode. That fast-forward mode really fast-forwards (no merge commit, `parents() == 1`). That `merge commit` makes a merge commit when a fast-forward was possible. The `squash` mode (uncommitted) is never used at all. A squash with nothing to squash commits nothing. |
| **Rebase onto** (`r`) | Only with conflicts | A clean rebase: linear history, the right base, and `ORIG_HEAD`. **Autostash**: the help text promises that uncommitted changes are stashed and brought back. The only test of this is an *abort*. |
| **Cherry-pick** (`C`/`V`) | Only with conflicts | A clean cherry-pick. A failure other than a conflict ("it is undone and the commit stays copied"). Refusing a merge commit. Esc letting go of the copy. |
| **Revert** (`t`) | Only with conflicts | A clean revert. Reverting a merge commit against its first parent, which the help text promises. |

Four of the most-used history operations are tested only on their unhappy path.

**Done.** Each of the gaps in this table now has a test: `test_merge_and_rebase.py` covers merge in all four modes and rebase onto, and `test_cherry_pick_and_revert.py` covers cherry-pick and revert. `branches.merge` waits for the commit of `squash and commit`, which also closes that row of the race in point 1 of the API section.

### High: safety promises with no test

- **Force push uses `--force-with-lease`.** Nothing tests that the lease protects anything. Test it like this: the teammate pushes to `feature` *after* our last fetch, we amend and force-push, and the push must be refused. `origin.subjects("feature")` should still contain the teammate's commit. That is the whole point of the lease, and a regression there destroys other people's work. `review.md` #3 (a force push offered when there is nothing to push) is the same kind of risk, and there is no acceptance test pinning that fix either.
- **Regressions from `review.md` with no acceptance test:** renaming to a name with a leading `-` (#1), pushing a branch made from `origin/develop` going to `develop` (#2), deleting with `-D` without warning (#4; partly covered now by the "anyway?" flow in `delete`), a tag with the same name as the branch being merged (#5). These were reproduced bugs. Each deserves a one-screen acceptance test.
- **Checkout that would overwrite changes.** Only the happy path of `checkout_bringing_changes` is tested. Not tested: declining the offer (nothing must change), and the stash pop conflicting on the target branch (`StashNotRestored`: the stash must stay and the user must be told).
- **Rollback paths.** `CliMoveCommits.move`/`moveTo` undo the checkout and delete the new branch when the cherry-pick fails. `Unfinished.runOrUndo` aborts a failed pull rebase. None of these undo paths is reached by any test, and a broken undo leaves the repo half-moved.

**Done.** `test_safety.py` has a test for each item above. A failing `prepare-commit-msg` hook makes the cherry-pick and the rebase stop with nothing to resolve, which reaches the undo paths. Writing the lease test turned up a gap: **the app fetches as it starts, and the lease is checked against the tracking ref that fetch updated.** So when the teammate pushed *before* that fetch, the force push is offered for a branch that has diverged and goes through: their commit is lost. The test only covers a push made *after* the app's fetch, which the lease does refuse. `git push --force-with-lease --force-if-includes` would refuse the other case too, unless the local branch already has the remote's commits.

### Medium: behaviour the app documents but no test checks

- **Parent recording.** New branch, move to new branch, move onto a branch and new branch worktree all write `branch.<name>.gh-merge-base` (`CliCheckout.recordParent`). Ahead/behind counts, "only the commits not on the base" and stacks are built on it. The one test that uses it (`test_a_branch_says_how_far_it_is_from_its_base`) sets the config by hand. Add `repo("config", "branch.feature.gh-merge-base") == "main"` to `test_new_branch_from_the_checked_out_branch` and its siblings.
- **History with merge commits.** The rewrites run `rebase --rebase-merges`, but no test rewrites below a merge commit. Not tested: rewording, dropping or amending a commit under a merge, and checking that the merge survives (`parents("HEAD") == 2`). The refusals are untested too: squash or drop on a merge commit, squash of the first commit, drop of the only commit, amending a merge that is not the last commit.
- **Reword:**
  - Reword a commit that has a body: is the body kept?
  - Reword with uncommitted changes (autostash).
  - Reword should keep the author and author date. With fixed dates this is a one-line check: `log --format=%an%ad`.
- **Amend when nothing is staged.** Both `A` keys offer to add all changes. That branch of the dialog is never taken.
- **Rename keeps the upstream.** The help text promises it, but `test_rename_a_branch` uses a branch with no upstream.
- **Reset onto upstream:** `mixed` is not tested. `hard` with uncommitted changes (the extra confirmation) is not tested either.
- **Pull:**
  - with uncommitted changes
  - with `pull.ff=only` on a diverged branch, where it should be refused and the refusal said
  - fast-forwarding a non-checked-out branch that is checked out in *another worktree*
- **Stash:**
  - a pop that conflicts: the stash is kept, the conflicts are shown, the files pane shows them
  - stashing when there is nothing to stash
  - stashing only untracked files
- **Worktrees:**
  - prune must *keep* a locked worktree whose directory is gone; the help text says locking prevents pruning
  - removing the worktree that is currently shown must be refused
  - a lock with a reason
  - `new_branch_worktree_from_remote` with a custom name
  - checking out a PR in a worktree (see GitHub)
- **Unborn and detached HEAD.** Only `commit` is tested before the first commit; branch, stash and worktree on an unborn branch are not. Detached HEAD in the main worktree is never shown: commits pane, new branch from it, checkout away from it.
- **Unusual names:** branch names with `/`, unicode or leading dots, and file names with quotes, newlines or leading dashes. The app passes `--` and `GitArgs.*` everywhere, so tests should pin that.

### Low: read-only views whose queries are git logic

These are reads, but the git query is the feature, and a wrong query shows the wrong commits without any error:

- **Commit search (`/`):** `author:`, `path:`, `-S:`, quoted values, smart case.
- **"Only the commits not on the base" (`b`),** in both panes. The base can be the parent config, `origin/main`, or the local `main` or `master`.
- **Divergence view** (upstream options → view divergence).
- **The whitespace toggle** (`git diff -w`).

### Not testable as things stand: background fetch

The app fetches once a minute, and there is no setting to make that shorter. Every remote test fetches by hand (`repo("fetch")`), so the background fetch is never exercised: not `--prune`, not that it never prompts (`GIT_TERMINAL_PROMPT=0`), not that a failing remote is reported. A `-Djazygit.fetchInterval`, like the existing `jazygit.refreshInterval`, would make this a cheap test.

### Assertions that could be stronger

- **There are no checks for "nothing else changed".** Most tests check what should change, but not that the rest was left alone. A cheap `assert_clean_finish(repo)` helper, or an autouse check after the test, would catch a whole class of leaks:
  - no stash left over (unless the test expects one)
  - no operation in progress
  - no `index.lock`
  - no leftover `refs/original`, `ORIG_HEAD`-style or temporary refs
  - other branches still at their SHAs
- `test_squash_a_commit_into_the_one_below` only checks `"Add three" in repo.message()`. It should check that both messages are there, in order, as the app writes them.
- `test_soft_reset_onto_the_upstream_keeps_the_changes_staged` checks the names in `diff --cached`, not `repo.status()`.
- `test_a_drop_that_stops_on_conflicts_is_continued_...` never checks the content of the file.
- `test_delete_a_branch_with_commits_of_its_own` passes whether or not the "anyway?" question was asked. The page object answers it whenever its own oracle expects it. Separate tests should check both sides: that the question *is* asked when commits would be lost, and that answering no keeps the branch.

## Correctness of the page-object API

### 1. `changing_repo` returns on the first observable change (race)

`changing_repo` waits for three things: `_state()` differs from before, nothing is busy, and `done()` holds when it is given. `_busy()` only sees `index.lock` and a running rebase. Between two separate git processes there is no lock, so an operation made of several commands is "settled" after the first one. `caught_up()` does not save it either: if the app refreshes in that gap, it matches git's intermediate state too.

These operations run more than one git command and pass no `done`:

| Page method | Commands the app runs | What the gap looks like |
|---|---|---|
| `branches.checkout_bringing_changes` | `stash push`, `checkout`, `stash pop` (`CliCheckout.checkoutWithStash`) | After the stash: clean tree, one stash, still on `main` |
| `commits.move_to_new_branch` | `checkout -b`, then `branch --force <from>` (`CliMoveCommits.move`) | New branch checked out, `main` not moved back yet |
| `commits.move_to_branch` | `checkout`, `cherry-pick`, `branch --force` | Branch checked out, nothing picked yet |
| `branches.merge(mode="squash and commit")` | `merge --squash`, then `commit` (`CliMerge`) | Changes staged, no commit |
| `branches.new` / `commits.new_branch` | `checkout -b`, then `config branch.X.gh-merge-base` | Fine for refs, but see point 3 |

Today these tests usually win the race, because the app's 100 ms refresh rarely lands in the gap. When one loses, it fails flakily. It does not pass falsely: the later asserts catch the intermediate state. The fix is the one `delete`, `nuke` and `commit` already use: give each of these a `done` that says what the *last* command leaves. Or, from the app side, a "busy" marker the harness can see.

### 2. Dialogs are matched by text anywhere on the screen

`confirm(title)` and `enter_text(title)` call `app.wait_for(title)`, which searches the whole screen, including the command log and the diff pane. Some titles are very generic: `" added"`, `"anyway?"`, `"(enter) confirm"`, `"New branch…"`. If that text is already on screen from a log line or an earlier dialog, the key is pressed before the dialog opens, and it goes to the pane behind. The worst case is that it acts on the wrong item. Scope the search to the dialog: the harness already finds pane borders, and a popup has its own box. At the very least, wait for the dialog's border to show up as well as its text.

### 3. `_state()` cannot see some changes

`_state()` covers refs under `refs/heads` and `refs/remotes`, upstreams, the current branch, status, the stash list, worktrees and the operation in progress. It does not cover:

- **config:** parent recording, an upstream being unset, `push.autoSetupRemote`
- **HEAD's SHA when detached:** a commit made in a detached worktree changes neither `refs/heads` nor, after it, the status
- **tags:** the app has no tag operations yet

A page operation whose only effect is one of these would wait until it timed out. Today none exists, but checking out a PR or making a stacked branch may soon be one.

### 4. Page objects contain the app's rules

`delete` (`unique_commits`), `reset` (`discards`), `commit` (`nothing_staged`), `nuke` (`deletes_remote`) and `remove` (`loses_commits`) each work out for themselves whether the app *should* ask a further question.

- **The good side:** this pins the app's confirmation rules implicitly.
- **The cost:**
  - The rules live in the driver instead of in a test, so no test is named after them.
  - When they disagree with the app, the test fails with a 15-second "timed out waiting for 'anyway?'" instead of an assertion that names the rule.
  - Every test that goes through the method assumes the rule is right.

Make the rule explicit instead: the method takes `expect_warning=True/False`, and a dedicated test per rule checks it. Keep the oracle only as a default.

### 5. Other API points

- The `try_to_*` methods are used together with `wait_for(<message>)` and then an immediate state check. That is fine, since the message comes after the attempt. But no test checks that nothing changes *later*: the app could show the error and then go ahead anyway. A short `eventually` that the state stays unchanged, or one more `caught_up()` before the check, would close that gap.
- `enter_text` relies on `ctrl+e ctrl+u` (readline-style) clearing the field. If the line editor ever changes, every rename and new branch types after the old text. One test should check that pre-filled text is really replaced (rename `feature` → `feat`, which would otherwise become `featurefeat`). Today `rename` passes only because the replacement is complete.
- **Real `gh` is used in tests that don't ask for the `gh` fixture.** `isolated_env` keeps `PATH`, and `gh` is installed in CI (and here, at `/usr/local/bin/gh`). The app asks gh for the pull requests of the branch rows whatever the remote is (which is why the fake's default answer is gh's "no GitHub remote" error), so every test that doesn't ask for `gh` runs the *real* gh, unauthenticated, against local remotes. It fails harmlessly today, but this depends on whichever gh version is installed, and a future gh that behaves differently without auth (prompting, networking) would change every test. Make an always-failing fake gh the default (autouse), and let the `gh` fixture configure it.
- **The fake gh matches answers by substring, first match wins** (`"pullRequests(states: OPEN"`). Rewording a query in `jazygit-gh` breaks tests with a timeout instead of a clear "no answer for this call". Two queries that share a substring can also get each other's answer. Log unmatched calls to the test failure output, so that the timeout shows which call went unanswered.

## GitHub: what is covered, and what could be

### Covered

`test_pull_request_files.py`, 2 tests. Both are read-only:

- The files a PR changes show in the files pane, fetched without leaving a ref behind. This one does check a git result: `"refs/pull" not in for-each-ref`.
- Review threads show next to their lines.

### Not covered

| Feature | gh call | Changes git? |
|---|---|---|
| Check out PR (`space` on the PR screen) | `gh pr checkout N` | **Yes**: new branch, upstream, working tree |
| Check out PR in a new worktree | `git worktree add` + `gh pr checkout N` run *in* the new worktree | **Yes** |
| Create PR (`o` on a branch without a PR) | push first if needed, then `gh pr create --head --base --title --body [--draft]` | **Yes** (the push) |
| Merge PR | GraphQL mutation (`MergeQuery`), polled with `mergeStatus` | No (remote) |
| Close, draft/ready, comment | `gh pr close/ready [--undo]/comment --body` | No |
| Browse branch or PR | `gh browse --branch`, `gh pr view --web` | No |
| Branch indicators | PR, review, CI, merge and stack state on each branch row | No (display) |
| Checks dialog, PR screen, comments | GraphQL reads | No (display) |

### Can it be tested? Mostly yes, without GitHub

1. **Test the command-line contract with the fake we already have.** It is cheap and catches most regressions. The fake already logs every call. Add answers that just succeed, then assert on `gh.calls()`:
   - Create PR: `["pr","create","--head","feature","--base","main","--title","Add parser","--body","…","--draft"]`. Also check that the branch reached origin *before* the create call: the push-first popup, a git result, checked with `origin.branches()`.
   - Close, ready/undo, comment: the exact argv.
   - Merge: the mutation's variables (number, `expectedHeadOid`, method).
   - Browse: that `--web` and `--branch` are passed. The fake must answer them, so that no browser opens.

2. **Teach the fake to do what `gh pr checkout` does to git.** This tests the git result. `gh pr checkout N` amounts to `git fetch origin refs/pull/N/head:<branch>`, a checkout, and setting `branch.<branch>.remote/merge` (for a fork it adds a remote). `fake_gh.py` can run those git commands itself, since `test_pull_request_files.py` already pushes `refs/pull/1/head` to the bare origin. The test then asserts with git as usual:
   - `current_branch()` and `subjects()`
   - `upstream()`
   - for the worktree variant: `repo.worktrees()` gains the new path on the PR branch, and the main worktree's changes stay put

   The fake should also **log its working directory** next to its argv. That pins that `gh pr checkout` runs in the new worktree (`process.in(in)`), and that is the bug the worktree variant is most likely to have.

3. **Refusals and failures through the fake:**
   - gh exits non-zero with a known stderr: the message is shown and nothing changed
   - checkout with a dirty tree is refused before gh runs: assert that `gh.calls()` has no `pr checkout`
   - the merge mutation fails or is queued, which shows the merge-queue state

4. **Keep the fake's answers honest.** The risk is that the hand-written GraphQL answers drift from what GitHub really returns, so the tests stay green while the app breaks for real. Two options:
   - **Recorded fixtures:** a small script runs the real queries once against a sandbox repo and saves the JSON. The tests load those files instead of dicts built in Python. Re-recording shows the drift.
   - **An optional live smoke test:** marked, and skipped without `GH_TOKEN`. It runs nightly against a throwaway repository: create a PR, check it out, comment, close. It never runs on PRs.

5. **Running the real gh against a local fake GitHub** (`GH_HOST` pointing at an HTTPS stub). It works in principle, but you have to manage TLS, `gh auth` and REST plus GraphQL emulation. It costs more than options 1, 2 and 4 together and adds little, so I don't recommend it.

## Suggested next tests, in order

1. Clean merges, in all four modes, including uncommitted `squash`.
2. Clean rebase onto, with uncommitted changes that come back afterwards.
3. Force-with-lease refusing to overwrite a teammate's unfetched push; a force push not offered when nothing is ahead.
4. Clean cherry-pick, clean revert, and revert of a merge commit.
5. `done` predicates for `checkout_bringing_changes`, `move_to_new_branch`, `move_to_branch` and `merge("squash and commit")`.
6. A fake gh by default for every test, and one that logs its working directory.
7. `gh pr checkout` through a fake that does the git work, in place and in a worktree, asserted with git.
8. Argv-contract tests for create, close, ready, comment and merge, with the push-first check on origin.
9. Parent recording asserted in the existing new-branch and move tests.
10. Rewriting below a merge commit (`--rebase-merges`), and the merge-commit refusals.
11. Acceptance tests pinning the fixed bugs from `review.md` (#1, #2, #3, #5).
12. A check after each test that nothing else changed: no stray stash, no operation in progress, no leftover refs.
