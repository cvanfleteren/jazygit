# 12. A pool for the background commands

Date: 2026-10-21

## Context

Commands run on one of two threads. `git-io` runs the user's operations and the reads of the repository, one after
the other. `git-fetch` runs the commands that are `background()`: fetching, the pull requests and their checks,
the pull requests screen, the preview of a pull request, reading how gh is set up, and which worktrees have
changes (ADR 11). Those share one thread, so a preview the user waits for can wait behind a fetch from a slow
remote, or behind the previews of the pull requests the user moved past.

## Decision

### One pool

- The background commands run on a pool of 4 daemon threads, `git-background-N`, in place of `git-fetch`. That is
  enough for a fetch, the pull requests and a preview at once, and few enough that a burst does not start dozens
  of git or gh processes, nor eat into GitHub's rate limit. It is not a setting.
- What must not overlap is kept from it by the model: one fetch at a time, asking for each remote after the last
  (`Jobs.fetching`), and a load not started again while it runs (`reading`, `refreshing`).
- The pull requests and the checks of the branches were read again while a read ran, after a change on GitHub
  (a push, a pull request created, merged or closed), as the running read may have missed it. On one thread the
  later read finished last; on the pool the earlier one could, and replace what the later one read until the
  next refresh. Such a read now waits: it is kept in `GitHubState.again`, and starts once the running one is
  done (`GitHubReads`), as a fetch asked for during a fetch does. However often it is asked for meanwhile, it
  runs once.
- Switching to another repository stops the pool (`shutdownNow`), as it stopped `git-fetch`.
- `git-io` stays one thread. Writes take the index lock, and the model relies on its reads arriving in the order
  they were asked for. Reading the repository in parallel would need results to be ordered, and is left for when
  refreshing is shown to be slow.

### Only the latest preview

A command can take a slot (`Cmd.slot()`). When `Program` starts a command for a slot, it cancels the one it started
for that slot before, if that one has not started running: it is then skipped, and reports nothing. One that runs
already finishes. Only a command whose result the model drops once a newer one is asked for may take a slot.
`LoadPullRequestPreview` does: moving through the pull requests no longer runs gh for each one passed while the
pool is busy.

### Checking a gh executable

As checks can now finish in another order than they were asked for, the gh executable entered last is kept
(`Jobs.ghExecutableEntered`), and the outcome of a check of another one is dropped, whether it finishes before or
after the check of the last one. It is kept in the jobs rather than in the settings panel: entering a gh
executable replaces the panel, as one popup shows at a time, so no panel is open while it is checked. The outcome
of the last one is used as before, panel or not: it was the user's last choice.

## Consequences

- Up to 4 git or gh processes run in the background at once.
- `Program` keeps the last command of each slot; it is only touched on the UI thread.
