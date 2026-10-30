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

## Decision

1. **The branches, their parents and their distances are read by one load, into one message.** `LoadBranches` reads
   the branches, then the parents, then counts the distances (ADR 46) against the tips and the main branch of the
   branches it just read, and comes back with all three in `BranchesLoaded`. `LoadParents` and `LoadBaseDistance` are
   gone, and with them the stamp of the config and the check that dropped an outdated count. When the parents cannot
   be read, the branches come back without them, and the parents and distances read before stay, as a failed count
   did. The parents are read with every read of the branches again: a commit takes 7 git processes, not 6, but no
   two loads race over what the distances go by. The config stays in the stamp of the branches, so a parent jazygit
   records reads the branches again.
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
   focus, or a tmux without `focus-events`, never says the app is away, and nothing changes there.
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

The other chains of loads stay as they are: the commits ahead of HEAD go by the HEAD of the status and the bases of
the branches, read by loads of their own, and the log of the highlighted branch, the divergence and the operations
run when the user asks. Those keep the checks of ADR 49.

## Consequences

- `BranchesLoaded` holds the parents and the distances, when they could be read. `AllBranches` tells the tips and
  the main branch the distances go by.
- The branches show only once their distances are counted, one `for-each-ref` later than before.

Amends ADR 56 (`0056-fewer-git-processes-after-a-commit.md`).
