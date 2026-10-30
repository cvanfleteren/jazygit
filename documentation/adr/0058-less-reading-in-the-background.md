# 58. Less reading in the background

Date: 2026-10-30

## Context

ADR 56 (`0056-fewer-git-processes-after-a-commit.md`) cut what a commit costs. Two things in it did not hold:

- Its decision 3 read the parents of the branches by a `LoadParents` of their own, guarded by a stamp of the config,
  and gave them to `LoadBaseDistance`. The branches and the parents then each asked for the distances, and came back
  in either order: at the start, one of the two counts was thrown away, and every count was checked against the load
  the model would make now.
- Its decision 2 stamped the worktrees by their HEAD files. A reftable repository leaves those as stubs git never
  rewrites, and keeps each HEAD in its tables: the main worktree's with the branches, a linked one's in tables of its
  own. A checkout there did not list the worktrees again.

And what runs while the user does nothing was never counted. Idle, jazygit ran about 80 git processes a minute, 60
of them the status of ADR 52, and 3 to 4 gh processes; several of them read what was known already:

- the diff of the highlighted file, read again after every status, one to three processes a second;
- the repository, read again after every fetch, also one that brought nothing; and, since git 2.48, the branches on
  the ticks after it, as the fetch locks the HEAD of the remote, which changes the directory the stamp looks at;
- all of it, also while the terminal does not have the focus and nobody looks;
- gh failing three times a minute, all day, in a repository without a remote on GitHub, or with gh logged out;
- the open pull requests of the repository, read twice a minute by two queries, one for the branches and one for
  the pull requests screen, even while it is hidden;
- the details of a pull request, read by two queries, one after the other, every 10s while a check runs.

## Decision

1. **The branches, their parents and their distances are read by one load, into one message.** `LoadBranches` reads
   the branches, then the parents, then counts the distances (ADR 46) against the tips and the main branch of the
   branches it just read, and comes back with all three in `BranchesLoaded`. `LoadParents` and `LoadBaseDistance` are
   gone, and with them the stamp of the config and the check that dropped an outdated count. When the parents cannot
   be read, the branches come back without them, and the parents and distances read before stay, as a failed count
   did. The parents are read with every read of the branches again: a commit takes 7 git processes, not 6, but no
   two loads race over what the distances go by. The config stays in the stamp of the branches, so a parent jazygit
   records reads the branches again. The other chains of loads stay as they are: the commits ahead of HEAD go by the
   HEAD of the status and the bases of the branches, read by loads of their own, and the log of the highlighted
   branch, the divergence and the operations run when the user asks. Those keep the checks of ADR 49.
2. **A reftable repository stamps its tables for the worktrees.** The stamp of the worktrees holds the tables of the
   repository and those of each linked worktree; the stamp of the branches holds those of each linked worktree too.
   There a commit, which writes the tables of the branches, lists the worktrees again.
3. **The highlighted diff is read again once its files changed, not with every status.** A file can change without
   its status changing, so the diff was read again after every status: one to three git processes a second for as
   long as a changed file was highlighted. `LoadFileDiff` now stamps what it reads from before it reads: the files on
   both sides of each entry, and the index. After a status, `CheckFileDiff` stamps them again, without git, and the
   diff is read again only when the two differ, or when the stamp tells nothing, as within 2s of a change (ADR 15).
   A status that says HEAD moved reads the diff at once, as the staged changes are compared with HEAD, which neither
   stamp holds.
4. **The timers read nothing while the terminal does not have the focus.** On its alternate screen, the app asks the
   terminal to report the focus (`ESC [?1004h`). TamboUI reads the reports, `ESC [ I` and `ESC [ O`, as one unknown
   key, so `HandOverBackend`, which every key is read through, takes them out before it does and tells the app,
   which dispatches `Msg.TerminalFocused`. While away, the messages of the timers that read the repository, the
   remotes or GitHub (`Msg.Periodic`: the tick, the fetch, the pull requests overview and the dialogs of checks) are
   kept instead of handled; once back, each is handled once, so what they would have read is read at once. A merge
   that is being checked on still is, and what the user does is handled as ever. A terminal that does not report the
   focus, or a tmux without `focus-events`, never says the app is away, and nothing changes there. A key, a click or
   a paste only comes from a terminal that has the focus, so one read while away also tells it is back: a report that
   got lost, split up over a slow link or never sent after a tmux detach, does not leave the app paused for good.
