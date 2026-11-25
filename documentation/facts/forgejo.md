# Forgejo and fj

What Forgejo and fj do that jazygit relies on, learned by testing, reading the source or docs, or measuring. Each fact
says when it was learned, so a fact that has grown old can be checked again; see README.md.

## Detection and hosts

- `GET /api/forgejo/v1/version` answers only on Forgejo, not on Gitea or other hosts. *Why it matters:* it is how
  jazygit tells that a remote's host is Forgejo. — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
- Forgejo and Gitea share one API under `/api/v1`. *Why it matters:* they are treated as one forge, which tells them
  apart itself where they differ. — learned 2026-11-22 · [ADR 0174](../adr/0174-how-the-last-build-on-forgejo-went.md),
  [ADR 0175](../adr/0175-each-forge-has-its-own-pull-request-dialogs.md)
- A Forgejo behind a reverse proxy answers 5xx while Forgejo restarts. *Why it matters:* a 5xx during detection is
  treated as "could not be reached" and asked again later, not as "not Forgejo". — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
- Codeberg runs Forgejo. *Why it matters:* Codeberg's swagger is a reference for Forgejo's API. — learned 2026-11-22 ·
  [ADR 0174](../adr/0174-how-the-last-build-on-forgejo-went.md), [ADR
  0180](../adr/0180-merging-a-pull-request-on-forgejo.md)

## fj

- fj (forgejo-cli) keeps a token per host in `keys.json` under `$XDG_DATA_HOME/forgejo-cli`, else
  `~/.local/share/forgejo-cli`. It also keeps aliases from an ssh host and port to the web host
  (`code.example.net:10022` → `code.example.net`), which `fj auth login` makes. *Why it matters:* jazygit reads fj's
  token and aliases, so `fj auth login` is the only setup, and an ssh remote is mapped to its https host. — learned
  2026-11-22 · [ADR 0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
- fj has no command that gives a commit's status: `fj actions tasks` prints the repository's last 20 Actions jobs as
  text, with a short sha and no branch, and leaves out the statuses of other CIs. *Why it matters:* jazygit calls
  Forgejo's HTTP API with the JDK client instead of going through fj. — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
- fj sends its token to a host it logged in to over plain `http://`. *Why it matters:* this is one of the precedents for
  jazygit sending fj's token over plain HTTP to an `http://` remote. — learned 2026-11-22 · [ADR
  0179](../adr/0179-fjs-token-goes-where-the-remote-points.md)

## Commit statuses

- `GET /api/v1/repos/{owner}/{repo}/commits/{ref}/status` gives a commit's combined status: one `state` (`success`,
  `pending`, `failure`, `error`, `warning`, or empty for no status) and the statuses it is made of. *Why it matters:* it
  is the source of the build badges on Forgejo branches. — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
- Forgejo Actions posts one commit status per job, named `<workflow> / <job> (<event>)`. Other CIs post theirs the same
  way. *Why it matters:* statuses are shown by name as checks. — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
- `GET /repos/{owner}/{repo}/commits/{ref}/statuses` lists every status posted for a commit, paged, each with `id`,
  `context`, `status`, `description`, `target_url` and `created_at`; the combined status keeps only the last of each
  context. *Why it matters:* the checks popup folds all of them, to time each check from its first status. — learned
  2026-11-25 · [ADR 0195](../adr/0195-the-checks-of-a-branch-on-forgejo-one-by-one.md) · Forgejo 17.0.2 swagger
- Forgejo Actions posts a job's status when it is made (`Waiting to run`, or `Blocked by required conditions`), when it
  starts (`Has started running`) and when it ends (`Successful in …`, `Failing after …`, `Has been skipped`, `Has been
  cancelled`), and anew when it is run again; these texts are not translated. Its link is
  `…/actions/runs/<run number>/jobs/<place in the run>`, maybe with `/attempt/<n>`. *Why it matters:* a check is queued,
  started and done as these say, and its job is found from its link. — learned 2026-11-25 · [ADR
  0195](../adr/0195-the-checks-of-a-branch-on-forgejo-one-by-one.md) · Forgejo 17.0.2 binary
