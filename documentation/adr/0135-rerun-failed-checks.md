# 135. Running failed checks again

Date: 2026-11-15

## Context

The checks popup (`C`, ADR 32) lists the checks on the tip of a branch, local or remote, or of the pull request the
pull request dialog shows. When a check failed because the build flaked, the user has to go to GitHub to run it again.
ADR 131 left running a build again for later, as its builds have no cursor to pick one with; neither has the checks
popup, which only scrolls.

GitHub runs the failed jobs of a workflow run again, and the jobs that need them, with
`POST repos/{owner}/{repo}/actions/runs/{run_id}/rerun-failed-jobs`; the whole run with `/rerun`; a job with
`actions/jobs/{job_id}/rerun`. GraphQL has none of these for GitHub Actions. It only does so for a run that is
completed, and for a user who may write to the repository. A run run again keeps its id, with an attempt more; the
checks of the tip then show the jobs of that attempt.

The checks popup knows of each check run of a workflow which run it is of (ADR 32). A check of another app, and a
commit status, are of no workflow run: GitHub Actions cannot run them again.

## Decision

- `r` in the checks popup runs the failed jobs of every failed workflow run on the tip again: one
  `rerun-failed-jobs` a run. Not: whole runs, which run what passed too; nor one check picked, which needs a cursor
  the popup does not have. The popup's hint lists it, next to `R`, which reads the checks again.
- A run failed when one of its checks did: failed, timed out, could not start, needs an action, or was cancelled,
  as the popup counts them failing. A run cancelled as a newer push superseded it is one to want back.
- It asks first, in a popup over the checks, naming each run it runs again, its workflow and event, and how many of
  its jobs failed; and what it does not run again, and why: a run with a failed job that still runs, as GitHub does
  not run it again before it is done; a failed check that is not of GitHub Actions. Enter runs them again, Escape
  does not; both go back to the checks. Running a build again is seen by others on GitHub, as an attempt more, and
  costs minutes of CI.
- With nothing to run again, `r` stays listed, and says in the checks popup why nothing is: none failed, those that
  failed still run, or they are not of GitHub Actions. Not: leaving it out of the hint.
- The runs are run again one after the other, in the background. One that GitHub refuses does not stop the others,
  and those run again stay so. The checks popup says how it went, `rerun 2 of 3 · E2E: <what GitHub said>`, as an
  error when one was refused. No call asks first whether the user may write to the repository: GitHub refusing says
  so, and asking would hold up the popup.
- Once asked, the checks are read again at once, as `R` does, and then by themselves as while a check runs, every
  10 s, until a job of a run run again shows not done, or for a minute: GitHub takes a few seconds before it shows
  one queued. Meanwhile the title says `rerun asked 14:02:10`. The branches catch up as they do when the popup reads
  checks that do otherwise than they show.
- Whether it was opened from a local branch, a remote one, or the pull request dialog, the popup runs checks again
  the same way.
- Once the checks popup is closed, a run GitHub refused still shows as an error; all of them run again shows
  nothing. The command log lists each call, as for any operation that changes GitHub.
- Not yet: running a single job or check again, with debug logging, or from the builds of a branch (ADR 131).

## Consequences

- jazygit changes something on GitHub from the checks popup: CI runs again, on the user's account.
- A failed run that has not ended needs `r` again once it has.
- The builds of a branch still cannot run a build again; picking a build there is left to a later ADR.