5. **A read from GitHub that keeps failing waits longer the more it failed.** A repository without a remote on
   GitHub, or gh missing, logged out or offline, made each timer run gh for nothing: 3 failing processes a minute,
   all day. `GitHubState.failing` keeps, per read, how often it failed in a row and when last (`FailingRead`); the
   timers (the read of the pull requests and the checks after each fetch, and the tick of the pull requests
   overview) start a read that failed once at the next tick, one that failed twice after 2 minutes, three times
   after 5, and more after 10. A read that succeeds is no longer failing. What the user does, and what an operation
   on GitHub asks for, reads at once, as before.
6. **A fetch that changed nothing reads nothing of the repository again.** The fetch runs with `--porcelain`, which
   prints a line per remote-tracking ref it updated, created or pruned, and nothing when there was none (not with
   `--quiet`, which silences it too; git 2.41, below the 2.45 jazygit needs). Once all remotes are fetched from, the
   repository is read again only when one of them changed a ref (`Jobs.fetchChanged`); GitHub is read either way. And
   since git 2.48, every fetch takes the lock of `refs/remotes/<remote>/HEAD` and lets it go, which changed the
   directory the stamp of the branches looked at: the branches were read again on the ticks after every fetch, three
   processes each. The stamp now holds the ref files under `refs/remotes`, but the locks, rather than its
   directories. Turning `followRemoteHEAD` off instead would stop the fetch from setting the remote's HEAD, by which
   the main branch is told (ADR 46).
7. **The details of a pull request tell how far behind its base it is in the same query.** ADR 42 asked it in a second
   query, as it needs the commit of the head, which the first one tells. GitHub keeps `refs/pull/<number>/head` at the
   head of every pull request, in the repository of its base, also for one from a fork; the number is known before,
   so the details ask `baseRef { compare(headRef: "refs/pull/<number>/head") { behindBy } }` along. The dialog of a
   pull request reads its details every 10s while a check runs, so that is half the processes, 6 a minute instead of
   12. It counts only while the pull request is open, at a head that is known, as before, and reads leniently: what
   cannot be told is none, and the rest of the details read.
8. **The open pull requests are read once a minute, by one query, for the branches and the pull requests screen.**
   The branches read them after every fetch (`PullRequestsQuery`, all pages, the state of up to 100 checks each), and
   the screen every minute, also while hidden, for its new ones (ADR 29: `RepositoryPullRequestsQuery`, the first 100,
   the state of the rollup): the same pull requests, twice a minute. Now the screen's query reads all pages, and the
   branches take those of its pull requests that are not from a fork (`RepositoryPullRequests.ofBranches`).
   `LoadPullRequests` and `PullRequestsQuery` are gone: after a fetch, a push or a change on GitHub,
   `LoadRepositoryPullRequests` runs, and what it reads updates the screen and the branches together. A tick of the
   screen reads only the pull requests of the user, while it is shown. The checks of a pull request are the state of
   their rollup, as for the branches and the screen already, rather than the states of the first 100 checks combined:
   the rollup counts them all. The checks of the branches stay a query of their own, as `gh --paginate` follows the
   pages of one list only.

### What runs in the background, idle

| | before | then |
|---|---|---|
| git, per minute | ~80: status 60, fetch 4, reads after it 7, after its lock 6–9 | ~65: status 60, fetch 4 |
| git, a changed file highlighted | 60 to 180 more | a read per change |
| gh, per minute | 3–4 | 2–3: the pull requests, the checks of the branches, those still pending |
| gh, without a remote on GitHub | 3 a minute, failing | each read once every 10 minutes |
| the terminal without the focus | as with it | nothing, until it has the focus again |

## Consequences

- `BranchesLoaded` holds the parents and the distances, when they could be read. `AllBranches` tells the tips and
  the main branch the distances go by.
- The branches show only once their distances are counted, one `for-each-ref` later than before.
- `Jobs.readAt` holds the stamp of the highlighted diff too, next to those `CheckMetadata` takes.
- A message a timer sends implements `Msg.Periodic`; `Update.update` keeps it while the terminal is away. A new timer
  that reads something is to implement it too.
- The pull requests screen and the branches show the same read of the open pull requests, so a pull request shows
  the same checks in both. A repository with many open pull requests reads all their pages every minute, as the
  branches did already.
- What is shown of a repository can be up to a fetch older when the terminal was away: it is read once the focus is
  back.

Amends ADR 56 (`0056-fewer-git-processes-after-a-commit.md`), ADR 29 (the pull requests screen no longer reads its
new pull requests while hidden: the read after each fetch does) and ADR 42 (how far a pull request is behind its
base is asked with its details).
