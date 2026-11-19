# 161. A failed build handed to an agent

Date: 2026-11-19

## Context

When a build fails, the user reads its logs, finds the cause and fixes it. A coding agent, such as Claude Code, can do
much of that on its own once told which build failed and how to read its logs. The user wants an action on a failed
build that starts one with a prompt to investigate, set up by the user, and off until then.

Two things jazygit lacks for it:

- **An action on the content of another group.** The builds of a branch (ADR 131) and the checks popup (ADR 32) are
  `features-github`'s, and so are their keys (`BuildsAction.SCOPE`). A group uses only the groups before it
  (ADR 159), so an action of another group of features cannot reach them. ADR 158 foresaw an extension point that a
  pane declares for another group to draw in, as GitHub's badges on the branches; none exists yet.
- **A way to run an agent.** The editors (ADR 110, 145) are programs the user lists in `config.toml`, run without a
  shell, either handed the terminal or started and left running. An agent is run the same way.

jazygit should come to work with other forges than GitHub (#588): what the agent is told of a build must not tie it to
GitHub.

Neither view picks a job: the builds show one build, picked with `n` and `N` (ADR 133); the checks popup lists the
checks on the tip of a branch, which can come from several workflow runs, and only scrolls.

## Decision

### A failed build, and actions on it

- A `FailedBuild`, in `kernel-state`, is what failed on one commit of a branch: the repository, the branch, the
  commit, the number of its pull request if it has one, and its failed jobs, each with its name, the run it is part of
  and its URL. It also carries how to read their logs, a text the forge side writes, as
  `gh run view <run> --log-failed --repo <owner/name>` for each run on GitHub, so that what reads it knows no forge's
  program.
- It names no forge: another forge (#588) gives the same value.
- **Actions on a subject** are an extension point: a typed key, as a `Part` is (ADR 157), for actions on a value of
  one type, here a `FailedBuild`. A `Feature` lists the actions it contributes for each key, and the app gathers them
  and hands them to the views that show that subject, as ADR 158 foresaw for the badges. An action has a label, a key
  and its help text, whether it is enabled or why not, and what it does with the subject: a `Cmd`.
- The views show a contributed action as one of their own, in their keys, their hints and the help. A keymap test
  fails when a contributed key is one the view already has, as a contributor cannot see the view's keys.
- `features-github` gives the subject: the builds give the failed jobs of the build shown; the checks popup the
  failed checks of the tip of the branch, across runs. The agent works out which of them matter. Not: a subject of
  one job, nor of the check picked once ADR 160 picks one, as more than one may have failed; nor of a whole branch
  with several failed builds.

### The agents

- A new group of features, `features-agent`, investigates a `FailedBuild` with an agent. It uses neither
  `features-github` nor `core-gh`, which a rule of `GroupsTest` checks.
- The user lists the agents in `config.toml`, as the editors:

  ```toml
  [[agents]]
  name = "Claude"
  command = ["claude", "{prompt}"]
  terminal = true
  ```

  - `name`, `command`, `terminal` and `shortcut` are as for an editor (ADR 110): the command is a list run without a
    shell, `terminal = true` hands the terminal over to the agent, else it is started and left running, as an agent
    in a tmux window of its own is.
  - The command takes `{prompt}` and `{dir}`; without `{prompt}`, the prompt is added as the last argument.
  - `prompt`: what the agent is asked, if not jazygit's own text. It takes `{repo}`, `{branch}`, `{commit}`, `{pr}`
    (empty without a pull request), `{failures}` (the failed jobs with their URLs, one per line) and `{logs}` (how to
    read their logs).
  - An entry that is wrong, as one with a placeholder it does not know, is left out and reported as
    `SettingsProblem.Invalid` under its keys, as an editor is.
- Not: an agent jazygit knows of, as Claude with its flags. jazygit names no agent; Claude is the example in the
  documentation.
- The editors and the agents share one type of program in `kernel-settings`: its name, its command, `terminal`, its
  shortcut, how its placeholders are filled in and how a wrong entry is reported. Each list adds only its own
  placeholders.
- Only in `config.toml` for now: a section of the settings panel that a feature contributes is for a later ADR.

### Investigating

- `I` investigates the failed build in the builds of a branch and in the checks popup. With one agent it starts it,
  with more it opens a menu of them.
- The prompt names the build; the agent reads the logs itself. Not: jazygit reading the logs and handing them over.
- The agent starts in the worktree that has the branch checked out (ADR 110). Without one, `I` is disabled: the
  branch is not checked out in a worktree, as `e` says (ADR 110). Not: starting in the worktree jazygit shows, where
  another branch is checked out and the agent's fix would land on it.
- The builds can show an older build than the tip (ADR 133): the prompt says that `{commit}` may not be what is
  checked out.
- It starts at once, without asking; the command log has its command, and a failed one is shown as a failed editor is
  (ADR 146). Not: a popup to edit the prompt first, until one is wanted.
- Once an agent handed the terminal exits, the repository is read again, as after the editor (`EditCmd`), as the
  agent may have changed it. One left running is seen by the reads every so often (ADR 125).
- `I` stays in the hint, disabled when it cannot start: without an agent in `config.toml` it says to add one; with
  nothing failed it says so, or that the build still runs. One failed job is enough, while others still run.

## Consequences

- `features-agent` is the first group of features that only reaches what it acts on through an extension point:
  adding an action on a failed build changes nothing in `features-github`.
- The views that show a `FailedBuild` take actions they do not know; another subject, as a pull request, needs its
  own key and its own views to show it.
- An agent gets no logs from jazygit: one that cannot run `gh`, or the program the hint names, cannot read them.
- What the agent does is its own: jazygit does not show its output, and does not know when one left running is done.
- Moving the editors onto the shared type of program changes no setting of theirs.
- ADR 160 (#587), still open, picks a check in the checks popup and keeps the logs of checks in a cache directory.
  The check picked changes nothing for `I`: it hands over every failed check. Naming the logs already kept in the
  prompt is for a later change.