- Forgejo 17 has `GET /actions/runs?run_number=`, `GET /actions/runs/{id}/jobs`, `GET /actions/jobs/{id}/logs` (plain
  text) and `POST /actions/jobs/{id}/rerun` (204; a done job and the jobs that need it). *Why it matters:* `e` and `r`
  in the checks popup use them; an older Forgejo answers 404. — learned 2026-11-25 · [ADR
  0195](../adr/0195-the-checks-of-a-branch-on-forgejo-one-by-one.md) · Forgejo 17.0.2 swagger
- A public repository's API can be read without a token, but a private repository without a token fails on every
  request. *Why it matters:* a failed read is retried only after the next fetch, not every 10 s. — learned 2026-11-22 ·
  [ADR 0174](../adr/0174-how-the-last-build-on-forgejo-went.md)

## Pull requests

- `GET /repos/{owner}/{repo}/pulls?state=open` lists the open pull requests, at most 50 a page by default. *Why it
  matters:* jazygit pages 50 at a time, up to 10 pages. — learned 2026-11-22 · [ADR
  0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)
- A Forgejo host may serve fewer items per page than asked, without saying so. *Why it matters:* paging stops only on an
  empty page, so each listing costs one extra request. — learned 2026-11-22 · [ADR
  0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)
- `GET /repos/{owner}/{repo}/pulls/{index}` gives a pull request's state, `draft`, `mergeable`, `merged`, author,
  labels, comments, additions, deletions, changed files, requested reviewers, head and base (each with `ref`, `sha` and
  repository), and when it was opened and last changed. *Why it matters:* these are the rows Forgejo's dialog can show.
  — learned 2026-11-22 · [ADR 0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)
- Forgejo gives no count of a pull request's commits anywhere in its API. *Why it matters:* the changes row shows `+/−`
  and files, without commits. — learned 2026-11-22 · [ADR 0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)
- `GET /repos/{owner}/{repo}/pulls/{index}/reviews` gives each review's `state` (`APPROVED`, `REQUEST_CHANGES`,
  `COMMENT`, `PENDING` or `REQUEST_REVIEW`) and whether it is `stale` or `dismissed`, paged. *Why it matters:* the
  review standing is computed from each reviewer's last non-dismissed approve or request-changes review. — learned
  2026-11-22 · [ADR 0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)
- A pull request's `mergeable` is false for a draft, while conflicts are still being checked, and when it conflicts. It
  says nothing about the rules of the base branch. *Why it matters:* "merges: no" can't be read as a conflict for a
  draft or while checking, and branch rules must be read separately. — learned 2026-11-22 · [ADR
  0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md), [ADR
  0180](../adr/0180-merging-a-pull-request-on-forgejo.md), [ADR
  0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- Forgejo has no stacked pull requests and no merge queue, and has commit statuses rather than GitHub's check runs and
  workflows. *Why it matters:* Forgejo's dialog leaves out the stack, the merge queue and the checks bar. — learned
  2026-11-22 · [ADR 0175](../adr/0175-each-forge-has-its-own-pull-request-dialogs.md), [ADR
  0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)

## Creating a pull request

- `POST /repos/{owner}/{repo}/pulls` requires `head`, `base` and `title`; `body` is optional. *Why it matters:* this is
  the create request jazygit sends. — learned 2026-11-22 · [ADR 0178](../adr/0178-creating-a-pull-request-on-forgejo.md)
- A head in another repository is written `owner:branch` and must be that owner's fork of the target repository, one hop
  away. Otherwise the answer is 404, the same as when the token cannot read either repository. Gitea writes the head the
  same way. *Why it matters:* a 404 on create is explained as "that remote must be a fork you can read". — learned
  2026-11-22 · [ADR 0178](../adr/0178-creating-a-pull-request-on-forgejo.md)
- Forgejo has no draft field on create: a pull request is a draft when its title starts with one of the server's
  `WORK_IN_PROGRESS_PREFIXES`, `WIP:` and `[WIP]` by default, and no endpoint exposes that setting. *Why it matters:*
  the draft box prepends `WIP: `, and a server with other prefixes won't treat it as a draft. — learned 2026-11-22 ·
  [ADR 0175](../adr/0175-each-forge-has-its-own-pull-request-dialogs.md), [ADR
  0178](../adr/0178-creating-a-pull-request-on-forgejo.md)
- Creating a pull request answers 201 with the pull request, 401 without a token, 403 when the token lacks the
  `write:repository` scope, 409 when an open pull request with the same head and base exists (naming its number only in
  the message text), and 422 when head and base are the same string. *Why it matters:* each status gets its own
  actionable error, and the 409's number is parsed from the message. — learned 2026-11-22 · [ADR
  0178](../adr/0178-creating-a-pull-request-on-forgejo.md)
