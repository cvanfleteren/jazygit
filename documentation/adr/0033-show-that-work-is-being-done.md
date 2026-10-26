# 33. Show that work is being done

Date: 2026-10-26

## Context

`Program.launch` runs every `Cmd` on one of two executors: `io`, a single thread for git, or `background`, for the
commands that say `background()`: the fetch, the reads of GitHub and of the checks, the changes of the worktrees, the
merge check and the gh checks in the settings. Only some of that shows while it runs: `FetchLine`, the line under
everything else, says `fetching <remote>` or that the pull requests are read; a branch an operation runs on shows a
spinner (`Jobs.busy`); the comments, pull request and checks dialogs have a spinner of their own. A slow `git log`, a
diff, a rebase or the reads of the checks of the branches show nothing, and the user cannot tell whether jazygit is
working or stuck.

The model does not know what runs. `Jobs.refreshing`, `fetching` and `busy` each keep track of a few commands, in
their own way; most commands, a diff or a preview say, leave no trace in the model while they run. Commands are
records compared by value, so two equal ones, like two `LoadStatus`, can run at once and cannot be told apart.

The model is told the time every 5 s (`Msg.Clock`, ADR 21), so `model.now()` can be 5 s old: too coarse to tell
whether a command ran for 300 ms. The clock ticks that slowly so that ages do not jump, which is about how they are
drawn, not about how often the model knows the time.

## Decision

One indicator shows that something runs, and what: a spinner, the label of what has run longest, and how many more
run, on the right of `FetchLine`, before how fresh the screen is: `⠋ fetching origin +2 · fetched 30s ago`.

### What counts

- Every command, on either executor, from the moment `Update` asks for it until `Program` reports it done: queued
  behind another on `io`, or waiting for its guard, the user waits all the same. Not: only the commands that run in
  the background; that says where they run, not whether the user waits for them. Not: only those that talk to a
  remote.
- A command shows only once it has run for 300 ms, so the reads that take a few milliseconds, on every key and every
  tick, do not make it flicker. `+N` counts only the others that ran that long too.
- Once shown, the indicator stays until 300 ms after the last command it showed is done, with the label of that
  command, so it does not blink between two commands. That is kept once, for the indicator, not per command
  (`Jobs.lingering`).
- A command that fails just stops showing; the failure shows as it did, as an error or as how fresh the screen is.
- The other spinners stay: a busy branch and the dialogs say where something runs; the indicator that something
  does. It counts their commands too. What `FetchLine` said on its left while fetching or reading the pull requests
  goes: that is now the label of those commands. The cherry-pick keeps the left.
- On a narrow screen the label is cut before how fresh the screen is.

### Kept in the model

- `Jobs.running` holds each command that runs, by an id, with its label and when it started; `Jobs.nextId` gives the
  next id. The model knows all that runs, so `Update` can decide on it, and it can be tested without `Program`. Not:
  kept in `Program`, which knows best when a command starts and ends, and told to the model by a message: that would
  be a second account next to `refreshing`, `fetching` and `busy`, always a message behind, and the model would no
  longer tell all that is drawn.
- A command does not carry its id. The handlers return `Next(model, List<Cmd>, quit)` as before. The public
  `Update.update` and `Update.init` stamp them, as their last step: each gets the next id and goes into `running`,
  and they return `Step(model, List<Launch>, quit)`, with `Launch(long id, Cmd cmd)`. `Program` only gets a `Launch`,
  so no command runs without an id. Not: an optional id on every command, which every command record would carry,
  which would make two equal commands unequal, which `refreshing`, `failed` and `retries` rely on, and which
  `Program` would have to check for.
- `Program` reports every end of a `Launch` as `Msg.Done(id, Option<Msg>)`: its result, `failed` for a bug,
  `skipped()` when a newer one for its slot cancelled it, and none when that has nothing to report. One case in
  `Update` takes the id out of `running` and handles what is inside as before; no handler changes. A command dropped
  as the app stops reports nothing, as nothing is drawn anymore.
