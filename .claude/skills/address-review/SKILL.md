---
name: address-review
description: Work through claude[bot]'s review findings on the current PR (assess, fix with failing-first tests, verify, push, reply on threads) until it says "Ready to merge". Use when the user says "address review", "fix the review", or points at review findings on a jazygit PR.
---

# Address review

Arg: optional PR number; default is the PR for the current branch (`gh pr view --json number`).

## 1. Collect
- Latest review verdict: `gh api repos/{owner}/{repo}/pulls/<n>/reviews` — take the last claude[bot] entry (CHANGES_REQUESTED "Merge after minor fixes" / APPROVED "Ready to merge").
- Unresolved inline threads: GraphQL `reviewThreads(first:100){nodes{id isResolved comments(first:10){nodes{databaseId author{login} body path line}}}}`; keep `isResolved == false`.
- The summary comment (`gh pr view <n> --comments`) for findings that have no thread.
- CI: `gh pr checks <n>`.

If the verdict is "Ready to merge", CI is green and no threads are open → report that and stop.

## 2. Assess — then STOP
A table: finding · severity tag · file:line · verdict (fix / disagree + reason / needs design call).
Anything that conflicts with an ADR or changes behaviour the user settled is "needs design call".
Wait for the user's ok. Do not edit before that.

## 3. Fix (per accepted finding)
- Write the test first and watch it fail for the stated reason; then fix. Untested-branch findings: the test *is* the fix.
- Touch only what the finding names. No drive-by refactors.
- Help text, keymap docs and the PR description must still agree with the code.

## 4. Self-review before pushing
Re-read `git diff origin/main...HEAD` as the bot would: off-by-one in line/col math, past-the-end / empty / narrow-width states, equals/hashCode on anything compared per frame, duplicated logic that can drift, keys shown disabled in help. Fix what you find (same test-first rule).

## 5. Verify
- `git merge origin/main` first if behind — CI tests the merge.
- `env -u GH_TOKEN ./mvnw verify` (not `test`; checkstyle only runs in verify). With `-pl`, add `-am`.
- Spotless reformats locally: make no edits after the last verify, or verify again.
- In a worktree: `nix develop <wt> -c ./mvnw verify` (direnv is blocked there).

## 6. Commit, push, reply
- `git status` before staging; never `add -A` blindly.
- One commit per finding (or a coherent group); message says what behaviour changed.
- Push (force-with-lease only if you rebased).
- Reply on each thread: `gh api repos/{owner}/{repo}/pulls/<n>/comments/<id>/replies -f body="Fixed in <sha>: <one line>. Test: <TestClass#method>."`
  For "disagree": the reason, no commit.

## 7. Watch
`gh pr checks <n> --watch`, then wait for the next claude[bot] review. New findings → back to step 1.
Flaky-looking CI failure: re-run once (`gh run rerun --failed`); if it fails again, report it, don't loop.

## Report
Table: finding · action · commit · test · status. Then the final verdict and CI state.
