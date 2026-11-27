# 210. The forges reach the branches pane and the fetch through the kernel

Date: 2026-11-27

## Context

The forges reached into `features-branches` and `features-changes` for four things (#704): the slots of the branches
pane, the create flow of a pull request, checkout and worktrees (GitHub only), and a fetch after a merge or a create.
Two of them are small enough to move to the kernel.

- **The slots.** ADR 172 has a pane declare its slots beside itself, so `BranchBadges` and `BranchGroups` live with the
  branches pane in `features-branches.panes.branches`. Both forges fill them, so both import the pane's package and
  depend on the whole module for two records that only use kernel types.
- **The fetch.** After a merge, or once a pull request is created, the forge's update calls
  `FetchUpdate.requested(next)` itself, so it imports `changes.fetch`. ADR 206 solved the same problem for opening a
  file: the feature that asks raises a `KernelEvent`, and the feature that does the work answers it.

## Decision

- **A slot that features of several groups fill is declared in the lowest module that the pane and all its fillers
  see**, as a message key several modules use is. `BranchBadges` and `BranchGroups`, with `Badge`, `Shown`,
  `Grouping`, `Group`, `Entry` and `Keys`, move to `kernel-ui`, in `kernel.ui` beside `Slot` and `KernelSlots`: its
  subpackages may not use the shell, where `Slot` and `Fills` are. The pane stays in `features-branches` and reads them
  as it did. A slot only its own group fills, as `CommitsPane.LINE`, stays beside its pane, as ADR 172 has it. The
  names of the slots do not change.
- **`KernelEvent.RemoteChanged`**: a feature that changed what a remote has without fetching it, as merging or creating
  a pull request on a forge does, raises it with `Events.happened`. `changes.fetch` answers it with `reacting`
  (`FetchUpdate.reacting`) as `FetchUpdate.requested` did: it fetches now, or once the running fetch is done. The event
  says what happened, not what to do, as ADR 158 asks of an event.
- The forges keep what they did around it: GitHub still reads the pull requests "anyway" (`PullRequestReads.loadAnyway`)
  before the fetch, and the reaction's commands come after the update's, so the order of the commands is the same.

## Consequences

- The forges import nothing from `branches.panes.branches` or `changes.fetch`.
- The poms do not change yet. The create flow (`branches.branch.pullrequest`) is still `features-branches`', and both
  forges use it; ADR 209 lists it as the next port of `features-forge`. GitHub also still uses checkout, worktrees, the
  base of a branch and the files of a commit.
- A test of a forge's update sees the event through `HeardEvents.hearing` (kernel-state's tests), which answers each
  event with a `Heard` command, so it needs no fetch. `FetchUpdateTest` and the app's `RemoteChangedTest` check that the
  event fetches.
- `kernel-ui` knows that the branches pane has badges and groups, as it did before ADR 172, but not the pane itself.

Amends ADR 172.
