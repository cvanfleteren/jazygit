# 180. Merging a pull request on Forgejo

Date: 2026-11-22

## Context

ADR 176 shows the pull request of a branch on Forgejo with `o`, and left merging it for a change of its own, the
first of those after it. On GitHub, `m`, `s` and `r` in the dialog merge it with a merge commit, squashed or rebased
(ADR 24), with no confirmation and the message GitHub writes; ADR 112 lets them merge when ready, and ADR 119 keeps a
merge that failed on the marker of its pull request until the pull request changed.

Forgejo's API (as of `main` at 6840f006da, Codeberg's swagger 17.0.0):

- `POST /repos/{owner}/{repo}/pulls/{index}/merge` merges at once: the pull request is merged and pushed when it
  answers 200. Its body is `Do`, the style, required: `merge`, `squash`, `rebase`, `rebase-merge` (rebased, then a
  merge commit), `fast-forward-only` or `manually-merged`; `MergeTitleField` and `MergeMessageField`, Forgejo's own
  message when empty; `head_commit_id`, which makes it answer 409 `head out of date` once the head moved on;
  `delete_branch_after_merge`; `force_merge`, past the rules of the base branch, for admins; and
  `merge_when_checks_succeed`. `Do` and the two fields of the message have no JSON tag: Go matches `Do` ignoring case,
  as Gitea's `do` is.
- It answers 401 without a token; 403 when the token has no `write:repository` scope; 404 for a pull request that is
  closed; 405 when it may not be merged, with why: a draft, conflicts or a check for them still running, a style the
  repository does not allow, `not allowed to merge [reason: …]` for the rules of its base branch (approvals, checks,
  outdated), or the user may not merge; 409 when merging conflicts, with the error as Go writes it and no `message`, or
  when the head moved on or the push was refused.
- Once merged, deleting the head branch that fails, as the default or a protected branch, answers 403, with the pull
  request merged.
- Forgejo does not apply the repository's `default_delete_branch_after_merge` to the API: without the field, the
  branch is kept. Gitea does since 1.26.
- `GET /repos/{owner}/{repo}` tells anyone who may read the repository which styles it allows
  (`allow_merge_commits`, `allow_squash_merge`, `allow_rebase`, `allow_rebase_explicit`,
  `allow_fast_forward_only_merge`), its `default_merge_style` and `default_delete_branch_after_merge`.
- A pull request's `mergeable` is false for a draft, while conflicts are checked for and for a conflict; it says nothing
  of the rules of the base branch.

## Decision

- **The dialog merges on five keys, one per style Forgejo has**: `m` Merge, `s` Squash and merge, `r` Rebase and
  merge, as on GitHub; `M` Rebase, then merge commit; and `f` Fast-forward only. Not: only GitHub's three: the two
  others take two free keys, `M` and `f`, which GitHub's dialog does not use either. `manually-merged` is not offered:
  it records a merge done elsewhere.
- **A key merges now, as GitHub's merge now does (ADR 24)**: no confirmation, no title or message, as Forgejo writes
  its own, and the dialog closes. The merge is an operation on the branch shown, busy meanwhile ("Merging PR") and
  logged.
- **Only the head as read is merged**: `head_commit_id` is the head the dialog shows. Once it moved on, Forgejo
  refuses, the error says to show it again with `o`, and the pull requests are read again.
- **The repository's styles are read with the pull request** (`GET /repos/{owner}/{repo}`, `ForgejoMergeSettings`): a
  style it does not allow has its key do nothing, its details saying so. When they cannot be read, every key merges,
  and Forgejo refuses a style it does not allow. Not: marking the repository's default style, as each key names its
  own.
- **The keys do nothing for a pull request that is not open, a draft, or does not merge as Forgejo found**, and their
  details say why: for `mergeable` false, that Forgejo found conflicts or is still checking for them, which `R` reads
  again. What the rules of the base branch require Forgejo tells only when it is merged: its refusal is the error.
- **The branch is deleted once merged when the repository deletes it by default**, as Forgejo's web interface does:
  `delete_branch_after_merge` is sent as read, and false when the settings could not be read. The 403 of a branch that
  could not be deleted is a merge, with a notice that Forgejo kept the branch, saying why. The local branch is left as
  it is.
- **Once merged, the remotes are fetched and the open pull requests read again**, as after any operation that changes
  the forge, as the base changed and the pull request is no longer open.
- **Errors say what to do**, as creating does (ADR 178): 401, to run `fj auth login` for the host; 403, that one may not
  merge in the repository, with Forgejo's message; 405, that Forgejo does not merge it, with its message; 409 without a
  message, that it conflicts with its base; others with their status and message.
- **A merge that failed is kept in Forgejo's part** (`FailedMerge`), the last of each pull request: its style, why, and
  how the pull request stood when it was asked, its head, `mergeable` and whether it was a draft, as GitHub's standing
  (ADR 119). Nothing reads it yet: once Forgejo's pull requests show on the branches, their marker can show it and let
  it give way once the pull request changed. It is dropped once the pull request is merged, or no longer listed open.

## Consequences

- A pull request on Forgejo is merged in jazygit, in any style its repository allows, with fj's token, which needs the
  `write:repository` scope.
- Each read of a pull request asks Forgejo once more, for its repository.
- A merge that the rules of the base branch block, missing approvals or a check, is refused with Forgejo's reason;
  merging once they are met is ADR 181's.
- No acceptance test covers it, for the reason of ADR 176. The feature's tests run its update, commands and dialog on
  a Forgejo of the test's, and core-forgejo's run the requests against a server of the test's.
- ADR 176 is amended: merging comes as this says.

Amended by ADR 181: a pull request its base branch holds is merged when its checks succeed.
