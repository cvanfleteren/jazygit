# 195. The checks of a branch on Forgejo, one by one

Date: 2026-11-25

## Context

On GitHub, `C` on a branch, and in the pull request dialog, lists the checks on its tip one by one (ADR 32): counts,
when they started and ended, a bar of how far they are (ADR 89), each with how long it took or runs, read again while
one runs (ADR 34); `n`/`N` and a click pick one, `e` opens its log in an editor (ADR 160), `r` reruns the failed jobs
(ADR 135), and the keys other features add to a failed build act on what failed (ADR 161). On Forgejo a branch only has
a badge (ADR 174), and its pull request dialog lists the failed and running checks by name (ADR 176).

Forgejo has commit statuses, not check runs (as of Forgejo 17.0.2, from its swagger and binary):

- `GET /repos/{owner}/{repo}/commits/{ref}/statuses` lists every status posted for a commit, paged: a CI posts a new
  one each time a check moves on, with `id`, `context`, `status`, `description`, `target_url` and `created_at`.
- Forgejo Actions posts a status for a job when it is made (`Waiting to run`, or `Blocked by required conditions`),
  when it starts (`Has started running`) and when it ends (`Successful in …`, `Failing after …`, `Has been skipped`,
  `Has been cancelled`), and again when it is run again. These texts are not translated. Its link is
  `…/actions/runs/<run number>/jobs/<place of the job in its run>`, maybe with `/attempt/<n>`.
- Forgejo 17 has an Actions API: `GET /actions/runs?run_number=`, `GET /actions/runs/{id}/jobs`,
  `GET /actions/jobs/{id}/logs` (plain text) and `POST /actions/jobs/{id}/rerun` (204; reruns a done job and the jobs
  that need it). A job is named by its id there, which a status does not give. An older Forgejo answers 404.
- Nothing says how long a check usually takes.

## Decision

- **`C` lists the checks of a branch on Forgejo in the same popup as GitHub's**, on the same keys: on a local branch
  those of the branch it follows, on a remote branch its own, and from the pull request dialog those of the commit it
  merges, Escape going back to it. It applies only when the badge says the tip has statuses, as on GitHub.
- **The popup is drawn by a frame of the kernel, `ChecksFrame` (kernel-ui), which GitHub's popup now uses too**: the
  title's marks, reading, failure, the counts, span, bar and note lines, the rows that fit with the one picked marked,
  scrolling, closing, the hint and the keys of a failed build. With it move the bar (`ProgressBar`), how durations are
  written (`CheckDurations`), and how far the rows scroll to the one picked (`CheckList`, kernel-state, as updates may
  not depend on views). What each row says stays the forge's.
- **A check is a context, folded from all its statuses** (`ForgejoCheck`, core-forgejo): how it is doing is its last
  status; it is timed from its current attempt, which starts after the last status that ended an earlier one; a job of
  Actions started when it said it started running, and is queued while it waits. Not the combined status, which keeps
  only the last status of each context, so no check can be timed.
- **Without times learned, the bar counts the checks done**, dimmed, as GitHub's does when it knows no times; rows say
  how long a check took, has run or has been queued, without what is left.
- **`e` and `r` find a job's id from its link**: the run by its number, then the job at its place among the jobs of the
  run, by id. A log is kept in the cache, under `logs/forgejo/<host>/<owner>/<name>/`, and opens at the first line that
  says a step failed (`❌`), else at its end. `r` reruns each failed job of each run whose jobs are all done; a status
  of another CI has no log and is not rerun, and says so.
- **What failed is a `FailedBuild`** whose logs are where each status links to, as Forgejo's API reads a log only by an
  id the link does not show.

## Consequences

- Both forges list their checks the same way, from one place; a change to the popup's frame changes both.
- On a Forgejo older than 17, the checks are listed, but `e` and `r` fail with Forgejo's 404.
- A job is found by its place in its run, which Forgejo derives from the order of the jobs' ids; were that to change,
  `e` would open another job's log.
- Each read pages through every status of the commit, more than the combined status costs.