- Forgejo does not refuse a head with no commit missing from the base: it creates the pull request, empty. *Why it
  matters:* jazygit refuses that case itself before sending. — learned 2026-11-22 · [ADR
  0178](../adr/0178-creating-a-pull-request-on-forgejo.md)

## Merging

- `POST /repos/{owner}/{repo}/pulls/{index}/merge` merges at once, merged and pushed when it answers 200. Its required
  body field `Do` is the style: `merge`, `squash`, `rebase`, `rebase-merge` (rebase, then a merge commit),
  `fast-forward-only` or `manually-merged`. *Why it matters:* this is where the five merge keys come from;
  `manually-merged` only records a merge done elsewhere. — learned 2026-11-22 · [ADR
  0180](../adr/0180-merging-a-pull-request-on-forgejo.md) · Forgejo main 6840f006da, Codeberg swagger 17.0.0
- The merge body's `Do`, `MergeTitleField` and `MergeMessageField` have no JSON tag, so Go matches them ignoring case
  (Gitea's `do`). Leaving the title and message empty makes Forgejo write its own. *Why it matters:* field-name casing
  is not critical, and jazygit sends no message. — learned 2026-11-22 · [ADR
  0180](../adr/0180-merging-a-pull-request-on-forgejo.md) · Forgejo main 6840f006da
- The merge body's `head_commit_id` makes Forgejo answer 409 `head out of date` once the head has moved on.
  `force_merge` bypasses the base branch rules (admins only). `delete_branch_after_merge` deletes the head branch. *Why
  it matters:* jazygit sends the head it showed, so only what the user saw is merged. — learned 2026-11-22 · [ADR
  0180](../adr/0180-merging-a-pull-request-on-forgejo.md) · Forgejo main 6840f006da
- Merge answers: 401 without a token; 403 without the `write:repository` scope; 404 for a closed pull request; 405 when
  it may not be merged, with the reason (a draft, conflicts or a conflict check still running, a style the repository
  disallows, `not allowed to merge [reason: …]` for base branch rules such as approvals, checks or outdated, or the user
  may not merge); 409 when merging conflicts (with the error as Go writes it and no `message`), when the head moved on,
  or when the push was refused. *Why it matters:* each status maps to its own error, and a 409 without a message is
  reported as a conflict. — learned 2026-11-22 · [ADR 0180](../adr/0180-merging-a-pull-request-on-forgejo.md) · Forgejo
  main 6840f006da
- When deleting the head branch after a merge fails (for example the default or a protected branch), Forgejo answers
  403, but the pull request is already merged. *Why it matters:* that 403 is treated as a merge, with a notice that the
  branch was kept. — learned 2026-11-22 · [ADR 0180](../adr/0180-merging-a-pull-request-on-forgejo.md)
- Forgejo does not apply the repository's `default_delete_branch_after_merge` to API merges: without
  `delete_branch_after_merge` the branch is kept. Gitea applies it since 1.26. *Why it matters:* jazygit reads the
  setting and sends it explicitly, as Forgejo's web UI does. — learned 2026-11-22 · [ADR
  0180](../adr/0180-merging-a-pull-request-on-forgejo.md) · Gitea 1.26
