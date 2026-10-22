# 17. Fetch from the remotes at startup

Date: 2026-10-22

## Context

The remotes were fetched from on a timer that repeats every minute, and whose first tick comes a minute after the
app starts. Until then, the branches showed how they compared with the remotes as of the last fetch made outside
the app, however old that was.

## Decision

- `JazygitApp.startRuntime` sends a `FetchMsg.Tick` once the program has started, next to the other messages
  it sends then. `Update.init` cannot start it: the state does not know the features
  (`ArchitectureTest.stateDoesNotKnowFeaturesOrViews`). Every start of a session does: starting the app, switching
  to another repository, and reopening it after the GitHub account or the settings changed. A repository switched
  to may not have been fetched from in a while; the reopen fetches once more than it needs to, which keeps it to
  one rule.
- The fetch does not wait for the first loads. It runs on the background pool, not on the thread that reads the
  repository, so it does not slow down showing it; once it is done, the repository is read again, as after each
  fetch. Offline, the status line says the fetch failed right away instead of after a minute.
- The timer stays as it is: every minute, counted from the start of the app. A tick right after a fetch fetches
  again, unless that fetch is still running. Skipping it would take keeping when the last fetch was tried, which
  is not worth the occasional extra fetch.
- The timer of the pull request overview stays as it is too. It only reads while that screen is shown, which it
  is not at startup, and the pull requests the branches show are read after each fetch, so also after this one. They are
  still read at the start as well, as before: a fetch can take a while, or fail offline.

## Consequences

- Each start of a session takes a `git remote` and a `git fetch` per remote, and a read of the pull requests
  once they are done.
- The ahead and behind counts of the branches can change shortly after the app shows them, once the fetch is in.
