# 27. Guard the remote refs

Date: 2026-10-23

## Context

Pulling `main` sometimes failed with `fatal: Cannot rebase onto multiple branches.`, while the branches pane showed
the commits to pull. The background fetch (ADR 12) ran at the same time as the pull, which fetches too:

- Every `git fetch` empties `.git/FETCH_HEAD` when it starts, and appends what it fetched when it is done. Two at
  once each empty it, and both append: for the checked out branch, both append a line that is to be merged.
- `git pull` reads `FETCH_HEAD` after its fetch. When the other fetch appended to it in between, there are two
  lines to merge, and a rebase refuses them. With plain git, a fetch started with a pull made the pull fail so a
  few times in a hundred.
- The two fetches can also both update `refs/remotes/origin/main`, and one of them then fails with `cannot lock
  ref`.

ADR 12 kept what must not overlap apart in the model: one fetch at a time, and a load not started again while it
runs. The commands of `git-io` and the fetch could overlap, as git locks what it writes; but `FETCH_HEAD` has no
lock, and a ref that is locked makes the other command fail instead of waiting.

## Decision

### The background fetch writes no FETCH_HEAD

`git fetch --quiet --prune --no-write-fetch-head <remote>`: nothing reads what the background fetch would write
there. A pull reads only what its own fetch wrote.

### A guard on the commands that write the remote refs

- A command can name a guard (`Cmd.guard()`), what it must not share with another command at the same time.
  `Program` holds one lock per guard, and takes it on the command's own thread around performing it: a command
  whose guard is held waits for it, on its thread.
- There is one guard, `Guard.REMOTE_REFS`: the remote-tracking refs and `FETCH_HEAD`. The commands that write them
  take it:
  - the fetch from a remote;
  - pulling, and fast-forwarding a branch that is not checked out;
  - pushing, also before creating a pull request;
  - deleting a branch on its remote;
  - checking out a pull request through gh, in place or in a new worktree.
- The guard is declared by the command, as data, as its slot is (ADR 12); `Program` does the waiting. The model
  knows nothing of it: a pull asked for while a fetch runs shows its spinner as soon as it is asked for, and starts
  once the fetch is done.
- A command waiting for the guard has left the queue of its executor, so stopping the program, when another
  repository is shown or the same one is opened again, does not drop it with what is queued. `Program.stop()`
  does: a command that gets its guard after the program stopped is skipped, and reports nothing. The locks are the
  program's own, not shared by the programs of the same repository.
- A command waiting for the guard on `git-io` holds up the commands after it there. They keep their order, which
  the model relies on (ADR 12). The commands that take the guard talk to the remote anyway, so they are already
  slow.

### Not: the fetch on `git-io`

Running the fetch on `git-io`, after the commands the user asked for, would keep it from overlapping anything. But
a fetch cannot be stopped once it runs, and on a network that hangs it takes up to its timeout of two minutes:
staging, committing and reading the repository would all wait for it. With the guard only the commands that talk
to a remote wait for a fetch.

## Consequences

- A pull can no longer fail because of a fetch in the background, nor can a push or a fetch fail on the lock of a
  remote-tracking ref that the other one is updating.
- A pull, push or other command that takes the guard can wait for a fetch from a slow remote, and so do the
  commands asked for after it.
- A command that writes the remote-tracking refs must take the guard; one that does not can fail again as the pull
  did.
- A pull or push that already runs when the repository is opened again still finishes, as it did before, and can
  overlap the fetch the new program starts with: one of them can then fail on the lock of a ref. Locks shared by
  the programs of a repository would keep that apart too, as state outside any program; that is left until it
  shows up.
