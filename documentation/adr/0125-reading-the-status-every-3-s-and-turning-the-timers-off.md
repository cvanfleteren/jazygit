# 125. Reading the status every 3 s, and turning the timers' reads off

Date: 2026-11-14

## Context

While the terminal has the focus, jazygit reads the repository again every second (`Timers.REFRESH_INTERVAL`): each
`TimerMsg.Tick` runs `LoadStatus` and `CheckMetadata` (`Update.tick`), and which worktrees have changes at most every
3 s (`Update.WORKTREE_CHANGES_INTERVAL`). While away it does so every 15 s (ADR 67), and a tick kept while away is
handled once the focus comes back (ADR 58, ADR 67). The remotes are fetched from at the start of each session (ADR 17)
and every minute (5 minutes while away).

Measured idle for 60 s on a repository with a diff, a Markdown and a JSON file changed, with a frame only when a time
shown changes (ADR 124): `git` takes 0.38 s of CPU a minute, the status read every second, and the JVM 1.3 to 2.0 s,
most of it starting those reads and handling what they return; 2.8 to 3.9 % of a core in all. The 1 s refresh is most
of what an idle jazygit costs.

lazygit reads the files every 10 s and fetches every 60 s by default, and lets both be turned off (`git.autoRefresh`,
`git.autoFetch`). Turning the fetch off is what is asked for most: an SSH agent that asks to approve each use of a key
(the 1Password agent, Secretive, a FIDO key that wants a touch) asks every minute, `-o BatchMode=yes` does not stop
it, as the agent asks and not ssh. Others fetch already (an IDE), the remote is heavy or the connection metered, or
the remote-tracking branches are to stay where they are while reviewing. All of these hold for a machine or a remote,
not for an hour. Being offline needs nothing: a fetch then fails at once, and only the line at the bottom says so.

jazygit has no fetch the user starts: only the timer, the start of a session, and `FetchUpdate.requested` after
creating, merging or ending a pull request fetch.

## Decision

1. **The status is read every 3 s while the terminal has the focus**, instead of every second.
   `Timers.REFRESH_INTERVAL` defaults to 3000 ms; `-Djazygit.refreshInterval` stays, for the acceptance tests. While
   away it stays every 15 s (ADR 67): that is what it costs, not a ratio to the focused interval.
2. **The intervals are not settings.** They stay constants in `Timers`; turning the reads off is the setting.
3. **Two settings, `git.auto-refresh` and `git.auto-fetch`, turn the timers' reads off.** Booleans, on by default,
   in `Setting.ALL` through their lens, and in the settings file only when off. They are global, as the other
   settings; not per repository, and no mode that lasts a session: the code would be the same, a boolean in the model.
   Each has a row in the settings panel that turns it on or off and keeps that, as the animations do (ADR 81).
4. **`Update` decides, not `Timers`.** The timers keep running; `Update.handle`, where each message from outside
   arrives and where the away check of ADR 67 already is, does not pass on what the timer sends while its setting is
   off. A setting changed in the panel or in the file (ADR 111) applies at the next message, without scheduling
   anything again, and a test needs no clock. Turning one back on reads or fetches at the next tick (at most 3 s, or a
   minute), not at once.
5. **With `git.auto-refresh` off, a tick the timer sends reads nothing while the terminal has the focus**: not the
   status, nor what `CheckMetadata` guards, nor which worktrees have changes, nor what failed before. While away,
   `Update.handle` keeps it in `Jobs.missed` (`keptWhileAway`) without asking whether it is due, so it is never handled
   on its 15 s. When the focus comes back, `Update.focused` hands each kept message to its `apply` directly instead of
   to `Update.handle` again: the away check is moot then, as the app is back, and so is the switch, which is about
   what the timer sends, not about a read that was put off. So the kept tick reads the status once then. The
   repository is still read at the start of a session and after each of jazygit's own commands (`Update.refresh`,
   which also tries again what failed).
6. **With `git.auto-fetch` off, `Update.handle` drops `FetchMsg.Tick`**, focused or away, so it is not kept either:
   neither the one of the timer, nor the one sent at the start of a session (ADR 17), fetches. A fetch the user asks
   for still runs, as do those `FetchUpdate.requested` starts after the user's own pull request actions. GitHub is
   read as before: the overview of the pull requests and the pull requests of the branches read at the start are not
   fetches. Turning it off does not stop a fetch that runs, nor the fetch it was asked to run again once done.
7. **A key fetches from the remotes**: `f` in the files and the branches panels, as lazygit, whatever
   `git.auto-fetch` says. It goes through `FetchUpdate.requested`: it fetches now, or once the fetch that runs is done.
   The line at the bottom says how it went, as for the fetches of the timer. `f` is the fixup of the commits panel,
   which stays.
8. **The line at the bottom stays as it is**: with `git.auto-fetch` off it shows how long ago the remotes were last
   fetched from, which only grows, or nothing when they were not.

## Consequences

- An idle, focused jazygit reads the status 20 times a minute instead of 60. A change made outside it (an editor in a
  split saving a file) shows up to 3 s later; one made while away still shows as the focus comes back.
- Which worktrees have changes is read with every tick, and after each of jazygit's own commands:
  `Update.WORKTREE_CHANGES_INTERVAL` and its guard go. That guard was measured from when the last read finished, so
  a 3 s guard would skip every other 3 s tick (the read came every 4 s with a 1 s tick, not every 3 s). A read of
  them that still runs is not started again, as for the other loads.
- The backoff of a read that failed (ADR 31) counts ticks, and so waits 3 times longer: at most 64 ticks, some 192 s
  instead of 64 s. That is meant: a read that keeps failing, like the preview of a commit that is gone, has no need
  to be tried every minute, and one of jazygit's own commands tries it again at once anyway. `Jobs.MAX_BACKOFF_SHIFT`
  stays 6; its Javadoc says 3 minutes.
- Tests: with auto-refresh off, a focused tick asks for no command; losing the focus, a tick, and the focus coming
  back asks for a `LoadStatus`. With auto-fetch off, a `FetchMsg.Tick` asks for nothing, focused or away.
- With `git.auto-fetch` off, the branches show how they compare with the remotes, and their pull requests, as of the
  last fetch: one the user asked for, or the one after their own pull request actions.
- A terminal that does not report the focus never reads on a timer with `git.auto-refresh` off.
- Acceptance tests (`acceptance/test_fetch.py`) turn `git.auto-fetch` off in the settings file, so that the fetch at
  the start does not race with the one they ask for: `f` in the files panel and in the branches panel shows a commit
  pushed to the remote as behind, and a fetch that fails says so in the line at the bottom.

Amends ADR 17 (`0017-fetch-at-startup.md`) and ADR 67
(`0067-reading-less-rather-than-nothing-in-the-background.md`).
