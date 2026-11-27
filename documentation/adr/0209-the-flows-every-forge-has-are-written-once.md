# 209. The flows every forge has are written once, in features-forge, and each forge gives a port

Date: 2026-11-27

## Context

ADR 195 gave Forgejo the checks popup GitHub has (ADR 32, 34, 135, 160), and shared only its frame
(`kernel.ui.checks.ChecksFrame`). The flow around it was copied file by file: the messages, the update, the popup,
the plan of what `r` runs again, its popup, dialog and note, the keys, and why a check has no log. Of a pair, 50 to
95% of the lines were the same, and every change to the flow, as filtering or watching the checks, would have been
made twice (#703).

What does differ between the forges is small, and stays theirs:

- GitHub reads the checks of a branch; Forgejo the statuses of a commit, the tip of the branch or the commit a pull
  request merges.
- GitHub learns how long checks take, orders them by it, and keeps a bar of how far they are from going back (ADR 32,
  89); Forgejo counts those done.
- GitHub runs the failed jobs of a workflow run again with one call; Forgejo a job at a time.
- GitHub runs gh, whose commands are logged; Forgejo asks its HTTP API.
- After a read, GitHub learns from the checks and catches the branch's badge up; after a rerun, GitHub reads the
  checks of the branches again, Forgejo the builds.
- Each has its own pull request dialog (ADR 175), which the checks go back to.

## Decision

- **A module of the flows every forge has, `features-forge`** (`net.vanfleteren.jazygit.features.forge`). It
  depends on the kernel only, and names no forge: `AppRules.theForgeFlowsNameNoForge` keeps it from `core.gh`,
  `features.github`, `core.forgejo` and `features.forgejo`, as `theKernelNamesNoForge` keeps the kernel. Both forges
  depend on it, so either can still be deleted (ADR 171). It is no feature: it has no `XxxFeature`, and each forge
  lists its keys, popups and timers in its own.
- **The checks flow is written once there**, in `forge.checks`: `ChecksMsg`, `ChecksUpdate`, `ChecksPopup`,
  `RerunPlan`, `RerunNote`, `RerunPopup`, `RerunDialog`, `CheckPick`, `NoLog`, `Reading`, and the keys,
  `ChecksAction`. What the dialogs of both forges say alike around the checks, as the title and the notes, is
  `forge.widgets.ChecksTexts`. The texts are `forge.*`, and name the forge as an argument where they do.
- **Over forge-neutral values**: a `ForgeCheck` is a check's name, how it went, the job of the forge's Actions it is,
  if it is one, and its `Source`, the check as the forge read it, which only that forge reads back, as `FailedBuild`
  carries the forge's own logs as text it wrote. `ChecksRead` is what a read brought: the repository, the commit
  when the forge read the checks by commit, and the checks. A `RerunPlan.Run` lists its failed checks, so GitHub runs
  the run again by its number and Forgejo each of its jobs.
- **Each forge gives a port, `ChecksPort`, as an enum of one** (`GitHubChecksPort.PORT`, `ForgejoChecksPort.PORT`):
  where the checks of a branch or of the open pull request are, the commands that read them, read a log and run
  failed jobs again, the order the checks are listed in, how far they were shown to be (`ChecksPopup.Reached`, which
  only GitHub keeps), and what it does after a read and after a rerun. The port travels in the messages that start
  the flow, in `ChecksMsg.Read`, `Rerun` and `Tick`, and in the popup, so the update reaches the forge without a
  registry. Not a `Slot` (ADR 202): what features give for a slot is read by views through `Fills`, which an update
  does not see.
- **The key `C` goes to the forge that is on**: both bind it, each sending `ChecksMsg.Requested` with its port, and
  `FeatureMsg.FirstOn` runs the one that is on (ADR 175). Each forge sends its own `Tick`, and only the popup of that
  port reads its checks again on it.
- **The dialogs that list the checks stay each forge's**: GitHub's draws a bar and how long is still to go for each
  check, which only it knows; Forgejo's how long each runs. Each draws the open `ChecksPopup` of its own port, in a
  scope of its own, so the help lists only the keys of the forge that is on. The dialog that asks to run failed jobs
  again is the same for both, and each lists it.
- **How long checks take stays GitHub's**: `CheckTimes`, `ChecksReached` and the learning of them are not shared.

## Consequences

- A change to the checks flow, as a new key, is made once, in `features-forge`, and tested there with a port of the
  tests; each forge tests its port and its dialog.
- A third forge gets the checks popup by giving a port, its dialog of the checks, and its commands.
- The checks packages of the forges keep their port, their commands, their dialog and the keys of `C`. Their messages,
  popups and update are gone, as are the `github.*` and `forgejo.*` texts of the flow.
- A forge whose checks have no commit leaves `ChecksRead.commit` empty; Forgejo, which reads by commit, fails a read
  without one with `ForgejoFailure.NoCommit`, which a popup it opened never meets.
- The create flow (`branches.branch.pullrequest`), merging and showing a pull request are next, as further ports in
  `features-forge` (#703).