- `GET /repos/{owner}/{repo}` tells anyone who can read the repository which merge styles it allows
  (`allow_merge_commits`, `allow_squash_merge`, `allow_rebase`, `allow_rebase_explicit`,
  `allow_fast_forward_only_merge`), plus its `default_merge_style` and `default_delete_branch_after_merge`. *Why it
  matters:* disallowed styles are disabled, at the cost of one more request per pull request read. — learned 2026-11-22
  · [ADR 0180](../adr/0180-merging-a-pull-request-on-forgejo.md)

## Scheduled merge (when checks succeed)

- Merging with `merge_when_checks_succeed` always answers 201 (scheduled), even when the rules are already met. Forgejo
  then merges only on the next event that makes it look again: a passing status on the head, an approving review, a
  dismissed review, or a finished conflict check (as after a push). Gitea looks at once. *Why it matters:* a green pull
  request must be merged now, not scheduled, or it waits indefinitely, so jazygit re-reads the checks just before
  sending. — learned 2026-11-22 · [ADR 0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md) · Forgejo main
  6840f006da
- A scheduled merge waits for every rule of the base branch, approvals included, not only checks. On a branch without
  rules, any of the triggering events merges it. *Why it matters:* the key's details list both required checks and
  approvals. — learned 2026-11-22 · [ADR 0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- `head_commit_id` is not checked when scheduling, and a push does not cancel a scheduled merge, so whatever is pushed
  meanwhile gets merged. *Why it matters:* the user is warned that later pushes are merged too. — learned 2026-11-22 ·
  [ADR 0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- Scheduling a second time answers 409 `pull request is already scheduled to auto merge when checks succeed`. *Why it
  matters:* that 409 is reported as "already scheduled" and the pull request is re-read, not shown as a failure. —
  learned 2026-11-22 · [ADR 0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- `DELETE /repos/{owner}/{repo}/pulls/{index}/merge` cancels a scheduled merge, for whoever scheduled it or may merge:
  204, or 404 when none is scheduled. *Why it matters:* a 404 on cancel means it was already merged or dropped, and the
  stale schedule is hidden. — learned 2026-11-22 · [ADR 0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- No field of a pull request says that its merge is scheduled. The timeline (`GET
  /repos/{owner}/{repo}/issues/{index}/timeline`) has `pull_scheduled_merge` (with who and when) and
  `pull_cancel_scheduled_merge` events, but not the merge style. A scheduled merge that Forgejo tried and failed, or a
  pull request merged by hand, leaves no event, so the timeline still says it is scheduled. *Why it matters:* jazygit
  reads the timeline (paged by 50), remembers the style itself, and treats a 404 on cancel as the end of a stale
  schedule. — learned 2026-11-22 · [ADR 0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- `GET /repos/{owner}/{repo}/branches/{b}` gives anyone who can read it the branch's `protected`, `enable_status_check`,
  `status_check_contexts` (globs of required checks) and `required_approvals`. *Why it matters:* this decides whether a
  key merges now or schedules. — learned 2026-11-22 · [ADR
  0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- Forgejo counts required checks as passed as follows: without contexts, the combined state of all statuses must pass
  (none at all never passes). With contexts, every glob must match at least one check, and all checks it matches must
  have passed. *Why it matters:* jazygit reproduces this rule to label the keys. — learned 2026-11-22 · [ADR
  0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- Forgejo compiles status-check globs with gobwas' glob without separators: `*`, `?`, `[…]`, `[!…]` and `{a,b}`; `\`
  escapes the next character; any other character matches only itself. A class is a range `[a-z]`, or else characters up
  to `]`, each literal (`-` included). A trailing `\` is dropped. A glob that does not compile (an unclosed class or
  brace, an empty class, or a range not closed right after it) is skipped. *Why it matters:* jazygit's matcher must
  agree with Forgejo's on which checks are required. — learned 2026-11-22 · [ADR
  0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
- Forgejo counts approvals only from reviewers it trusts, and does not say which reviewers those are. *Why it matters:*
  jazygit's approval count can be too high, so a merge-now may still be refused. — learned 2026-11-22 · [ADR
  0181](../adr/0181-merging-on-forgejo-when-the-checks-succeed.md)
