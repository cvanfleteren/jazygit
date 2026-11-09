# 93. The index written anew now and then

Date: 2026-11-09

## Context

Every tick, once a second while the terminal has the focus, reads `git status`. As every read (`GitCommand.reading`),
it runs with `--no-optional-locks`, so that it never holds the index while the user runs git elsewhere. That has a
cost that does not show at first: a status that may not lock the index may not write it either.

Before it can tell what changed, git refreshes the index: for each file, it compares what `stat` says now to what the
index holds. Where those differ, or where the file was written in the same second as the index (git calls that racily
clean), it reads the whole file and hashes it. A plain `git status` then writes what it found back into the index, so
the next one is quick again. With `--no-optional-locks` it never does, and every status after it hashes those files
again.

That happens more often than it seems: right after a clone, a checkout, a rebase or a reset, every file it wrote is
racily clean; a tool that rewrites files without changing them (a formatter, a build, an editor) leaves them stat
dirty. Whoever uses jazygit rather than `git status` in a shell may never write the index otherwise. On a clone of
jazygit itself, 1,245 files, a status took 75 ms instead of 6 ms, and the git processes of an idle app took 8 % of a
core instead of 1 %, for as long as the app ran.

## Decision

1. **Every 30th tick, `Update.tick` also asks for `Cmd.RefreshIndex`,** which runs
   `git update-index -q --unmerged --refresh`: it writes into the index what the files are like now, where it no
   longer says so, and leaves it alone otherwise. A file that changed stays changed, and one in conflict stays
   unmerged: `-q` and `--unmerged` keep git from failing on them, so a failure means the index was locked.
2. **Only while the terminal has the focus.** Then the user is not likely to be running git in another terminal, which
   could find the index locked for the moment the refresh holds it. While away, the ticks read the status every 15 s
   (ADR 67), and the index is left as it is.
3. **Not while an operation of the app runs, nor while the last refresh still does.** An operation writes the index
   itself, and the two would hold it in turn. The refresh is not a background command, so it never runs at the same
   time as another command of the app that is not one either.
4. **A refresh that could not lock the index changes nothing shown.** It reports `Msg.IndexRefreshed` either way, and
   only the debug log says it failed; the next one, 30 ticks later, tries again.

## Consequences

- After a checkout, or after something touched the files, the status is slow for 30 s at most, not for as long as the
  app runs.
- The index is locked for as long as a refresh takes, the time of a quick status, once every 30 s while the terminal
  has the focus. A git command run at that very moment elsewhere fails with `index.lock` exists, as it would next to a
  shell prompt that shows the status.
- The reads still take no optional locks: only `Cmd.RefreshIndex` takes the lock, and only when `Update` asks for it.