- `Jobs.lingering` is `Option<Lingering(Label label, Instant until)>`: what the indicator shows after the commands it
  showed are done, and until when. When the `Msg.Done` of a command comes while nothing else that runs has shown,
  and the command ran 300 ms or more (`now - started >= 300 ms`), it lingers until `now` plus 300 ms. That is the
  same 300 ms the view goes by; when a command is done right as it passes them, the two may disagree for one frame.
- `Jobs.running` and `Jobs.lingering` are drawn (`Model.drawn()`), so a frame is drawn as soon as a command is done,
  not at the next turn of the spinner.

### Told the time

- `Program` gives `Update` the time each message is handled, and the time it starts:
  `Update.update(model, msg, now)` and `Update.init(..., now)`, so the first loads, the slowest, are stamped too.
  Every message sets `model.now()` to it: there is one clock, for the commands that run, the indicator, when a fetch
  was done and every age. `Update` stays pure: the time is given to it, and tests give a fixed one. Not: a
  `Msg.Clock` every 100 ms while something runs, which is many more messages and still a stale time for the first
  command. Not: the view remembering when it first saw each command, which would keep apart what the model can hold.
  Not: a second time next to `model.now()`, for the commands only, which would disagree with it by up to a clock.
- `Msg.Clock` comes every second, not every 5 s, and carries no time: it only wakes the model up, so ages move on
  while nothing else happens. The view rounds the ages, so they still do not jump: `TimeText.readAgo` rounds down to
  steps of 10 s from the exact time, as when a fetch was done and the time it is now are both exact. Not: rounding
  to whole clocks, as ADR 22 did, which was only needed while both were told by the clock.
- `Model.drawn()` takes `model.now()` down to the second, so a message draws no frame only because time moved on,
  as ADR 14 wants: an idle screen draws one frame a second, for the ages. Not: a 5 s clock, with which a step of
  an age would show up to 5 s late, by however much, as ADR 22 had it before.
- Whether a command ran for 300 ms is told when the frame is drawn, by the clock the spinners turn by.
  `Spinners.shown` is true while something runs or the indicator stays, so frames are drawn and it shows in time.

### Labelled

- `Cmd.label()` is `Option<Label>`, a message key with its arguments; none shows `working…`.
- Labelled from the start: every `Cmd.Load` (`loading commits`, `loading the status`, …, a key per command), the
  fetch (`fetching <remote>`), the reads of GitHub and of the checks, the changes of the worktrees, and every
  `OperationCmd`, from its `Operation` (`pushing feature/x`).

### Logged

- At debug level (`./run.sh --debug`), each command that is done is logged on one line: the command, how long it
  ran and the message it reported, like `Done LoadStatus 412ms → LoadMsg.StatusLoaded`. That line takes the place
  of the one `Program.dispatch` logs for each message, which would say `Done` for nearly all of them; other
  messages are logged as before.

## Consequences

- Built in two steps: first the ids, `Msg.Done`, `Jobs.running`, the time given to `Update` and the clock every
  second, with nothing new on the screen; then the labels, and the indicator in `FetchLine`, taking the place of
  what it said on its left. That step also adds the checks dialog to `Spinners.shown`, which it was left out of:
  its spinner only turned when something else drew a frame.
- ADR 21's clock every 5 s, and ADR 22's rounding to it, are replaced: the model knows the exact time, and the view
  rounds. The commands that read the remotes and the pull requests still ask for no time; the update stamps them
  with `model.now()`.
- Each command that runs for 300 ms or more shows, also those the user did not ask for, like the fetch every few
  minutes and the reads of GitHub.
- While something runs, a frame is drawn every 120 ms, also before it shows.
- Every message is handled with the time, and every command adds to and takes from `running`: some work for each,
  but no more messages than the clock every second.
- A command that `Program` never reports done would show for good; that is a bug, as a command that never answers
  already keeps its load in `refreshing` or its branch busy.
- `Jobs.refreshing`, `Jobs.fetching` and `GitHubState.reading` could later be told from `running`; not part of this.
- Tested: the stamping, also in `init`, and `Msg.Done` in `Update`, with a fixed time; `lingering`; that
  `Program` reports `Done` for a result, a bug and a skipped and a cancelled command; which command the indicator
  shows, `+N`, the 300 ms before and after, and cutting it on a narrow screen; `drawn()` to the second; the ages
  counted from exact times.
