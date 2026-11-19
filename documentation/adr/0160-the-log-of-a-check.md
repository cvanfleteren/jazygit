# 160. The log of a check

Date: 2026-11-19

## Context

The checks popup (`C`, ADR 32) lists the checks on the tip of a branch, and runs the failed ones again (ADR 135). Why
one failed is in its log, which the user has to go to GitHub for. `wanted.md` lists it: CI check details and logs.

The popup has nothing picked: `j` and `k` scroll it (ADR 135 left running one check again for want of a cursor). The
builds of a branch (ADR 133) pick a build without a cursor either: `n` and `N` step the build picked, marked `▸` with
its title in bold, while `j` and `k` keep scrolling, and a click on a row picks it.

A check run of GitHub Actions is a job: its id is the job's. GitHub gives the log of a job over REST,
`GET repos/{owner}/{repo}/actions/jobs/{job_id}/logs`, as plain text, each line after a timestamp, with markers like
`##[group]` and `##[error]` and the colours of the tools as ANSI escapes; often some MB. It has one only once the job
is done, keeps it for 90 days by default, and a job's log does not change once it is done: a run run again is a new
attempt, of new jobs. A queued workflow run has no job yet (ADR 35); a check of another app, or a commit status, has
no log on GitHub.

Opening a file in an editor of the user's is there (ADR 145): git's editor, the one editor set to open files, or a menu
of those.

## Decision

### Picking a check

- One check of the popup is picked, marked `▸` with its name in bold. `n` picks the next one, `N` the one before,
  in the order the popup lists them; a click on a row picks that one. `j`, `k`, the arrows and the page keys keep
  scrolling. Not: a cursor that `j` and `k` move, which the builds do not have either, nor a menu of the checks to
  pick from, as `p` is for the builds: the checks are few and already in a popup.
- At first the first one listed is picked: the first failed one, as failed ones are listed first, else the first.
- The popup scrolls as little as it takes to show the one `n` or `N` picks.
- It stays picked while the checks are read again, by its job, or by its name for one without. Once it is no longer
  among them, as after a run is run again, whose jobs are new, the first one listed is picked again.

### Opening its log

- `e` opens the log of the check picked in an editor. The hint lists it, with `n/N`.
- The whole log of the job is read, `gh api --allow-escape-sequences repos/<owner>/<repo>/actions/jobs/<id>/logs`,
  as GitHub gives it, and kept in `<cache>/logs/<owner>/<repo>/<run>-<attempt>-<job>.log`. Not: only its failed
  steps (`gh run view --log-failed`), which drops what led up to the failure; nor the colours taken out.
- Since gh 2.104, gh refuses to print what has escape sequences in it, as the colours of a log are, without
  `--allow-escape-sequences`. An older gh does not know that flag: when it says so, the log is read again without
  it, as an older gh prints them anyway.
- `<cache>` is the cache directory of the OS: `$XDG_CACHE_HOME/jazygit`, else `~/.cache/jazygit`; on macOS
  `~/Library/Caches/jazygit`; on Windows `%LOCALAPPDATA%\jazygit\cache`; `$JAZYGIT_CACHE_DIR` on all of them.
- A log already kept is opened again without asking GitHub. Not yet: deleting old logs.
- It opens as a file does (ADR 145): in git's editor while no editor is set to open files; else in the one set, or in
  the one chosen from the menu, `Open <job>.log in`, over the checks popup, to which Escape goes back. It opens at the
  first line with `##[error]`, else at the last line.
- The popup stays open: a terminal editor hands the terminal back to it, another is left running.
- Reading the log is an operation, *Download log* with the name of the check, in the background: the command log has
  the gh command, and opening it the editor's, with the file. One GitHub refuses, as of a log it no longer keeps, is
  shown as a failed operation.

### When there is no log

`e` stays in the hint. On a check without a log it says why in the popup, in the line above the checks, as `r` does
with nothing to run again (ADR 135): it is queued or runs, as GitHub has its log once it is done; it was skipped; it is
not of GitHub Actions.

## Not yet

- Showing the log in jazygit, with its steps folded and its colours.
- The log of a build from the builds of a branch (ADR 131).
- Running one check again (ADR 135).

## Consequences

- Logs pile up in the cache directory until the user deletes them.
- A log of more than the 64 MB jazygit reads of a program's output is too much output.
- An editor shows the ANSI escapes of a log as they are, unless it renders them.
