# jazygit architecture

jazygit is a lazygit-style terminal UI for git, built on [TamboUI](https://github.com/tamboui).
It follows the **Elm architecture**:

- The whole UI state is one immutable `Model`.
- Every event (a key press, a tick, a git result) is a `Msg`.
- A pure update function turns `(Model, Msg)` into the next `Model` plus the IO to perform, as `Cmd`
  values.
- Commands run against git (and, for GitHub, `gh`) on a background thread, and their outcome comes back as a
  new `Msg`.

Rendering never talks to git, and update functions never do IO.

## Modules

The Maven build has two modules under the parent `pom.xml`:

| Module | Holds | Depends on |
|---|---|---|
| `jazygit-core` | `process`, `git`, `git.model`, `gh`, `gh.model`: running git and gh, and what they report as values. | vavr, zt-exec |
| `jazygit-app` | Everything else: `JazygitApp`, `cli`, `state`, `feature.*`, `ui`, `keys`, `settings`, `i18n`, and all resources. Builds the fat jar and the native image. | `jazygit-core`, TamboUI, picocli, night-config |

The packages keep their names (`net.vanfleteren.jazygit.git`, ...), whichever module they are in. The core
cannot see the application, TamboUI, picocli or night-config, so the compiler stops it from depending on them.
The rules inside the core (git and gh know nothing of each other, the models are pure) are in
`CoreArchitectureTest`. The core's test-jar shares `StubGitProvider`, `StubGitHub` and `SampleData` with the
app's tests.

`mvn verify` at the root builds and tests both. `./run.sh` runs the fat jar from `jazygit-app/target`, and
`mvn -Pnative package` builds `jazygit-app/target/jazygit`.

## Packages

```mermaid
graph TD
    App["JazygitApp<br/><i>entry point, layout, popups, focus</i>"]
    UI["ui<br/><i>panels and widgets: render a Model</i>"]
    Keys["keys<br/><i>Keymap: every shortcut, declared once</i>"]
    Feature["feature.*<br/><i>branch, commit, stage, stash, discard,<br/>github, fetch, error, help, theme, repos,<br/>settings, worktree, inprogress, edit</i>"]
    State["state<br/><i>Model, Msg, Update, Cmd, Program</i>"]
    Git["git<br/><i>GitInfoProvider, CliGitInfoProvider, Cli*, GitProcess, GitError</i>"]
    GitModel["git.model<br/><i>Branch, Commit, RepoStatus, Sha, ...</i>"]
    Gh["gh<br/><i>GitHub, CliGitHub, GhProcess, GhError</i>"]
    GhModel["gh.model<br/><i>PullRequest, PullRequestDetails, ...</i>"]
    Process["process<br/><i>Exec, ExecutedCommand, CommandLine</i>"]
    I18n["i18n<br/><i>Messages</i>"]
    Settings["settings<br/><i>Settings, SettingsFile, SettingsPaths,<br/>RecentRepositoriesFile</i>"]

    App --> UI
    App --> State
    App --> Feature
    App --> Git
    App --> Gh
    UI --> Keys
    UI --> State
    UI --> Git
    UI --> Gh
    Keys -. "lists their keys" .-> Feature
    Feature --> Keys
    Feature --> UI
    Feature --> State
    Feature --> Git
    State --> Git
    Git --> GitModel
    Feature --> Gh
    State --> Gh
    Gh --> GhModel
    Git --> Process
    Gh --> Process
    State --> Process
    UI --> I18n
    App --> Settings
    State --> Settings
    Feature --> Settings
    UI --> Settings
    Keys --> I18n
    Feature --> I18n
```

The arrows are the dependencies allowed. Inside `jazygit-app`, `ArchitectureTest` fails on any other;
inside `jazygit-core`, `CoreArchitectureTest` does; and the core cannot reach the app's packages at all.

- **The parts have no cycles**, with one exception: `Keymap.standard()` lists the keys of the features, which
  declare them with the types of `keys` (dotted).
- **Views use `ui`, but only to draw:**
  - The dialogs of a feature use the widgets of `ui`.
  - `ui` uses `git` and `gh` only for the errors, which `ErrorText` describes.
  - Nothing in `ui`, `keys` or a dialog runs git or gh.
- **`git` and `gh` know nothing of each other**, nor of anything above them: they live in `jazygit-core`.
- **Only views use `i18n`.** `state` does not.
- **`settings` knows nothing of the application**, and is the only part that reads or writes TOML.

| Package | Responsibility |
|---|---|
| `JazygitApp` | Opens the repository, starts the `Program`, lays out the panes, shows the open popup and moves the focus. |
| `ui` | Panels (`FilesPanel`, `BranchesPanel`, `CommitsPanel`, `ContentPanel`, `CommandLogPanel`, ...) that render a `Model` and turn key events into `Msg`s; the dialog widgets (`ConfirmDialog`, `ChoiceDialog`, `TextInputDialog`, `PickDialog`) and the `DialogFrame` they are drawn in, with `WithDetails` for a box of details under one; `ErrorText`, the only place that turns errors into text. |
| `keys` | `Keymap`: the keys of each panel (`Scope`) and the global keys. Panels react to keys from it, and the help popup lists them from it. |
| `feature.*` | One package per feature. Each one holds the feature's messages, its update functions, its commands, and its popups, dialogs and keys. |
| `state` | The Elm core: `Model`, `Msg`, `Update`, `Cmd`, `Program`, `Loadable`, `Preview`, the parts the `Model` is grouped in (`Repo`, `RepoView`, `GitHubState`, `Jobs`, `Outcome`, `Preferences`), the operations (`OperationCmd`, `OperationMsg`, `OperationUpdate`), the periodic reads (`LoadUpdate`, `PullRequestsUpdate`), the highlights of the panes and the previews read for them (`SelectionMsg`, `SelectionUpdate`), the command log and the errors as data (`AppError`, `Failure`). |
| `git` | Everything that touches the repository, all through the `git` command line. All results are `Either` values. |
| `git.model` | Immutable value types describing the repository. |
| `gh` | Everything that talks to GitHub, all through the `gh` command line. It knows nothing of `git`, and `git` knows nothing of it; the features combine them. |
| `gh.model` | Immutable value types describing GitHub: `PullRequest`, `PullRequestDetails`, `MergeMethod`, `GhAccount`, `GhLogin`. |
| `process` | `Exec`, the only place that starts a program, for `git` and `gh` alike, and its `ExecFailure`; `ExecutedCommand`, a command as it ran; `CommandLine`, how a command line is written. |
| `i18n` | `Messages`: the user-facing texts, from `messages.properties`. |
| `settings` | What the user keeps from one session to the next (`Settings`, `ThemeName`), where it is kept (`SettingsPaths`) and the TOML file it is kept in (`SettingsFile`, behind `SettingsStore`); the state jazygit keeps by itself, the recent repositories (`RecentRepositoriesFile`, behind `RecentRepositories`). |

## Settings

The settings live in `config.toml`, in the directory that is the standard for the OS:

- **Linux:** `$XDG_CONFIG_HOME/jazygit` if that is set to an absolute path, else `~/.config/jazygit`.
- **macOS:** `$XDG_CONFIG_HOME/jazygit` if that is set, else `~/Library/Application Support/jazygit`.
- **Windows:** `%APPDATA%\jazygit`.
- **`$JAZYGIT_CONFIG_DIR`** overrides the directory on all of them.

`main` loads them before the app starts, so the first frame is drawn in the right theme. A problem with the
file never stops the app:

- A missing file gives the defaults. The file is only made when a setting is first changed.
- A file that cannot be read, or a value that is not valid, gives the defaults, and the command log says so.
- Keys jazygit does not know are ignored.

A change in the app, like choosing a theme, updates the `Model` and returns a `Cmd.SaveSettings` with the
settings before and after. It writes only the settings that changed, so a hand edit made while jazygit runs
stays. Saving keeps the other keys, their order and their comments. It writes a temporary file and moves it
over the old one, and leaves a file that is not valid TOML alone. When saving fails, the error is shown, and
the setting still holds until jazygit stops.

Ctrl+S shows the settings (`feature.settings`): the gh executable, the theme, and the GitHub account of the
repository. The keys in `config.toml` are `ui.theme` and `github.executable`, the gh to run when it is not the
one on the PATH; a new one is only kept once `<executable> --version` runs. Changing the gh executable or the
account shows the repository again through `Env.repositories()`, as switching to a recent repository does, so a
new `Env` runs gh as the settings say now.

## Recent repositories

The repositories opened last are state, not settings: jazygit writes them by itself, and they are not meant
to be edited. They live in `state.toml`, as `recent-repositories`: the roots of their working trees, the
last one opened first, at most 20.

- **Linux:** `$XDG_STATE_HOME/jazygit` if that is set to an absolute path, else `~/.local/state/jazygit`.
- **macOS:** `$XDG_STATE_HOME/jazygit` if that is set, else `~/Library/Application Support/jazygit`.
- **Windows:** `%LOCALAPPDATA%\jazygit`.
- **`$JAZYGIT_STATE_DIR`** overrides the directory on all of them.

`main` records the repository it opens. Every change reads the file again first, so what another jazygit
recorded meanwhile stays. A file that cannot be read gives none, and is written anew; failing to write is not
shown, as the list is only a convenience.

Ctrl+R lists the others, leaving out the ones whose directory is gone, which are forgotten too. Choosing one
runs `RecentReposCmd.SwitchTo`, which asks `Env.repositories()` to open it. `JazygitApp` does that: it opens
the repository on the thread of the command and then, on the render thread, stops the runtime of the old one
without waiting for the git command that runs, and starts a new `Program` for the new one from scratch. The
theme and the settings of the session stay. A repository that cannot be opened is forgotten, and the error is
shown in the one still open.

## Worktrees

The worktrees of the repository are read with the other periodic loads, into `Model.worktrees`, and listed on the
third tab of the branches pane (`feature.worktree`). The one shown is marked, and so are the main one, a locked
one and one whose directory is gone.

- **Adding:** `w` on a local branch asks where to check it out (`git worktree add <path> <branch>`), next to the
  main worktree by default, as `<main>-<branch>`. A leading `~` is the home directory. Once added, the pane shows
  the worktrees.
- **Switching:** space on a worktree opens it in place of the one shown, as switching to a recent repository
  does (`RecentReposCmd.SwitchTo`), which makes it a recent one too.
- **Removing:** `d` removes a worktree other than the main one and the one shown (`git worktree remove`). When
  git refuses because it has changes (`GitError.WorktreeHasChanges`), removing it anyway (`--force`) is offered.
- **A branch checked out in another worktree** is marked `(worktree)` in the list of branches. git checks a branch
  out in one worktree at a time, so what it would refuse is not run: checking the branch out offers to switch to
  that worktree instead, and fast-forwarding it, deleting it locally or adding a worktree for it fail up front with
  an `AppError.CheckedOutElsewhere` that names the worktree. Deleting it on its remote still works.

## Divergence

`u` on a local branch offers, besides resetting the checked out branch onto its upstream, to view how the
highlighted branch diverged (`feature.branch`, `UpstreamUpdate`):

- **From its upstream** (`v`): the remote-tracking branch it follows, or the local one for an upstream in the
  repository itself (`BranchRef.of`).
- **From the base branch** (`b`): `origin/<main>`, else the local `<main>`, where `<main>` is the branch `Branch.isDefault`
  marks. The main branch itself has no base.

`Model.divergence` (`state.DivergenceView`) holds the two branches, the commits only on either of them, read with
`GitInfoProvider.divergence` (two `git log a ^b` of at most 300 commits each, and `rev-list --left-right --count` for
how many there are), and the preview of the highlighted one. While it is open, the branches pane lists it in place of
the local branches: the commits only on the other branch, then the ones only on the local branch, each under the
name of its branch. The highlight skips those names. The content panel shows the highlighted commit as the commits
pane does, in a scroll area of its own, so the two panes do not take each other's preview. Escape, or switching
tabs, closes it.

It is read again when either branch moves, as the periodic loads of the branches tell. When the branch is gone, it
closes; when the upstream is gone, or the base branch, it closes with the error that says so.

## The pull requests screen

Ctrl+P switches the left of the screen between the repository and the pull requests (`Model.screen`, `state.Screen`);
the status panel names both, the one shown in bold. What the pull requests screen shows lives in one part of the
model, `Model.prOverview` (`state.prs.PrOverview`), with its own messages and its own update, which works on that part
alone (`PrOverviewMsg`, `PrOverviewUpdate`); only switching screens, and whether the screen is shown, look at the rest.
It lives in `state`, next to the other reads of GitHub, so the GitHub feature can read it again after changing a pull
request. The design and the steps still to come are in `prs.md`.

- **Review requested** and **My PRs** list the open pull requests that ask for the user's review and the ones they
  opened, in any repository: one `gh api graphql` with two searches (`review-requested:@me`, `author:@me`), at most
  50 each; the title says how many match when there are more. **This repo** lists the open pull requests of the
  repository, forks included, at most 100: another `gh api graphql`, apart from the read for the branches pane, which
  also tells which repository it is (`PrOverview.repositoryName`). The three share one fragment and one parser
  (`CliGitHub.parseLists`), with the checks of the last commit of each.
- They are read when the screen is shown, every minute while it is shown (`PrOverviewMsg.Tick`, from `JazygitApp`),
  with `r`, and after a pull request was merged, closed or turned into a draft. Failing to read them again keeps the
  ones read; `PrOverview.freshness` keeps when they were last read and which read failed since, which the line at the
  bottom says on this screen ("read 2m ago · last read failed"). gh runs as the account of the repository, so the lists are that account's.
- `o` shows the pull request dialog, as for a branch, for a pull request of the repository, wherever it is listed;
  one of another repository opens in the browser, as `O` does for any (`gh pr view <url> --web`).
- **The preview**: the content panel shows the pull request highlighted in the pane in use like a commit: a header,
  its title and description, `---`, and its diff, read with `gh pr view <url> --json baseRefName,body` and
  `gh pr diff <url>`, so also for repositories that are not cloned; the diff is cut off after 5000 lines. It is read
  again when the pull request changed (`PrOverview.preview` is keyed by its summary), and the one shown before
  stays while it is read. Enter moves the focus into it, Escape back to the pane it shows.
- **Checking out**: space checks out a pull request of the repository in place, from a fork too, with
  `gh pr checkout <n>` (`CheckoutPullRequestCmd.CHECKOUT_PR`), which waits until the repository is in the middle of nothing. It is
  refused up front when the working tree has changes, untracked files apart (`AppError.ChangesBeforeCheckout`), and
  for a pull request of another repository (`AppError.OtherRepository`). Which repository this is is known once its
  pull requests were read; until then, checking out says they are still being read, or why they could not be.
- Each screen keeps where its focus was: `Msg.Focused` goes to `Model.focusedPane` or `PrOverview.focusedPane`,
  whichever screen is shown (`Model.shownPane`), and `JazygitApp` gives it back when the screen is shown again.

## Diff options

How the diffs of the files and the commits are read is a `DiffOptions` (in `git.model`), kept in
`Model.diffOptions` for the session only. For now it says whether changes to whitespace only are left out, which
Ctrl+W toggles anywhere outside a dialog (`feature.diff`); the context and word or side-by-side diffs are to
follow it there.

- `GitInfoProvider.diff` and `commitChanges` take the options, which `CliDiff.flags` turns into git's
  (`--ignore-all-space`). A file whose changes are all whitespace is then left out of the diff entirely, also from
  the files `git show --stat` lists.
- `Cmd.LoadFileDiff` and `Cmd.LoadCommitDetail` carry the options, and so do the messages they send back. What
  was read with other options than the model's arrives too late and is dropped, as what was read for another
  highlighted item is.
- Toggling reads the diffs shown again (the files, the commit and the commit of a divergence), which keep showing
  as they were until they are read.
- The titles of the diffs say when whitespace is ignored, and a diff that comes back blank says that only
  whitespace changed.
- The diff of a pull request is read with `gh pr diff`, which has no such option, so it shows all changes.

## The editor

`e` on a file in the files pane opens it in git's editor (`feature.edit`): the one `git var GIT_EDITOR` names, so
`$GIT_EDITOR`, `core.editor`, `$VISUAL`, `$EDITOR`, else `vi`, as for a commit message. `CliEditor` runs it as git
does: the program itself when it is a plain name, else through `sh -c '<editor> "$@"'`, so `code --wait` works. It
is an `EditCmd.EDIT` like any other: the command log shows it, an editor that exits with an error is shown as a
failure, and the repository is read again once it is closed.

Ctrl+O in the commit and reword dialogs opens the message typed so far in the same editor (`MessageEditorMsg`,
`MessageEditorCmd`). `CliEditor.editMessage` writes it to a file named `COMMIT_EDITMSG` in a temporary directory, so
editors treat it as a commit message, and reads it back once the editor is closed. No comments are added to it, so
lines starting with `#` are kept. The text being typed lives in the dialog's widgets, not in the model, so the
message comes back as the popup's `draft` (`MessagePopup`): a popup that changed counts as newly opened for
`CommitDialog`, which then fills its fields from the draft. The dialog stays open meanwhile, and a failing editor
leaves it as it was.

The editor needs the terminal, which the TUI owns. `Env.terminal()` (`state.Terminal`) hands it over: `EditCmd`
and `MessageEditorCmd` run the editor inside `terminal().handOver(...)`, on the `git-io` thread. `JazygitApp`
implements it with `HandOverBackend`, the TamboUI backend it gives the runner:

- **Leaving**, on the render thread between two frames: it stops reading keys, then puts the terminal back as it
  was before the app started (no mouse capture or bracketed paste, cursor shown, main screen, cooked mode), and
  from then on drops what the app draws.
- **Meanwhile** the app keeps running, as it does not block the render thread: TamboUI's ticks, which come every
  40 ms, would otherwise pile up and be handled one by one afterwards. Loads and fetches carry on; what they draw
  is dropped.
- **Coming back**, on the render thread again: it sets the terminal up as before, reads keys again, and clears
  TamboUI's buffers, so the next frame draws the whole screen.

Keys are read by polling the terminal (`available()` on the standard input), not through JLine. After a read that
timed out, JLine leaves a thread blocked on reading the terminal, which would take the first key typed in the
editor. On Windows, whose console cannot be polled that way, keys are read through JLine.

## The runtime loop

`Program` holds the current `Model`. `JazygitApp` gives it three executors:

- **ui**: the TamboUI render thread. It is the only thread that reads or replaces the model.
- **io**: a single `git-io` thread, so the commands the user asks for never run concurrently.
- **background**: a `git-fetch` thread for commands that are slow and that nobody waits for
  (`Cmd.background()`), so they don't hold up the others: the fetch, and reading the pull requests.
  - A fetch can run next to a command on `git-io`. git locks what it writes, so the two never corrupt the
    repository.
  - A command that needs a ref the fetch is updating at that moment can fail on the lock, though. That
    failure is reported like any other.

Every command answers with a `Msg`, also one that throws. `Program.perform` turns whatever it throws into a
`Failure.Bug`, and writes the stack trace to `jazygit-bugs.log` in the temporary directory, as the terminal
belongs to the TUI. Without an answer, the command's load would keep running, or its branch would stay busy.

When the app stops, the commands still queued on `git-io` are dropped. The one that runs is not interrupted,
which would kill git half-way, say in the middle of a rebase. It gets two seconds to finish, and otherwise
finishes by itself once the app is gone.

```mermaid
flowchart LR
    subgraph render["render thread"]
        Event["key event / tick / selection change"] --> Dispatch["Program.dispatch(msg)"]
        Dispatch --> Update["msg.apply(model)<br/><i>pure</i>"]
        Update --> Next["Next(model, cmds)"]
        Next --> Store["program.model = next.model"]
        Store --> Render["JazygitApp.render()<br/>draws the Model"]
    end
    subgraph io["git-io thread"]
        Perform["Program.perform(env, cmd)<br/>cmd.run(env)"] --> Result["Msg<br/><i>OperationMsg / LoadMsg / ...</i>"]
    end
    Next -- "each Cmd" --> Perform
    Result -- "ui.execute" --> Dispatch
```

Every 500 ms a `Msg.Tick` triggers `Update.refresh`, which re-reads the status, the local and remote
branches, and the stashes.
That keeps the screen in sync with changes made outside the app. A periodic load is skipped while an
equal one is still running: `Model.refreshing` holds the loads that are running.

Every minute a `FetchMsg.Tick` fetches from the remotes in the background, unless the previous fetch is
still running. `FetchUpdate` lists the remotes (those `git fetch --all` would fetch) and fetches them one after
the other with `git fetch --prune <remote>`, so branches deleted on the remote show as gone, whatever
`fetch.prune` says. `Model.fetching` holds the remote being fetched from, which the line at
the bottom of the screen shows, and the remotes still queued. When the queue is empty, the branches reload.
`Model.lastFetch` (a `Freshness`) holds when a remote was last fetched from, as `FetchCmd.Fetch` timed it, and the
remotes that failed the last time; the right of the line at the bottom says it, as "fetched 2m ago", with "last fetch
failed" when one did.
Neither the commands nor the failures are shown, since fetching also runs while offline, and a failing remote
doesn't stop the others. Once the fetch is done, the open pull requests are read again with `gh` (also once at
startup), into `Model.pullRequests`, which is none until they were read once. That read lives in `state` (`PullRequestsUpdate`, `LoadPullRequests`),
next to the other periodic reads, so the fetch does not depend on the GitHub feature. While it runs, the line
at the bottom says so, and `Model.readingPullRequests` keeps another read from starting; a failure there is
not shown either, as gh may be missing or logged out.

A change made on GitHub (creating, merging or closing a pull request) asks for a fetch at once, through
`FetchUpdate.requested`, so the branches show it without waiting for the next tick. When a fetch is already
running, it may have missed the change, so `Fetching.again` makes another one follow it. The pull requests are
read again at once too, even when a read is running, as that one may have missed the change as well.

## Core types

```mermaid
classDiagram
    class Msg {
        <<interface>>
        +apply(Model) Next
    }
    class Next {
        <<record>>
        Model model
        List cmds
    }
    class Cmd {
        <<interface>>
        +run(Env) Msg
        +failed(Failure) Msg
        +failedWith(GitError / GitFailure) Msg
        +failedWith(GhError / GhFailure) Msg
    }
    class Load {
        <<sealed interface>>
    }
    class OperationCmd {
        <<interface>>
        +operation() Operation
        +subject() Option
        +busyBranch() Option
        +succeeded(List) Msg
    }
    class GitOperationCmd {
        <<interface>>
        +perform(GitInfoProvider) Either
    }
    class Model {
        <<record>>
        Loadable status
        Loadable branches
        Loadable commits
        Loadable stashes
        Option popup
        Option error
        List commandLog
        Set refreshing
        ...
    }
    class Loadable {
        <<sealed interface>>
    }
    Loading --|> Loadable
    Loaded --|> Loadable
    Failed --|> Loadable
    class Popup {
        <<interface>>
    }
    Msg ..> Next : apply returns
    Next o-- Model
    Next o-- Cmd
    Load --|> Cmd
    OperationCmd --|> Cmd
    GitOperationCmd --|> OperationCmd
    Model o-- Loadable
    Model o-- Popup
    Cmd ..> Msg : run returns
```

- **`Msg` applies itself.** Each feature has a sealed sub-interface, for example `RewordMsg`, whose
  `apply` delegates to that feature's update class (`RewordUpdate`). Adding a feature therefore needs
  no change to `Update` or `Msg`. The one-line `apply` overrides, one per feature message interface, are
  the price of avoiding a central dispatch, and are kept on purpose.
- **Every dialog has its own `Cancelled`.** Cancelling is a one-liner,
  `Next.of(model.withoutPopup(X.class))`, so there is no `Update.cancel` helper. A generic
  `PopupMsg.Closed(type)` sent by the dialog widgets would remove some 20 `Cancelled` records, at the cost
  of a message that is not about a feature.
- **`Cmd` is immutable data** that knows how to run itself against an `Env` (the `GitInfoProvider` and the
  `GitHub`) and how to report failure:
  - `run` folds the provider's `Either` into a `Msg`.
  - `failed(Failure)` turns a failure into the failure `Msg`. A `Failure` is data: `Failure.Git` (the
    `GitError`, the commands that ran, and why the cleanup failed if it did), `Failure.GitHub` (the `GhError`
    and the commands that ran) or `Failure.Bug` (the command threw, which `Program.perform` catches).
- **`Cmd.Load`** reads part of the repository into the model, as a `Loadable`. Every `LoadMsg` names its
  load, so `LoadUpdate` marks it as no longer running in one place.
- **`OperationCmd`** changes the repository. It names its `Operation`, the branch it is done to, and the branch
  it keeps busy. An `Operation` is a record that the feature declares next to its commands (`CommitCmd.DROP`,
  ...): the command log title, the error message, whether it waits until nothing is unfinished, and what a
  branch it keeps busy shows next to its spinner. Its outcome is an
  `OperationMsg.Succeeded`, `OperationMsg.Stopped` (it stopped on conflicts, left for the user) or
  `OperationMsg.Failed`, which `OperationUpdate` handles the same way for every operation: it logs the
  commands, releases the busy branch, stores an `AppError.OperationFailed`, a `Notice` or neither, and reads
  the repository again. A feature only adds its own messages for outcomes that really
  are special, like `CheckoutMsg.NeedsStash`; `OperationUpdate.succeeded` is what any success does, which
  such a message adds to. A `GitOperationCmd` is one done through git alone: it only implements
  `perform(GitInfoProvider)`. The others, like creating a pull request, implement `run(Env)` themselves.
- **Operations are started through `Update`.**
  - `Update.start` marks the operation's branch busy.
  - `Update.ask` opens the popup that asks about an operation, which clears the error of the last one.
  - `Update.confirm` closes that popup and starts the operation made of it.
  - A branch that is busy (`Model.idleBranch`) takes no other operation until it is done.
- **Errors are data until the view.** `Model.error` is an `Option<AppError>` and `Loadable.Failed` holds a
  `Failure`; `ErrorText` in `ui` turns them into text. `Model.fail` stores the error, which the command log
  shows, and opens an `ErrorPopup`, unless another popup is open, which then keeps what the user entered.
- **Popups are part of the model.** `model.popup()` says which dialog is open. `JazygitApp` renders the
  dialog that matches it and moves the focus into it, then back to the pane it came from once
  the popup closes. Each feature lists its dialogs, with the pane they give the focus back to, as
  `PopupSlot`s in `XxxDialogs.popups(dispatch)`; `JazygitApp` only names the features.
  A popup that comes up by itself (`Model.openPopupIfFree`), like the outcome of a command or the theme key, does not replace a dialog the user is typing in.
- **The command log** (`Model.commandLog`) lists every git command that changed the repository, under the
  `Operation` that ran them, whether it succeeded or failed.

### The anatomy of a feature

Each feature package follows the same shape:

| Type | Example (reword) | Role |
|---|---|---|
| `XxxMsg` | `RewordMsg` | Sealed set of messages, usually `Requested`, `Confirmed` and `Cancelled`; a feature with several dialogs or outcomes names them after those (`StashMsg.DropRequested`, `PullRequestMsg.MergeChosen`). |
| `XxxUpdate` | `RewordUpdate` | Pure transitions for those messages; `Update.confirm` closes a popup and starts the operation made of it. |
| `XxxCmd` | `CommitCmd.Reword` | The operation the feature asks for, an `OperationCmd`. |
| `XxxPopup` | `RewordPopup` | What the open dialog is about; it lives in the model. |
| `XxxDialogs` | `CommitDialogs.SQUASH`, `CommitDialog.reword` | The popups, mostly as data (`ConfirmDialog.Spec`, `ChoiceDialog.Spec`, `TextInputDialog.Spec`, `PickDialog.Spec`) that `popups(dispatch)` turns into widgets, as the `PopupSlot`s `JazygitApp` shows; `XxxDialog` for a feature with one, or a dialog of its own. |
| `XxxKeys` | `CommitKeys` | Adds the feature's keys to the immutable `Keymap`. |

The branch package gives each of its operations a subpackage of its own (`checkout`, `newbranch`, `rename`,
`delete`, `merge`, `rebase`, `push`, `pull`, `upstream`, and `tabs` for switching between the local branches, the
remote ones and the worktrees), with its `XxxMsg`, `XxxUpdate` and popups. What they share stays in
`feature.branch`: `BranchCmd`, `BranchDialogs`, `BranchKeys`, and `Branches`, which knows which branches are
checked out where. The commit package likewise gives each of its operations a subpackage of its own (`create`, `amend`, `reword`, `squash`,
`fixup`, `drop`, `reset`), with its `XxxMsg`, `XxxUpdate` and popups. What they share stays in `feature.commit`:
`CommitCmd`, `CommitDialogs`, `CommitKeys`, and the message dialog that committing and rewording both use
(`CommitDialog`, `MessagePopup`, `MessageEditor*`). Amending is one operation with an `AmendTarget`: the last
commit, which is amended in place, also while something is in progress, or an older one, which rebases the commits
after it and so waits until nothing is.
`ErrorPopup` lives in `state`, as `Model.fail` opens it; the error feature only closes it.

## The git layer

```mermaid
classDiagram
    class GitInfoProvider {
        <<interface>>
        status() Either of ProcessError or RepoStatus
        log(branch) Either of GitError or commits
        diff(files, DiffOptions) Either of GitError or Diffs
        checkout(branch) Either of GitFailure of GitError or commands
        reword(Sha, CommitMessage) Either of GitFailure of GitError or commands
        pop(Stash) Either of GitFailure of GitError or commands
        ...
    }
    class CliGitInfoProvider {
        +open(Path) Either of GitError or provider
    }
    CliGitInfoProvider ..|> GitInfoProvider
    CliGitInfoProvider ..> CliStatus
    CliGitInfoProvider ..> CliBranches
    CliGitInfoProvider ..> CliLog
    CliGitInfoProvider ..> CliDiff
    CliGitInfoProvider ..> CliShow
    CliGitInfoProvider ..> CliCheckout
    CliGitInfoProvider ..> CliBranch
    CliGitInfoProvider ..> CliIndex
    CliGitInfoProvider ..> CliMerge
    CliGitInfoProvider ..> CliRebase
    CliGitInfoProvider ..> CliStash
    CliGitInfoProvider ..> CliRemote
    CliRebase ..> Unfinished
    CliMerge ..> Unfinished
    CliBranch ..> Unfinished
    Unfinished ..> GitProcess
```

Every `Cli*` class runs git through `GitProcess`; only a few of those arrows are drawn.

- **Everything goes through the `git` command line**, so the repository is read and changed exactly as
  git itself does: its configuration, hooks, credentials and repository formats included.
  - **Reads:**
    - `CliStatus` reads `git status --porcelain=v2`.
    - `CliBranches` runs one `git for-each-ref` for all branches, with their upstream and ahead/behind
      counts and the other worktree each is checked out in (`%(worktreepath)`, `Branch.worktree`), and another
      one for the remote-tracking branches, which the branches pane shows on its second tab
      (`Model.branchesTab`, switched with `[` and `]`).
    - `CliWorktree` reads `git worktree list --porcelain -z`, which the branches pane shows on its third tab.
    - **Linked worktrees** are read like any repository: nothing assumes `.git` is a directory. What lives in
      the repository's own directory, like the index or `MERGE_HEAD`, is found with `git rev-parse --git-path`.
    - `CliLog` runs `git rev-parse` and `git log -z`.
    - `CliStash` runs `git stash list`, and `CliRemote` reads the remotes.
    - These each have a pure parser for git's output. `CliDiff` and `CliShow` return git's text, cut off
      when it is long.
  - **Writes:** `CliCheckout`, `CliBranch` (push, pull, delete), `CliIndex` (stage, commit, discard),
    `CliMerge`, `CliRebase`, `CliStash`, `CliRemote` (fetch) and `CliWorktree` (add, remove).
  - **Opening:** `CliGitInfoProvider.open` asks `git rev-parse` whether the directory is in a repository,
    whether that is bare, and where its working tree is.
- **`GitProcess.run(workTree, GitCommand)`** is the only place that starts git, through `process.Exec`:
  - A `GitCommand` is data: the arguments, the environment and a timeout.
  - Every command runs with `LC_ALL=C` (git's messages are matched in English), `GIT_TERMINAL_PROMPT=0`
    (fail rather than wait for credentials) and `GIT_EDITOR=false` (fail rather than open an editor on the
    terminal the TUI owns; a command that is to edit something says how).
  - **Timeouts and locks:** a command that changes the repository gets 5 minutes, for its hooks. A read
    (`GitCommand.reading`) gets 30 seconds and takes no optional locks.
  - **Paths:** a command on paths takes them literally (`withLiteralPaths`), never as patterns, so
    discarding a file named `*.txt` discards only that file.
  - **Remotes:** a command that talks to a remote runs ssh in batch mode (`CliRemote.toRemote`), so a
    passphrase or unknown host fails rather than prompts.
  - It returns `Either<GitFailure<ProcessError>, Output>`. A failure's `CommandFailed` holds the line
    that explains it (the first `error:` or `fatal:` line, else the first line) and everything git printed.

## The GitHub layer

`gh.GitHub` is what the TUI does with GitHub; `CliGitHub` does it with the `gh` command line tool, which finds
the GitHub repository from the git remotes itself. It mirrors the git layer without depending on it:
`GhCommand` (run with `GH_PROMPT_DISABLED=1`), `GhProcess`, and its own errors, `GhError` and `GhFailure`.

- **Reading the pull requests:** `gh pr list --json ... --jq ...`. gh's built-in jq turns the JSON into one
  tab-separated line per pull request (fork pull requests left out), which `CliGitHub.parse` reads; there is
  no JSON library. The CI checks are folded into one state: failing, else pending, else passing.
- **The details of one pull request**, for its dialog: `gh pr view <n> --json ... --jq ...`, one tab-separated
  line per piece (the pull request, its labels, reviews, requested reviewers and checks), which
  `CliGitHub.parseDetails` reads.
- **Creating and opening:** `gh pr create --head <branch on the remote> --base <base> --fill` and
  `gh pr view <n> --web`. The name of the branch on its remote is the branch's push target, as the branches
  were read (`Branch.pushTarget`), which `PullRequestUpdate` puts in `PullRequestCmd.CreatePullRequest`.
- **Merging and closing:** `gh pr merge <n> --merge|--squash|--rebase` and `gh pr close <n>`, chosen in the
  pull request dialog. The branch is kept. Once done, the pull request is dropped from the open ones and they
  are read again.

### The account of a repository

gh can be logged in to several accounts, but which one it uses is global. A repository can be configured to use
another one, without changing that: the key `jazygit.githubUser` in its own git configuration names the login,
on the GitHub host of its remotes (`origin`'s, else the first one named like GitHub; an ssh host alias is
resolved with `ssh -G`). The settings list the accounts with `gh auth status --json hosts`.

`GhToken` asks `gh auth token --user <login> --hostname <host>` once, the first time it is needed, and keeps
the token in memory only. `GhProcess` sets it as `GH_TOKEN` (`GH_ENTERPRISE_TOKEN` and `GH_HOST` for a
GitHub Enterprise Server) for every gh command, and `CliGitInfoProvider.withCredentials` sets it for git's
commands that talk to a remote, for gh's credential helper. A token GitHub rejects is asked again once. A
token gh does not have is kept as `GhError.TokenUnavailable`: gh does not run as another account, the error
is shown once, and git still talks to the remotes without it. Without a configured account, nothing is set,
and gh uses its active one.

### Errors as values

No git operation throws on purpose: one that does is a bug, which `Program.perform` reports as a
`Failure.Bug`. A read returns `Either<E, T>`. An operation that changes the repository
returns `Either<GitFailure<E>, List<ExecutedCommand>>`:

- On success, the right side is the commands that ran.
- On failure, a `GitFailure` pairs the error with the commands that ran up to and including the
  failing one.

An `ExecutedCommand` holds the program and its arguments as they were run; `line()` writes it as one would
type it in a shell, which is how the command log shows it. The environment is left out, so a command that
runs with, say, `GIT_SEQUENCE_EDITOR` set does not quite do the same when typed again.

`E` is `ProcessError` when the operation only runs git commands, and `GitError` when it can also fail
in a way of its own; which ones is documented on the operation.

```mermaid
classDiagram
    direction BT
    class GitError {
        <<sealed>>
    }
    class ProcessError {
        <<sealed>>
    }

    ProcessError --|> GitError

    GitUnavailable --|> ProcessError
    TimedOut --|> ProcessError
    Interrupted --|> ProcessError
    CommandFailed --|> ProcessError
    UnexpectedOutput --|> ProcessError
    LocalChanges --|> GitError
    WorktreeHasChanges --|> GitError
    StashNotRestored --|> GitError
    TempFileFailed --|> GitError
    UnknownBranch --|> GitError
    RefusedPath --|> GitError
    StashGone --|> GitError
    StoppedOnConflicts --|> GitError
    NotARepository --|> GitError
    NoWorkTree --|> GitError
```

Some helpers combine these results:

- `GitFailure.sequence(...)` runs steps in order and stops at the first failure. That failure also
  carries the commands of the steps before it.
- `GitFailure.widen(...)` views a `ProcessError` failure as a `GitError` one.
- `GitFailure.followedBy(cleanup)` adds the commands of a cleanup, like `git rebase --abort`, and records in
  `cleanupFailed` why it failed, if it did.
- `GitProcess.log`, `read` and `ask` run a command for its effect, to read something, or to ask a question
  git answers "no" to by exiting with 1 silently.
- `Unfinished.runOrUndo` runs a merge, rebase, pull or cherry-pick and looks at what it left when it fails.
  - It first reads whether the repository is already in the middle of something: a merge, rebase,
    cherry-pick or revert, or unresolved conflicts.
  - If it is, git refuses to start the command, and nothing is undone. What is unfinished is the user's.
  - If the command stopped on conflicts, it is left so, for the user to resolve them (in the app or outside
    it) and continue or abort (M). The failure becomes a `StoppedOnConflicts`, saying what is in progress and
    how many files conflict. `GitOperationCmd` reports that as `OperationMsg.Stopped`, not as a failure, and
    `OperationUpdate` shows it as a `Notice` in the command log instead of an error.
  - If it stopped half-way otherwise, like a cherry-pick that turns out empty, it is undone: a rebase or
    cherry-pick that is still going is aborted. `runOrStop` is the variant with nothing to undo, for merges.

Parsing never throws: the parsers in `GitOutput` and the `Cli*` classes return
`Either<UnexpectedOutput, T>`, so output git was not expected to print is a `ProcessError` like any other.
The git package returns data, not messages; `ErrorText` in `ui` turns errors into text.

## Example: rewording a commit

The user selects a commit in the commits pane, presses `r`, edits the message and confirms. The flow
below shows what happens.

```mermaid
sequenceDiagram
    actor User
    participant Panel as CommitsPanel
    participant Program as Program (render thread)
    participant Update as RewordUpdate
    participant App as JazygitApp.render
    participant Dialog as CommitDialog.reword
    participant IO as git-io thread
    participant Provider as CliGitInfoProvider
    participant Git as git CLI

    User->>Panel: presses "r" on a commit
    Panel->>Program: dispatch(RewordMsg.Requested(sha))
    Program->>Update: requested(model, sha)
    Update-->>Program: Next(model + RewordPopup(commit), no cmds)
    Program->>App: render()
    App->>Dialog: render(model): popup is a RewordPopup
    Dialog-->>User: dialog prefilled with the commit's message

    User->>Dialog: edits the summary and submits
    Dialog->>Program: dispatch(RewordMsg.Confirmed(message))
    Program->>Update: confirmed(model, message)
    Update-->>Program: Next(model - popup, [CommitCmd.Reword(sha, message)])
    Program->>IO: io.execute(perform(env, cmd))

    IO->>Provider: reword(sha, message)
    Provider->>Git: git rev-parse --verify HEAD (is sha HEAD?)
    alt sha is HEAD
        Provider->>Git: git commit --amend --only -m ...
    else an older commit
        Provider->>Git: git rev-list --parents -n 1 sha
        Provider->>Git: git -c ... rebase --interactive ... sha^<br/>(GIT_SEQUENCE_EDITOR marks it "reword",<br/>GIT_EDITOR copies the new message in)
    end
    Git-->>Provider: exit code
    Provider-->>IO: Either of GitFailure or commands
    IO->>Program: ui.execute(dispatch(OperationMsg.Succeeded(reword, commands)))

    Program->>Update: OperationUpdate: Succeeded(reword, commands)
    Update-->>Program: Next(model + command log entry, [LoadStatus, LoadBranches, LoadStashes])
    Note over Program,Provider: the loads run on git-io. StatusLoaded sees HEAD moved,<br/>so LoadCommits re-reads the log with the new message
```

### Step by step

1. **Key press.**
   - `CommitKeys` registered `Binding.onSelected("r", "help.commits.reword", commit -> new RewordMsg.Requested(commit.sha()))`
     in the `COMMITS` scope.
   - `CommitsPanel` asks the `Keymap` to handle the key with the selected commit. That yields
     `RewordMsg.Requested(sha)`, which the panel dispatches. It names the commit, not its row, so a log that
     was read again meanwhile can't make it another one.

2. **Open the dialog**, a pure step in `RewordUpdate.requested`. If the commit is in the loaded log,
   `Update.ask` opens a `RewordPopup(commit)` and clears the error. No IO is needed.

3. **Render.**
   - `JazygitApp.render` asks each dialog to render. `CommitDialog.reword` renders because
     `model.popup(RewordPopup.class)` is defined, and starts out with the commit's current message.
   - `JazygitApp` moves the focus into the dialog.

4. **Confirm**, a pure step in `RewordUpdate.confirmed`.
   - A blank summary keeps the dialog open.
   - Otherwise `Update.confirm` closes the popup and returns one command: `CommitCmd.Reword(sha, message)`.

5. **Perform**, on the `git-io` thread. `Program.perform` calls `Reword.run(env)`, which `GitOperationCmd`
   implements for every operation done through git alone:

   ```java
   return perform(env.git()).fold(this::failedWith, this::succeeded);
   ```

   where `Reword.perform` is `git.reword(sha, message)`.

6. **The git work**, in `CliRebase.rewordAny`.
   - `CliLog.resolve(workTree, "HEAD")` runs `git rev-parse --verify --quiet HEAD^{commit}` to find out
     whether the commit is `HEAD`. If that fails, it's a `ProcessError`.
   - **The commit is `HEAD`:** `CliIndex.reword` runs `git commit --allow-empty --amend --only -m … [-m …]`.
     Its `ProcessError` is widened to a `GitError`.
   - **An older commit:** `CliRebase.reword` does the following.
     1. `TempFiles.using` writes the new message to a temporary file. If that fails, it's a
        `TempFileFailed`.
     2. `base` runs `git rev-list --parents -n 1 <sha>` to decide between `<sha>^` and `--root`.
     3. It runs `git rebase --interactive --autostash --keep-empty --rebase-merges <base>`, set up as
        follows.
        - `-c rebase.abbreviateCommands=false -c core.abbrev=no` makes the todo list name every commit as
          `pick <full sha>`, whatever the user's configuration says.
        - `GIT_SEQUENCE_EDITOR` is a shell function that turns `pick <sha>` into `reword <sha>`, and
          `merge -C` into `merge -c`, with plain `grep`, `sed` and `mv`. When the todo list has no such line,
          it fails, so the rebase stops instead of reporting success for a commit it never touched.
        - `GIT_EDITOR` is `cp <message file>`.
        - `-c commit.cleanup=whitespace` keeps lines starting with `#`.
     4. If the rebase stops on the conflicts of a later commit, it is left so, and the failure becomes a
        `StoppedOnConflicts` (see above). If it stops half-way otherwise, `Unfinished.runOrUndo` runs
        `git rebase --abort`, and that line joins the failure's command log. If the abort fails too, the
        failure records why. The repository is left as it was, unless it was already in the middle of a
        rebase: then git refused, and the user's rebase is left alone.

7. **Report.** The `Either` becomes one of two messages, which `OperationUpdate` handles:
   - **Success:** `OperationMsg.Succeeded(reword, commands)`. The commands are logged under
     `CommitCmd.REWORD`, the error is cleared and `Update.refresh` reads the repository again.
   - **Stopped on conflicts:** `OperationMsg.Stopped(reword, commands, notice)`. As for a success, but the
     command log shows the `Notice`, which `NoticeText` turns into text, and the status panel shows the
     rebase in progress.
   - **Failure:** `OperationMsg.Failed(reword, Failure.Git(error, commands, cleanupFailed))`. The commands
     that ran, including the abort, are logged; `Model.error` becomes
     `AppError.OperationFailed(REWORD, none, failure)`, which `ErrorText` shows as `"Reword failed: …"`;
     and the repository is read again.

8. **Refresh.** `Update.refresh` starts `LoadStatus`, `LoadBranches` and `LoadStashes`.
   - When `StatusLoaded` arrives, `LoadUpdate.statusLoaded` sees that `HEAD` moved (the reworded
     commit and everything after it have new shas) and issues `LoadCommits`.
   - When `CommitsLoaded` arrives, the commits pane shows the new message.

### The failure path

```mermaid
flowchart TD
    Run["Reword.run(env)"] --> Either{"git.reword(...)"}
    Either -- "Right(commands)" --> Done["OperationMsg.Succeeded(reword, commands)"]
    Either -- "Left(GitFailure(error, commands, cleanupFailed))" --> FailedWith["Cmd.failedWith(failure)"]
    FailedWith --> Failed["OperationMsg.Failed(reword, Failure.Git(...))"]
    Failed --> Log["OperationUpdate: CommandLog.append shows rebase, rebase --abort"]
    Log --> Update["error = AppError.OperationFailed(REWORD, ...)<br/>Update.refresh(model)"]
    Update --> Text["ErrorText.of(error) when rendered<br/>e.g. 'Reword failed: ' + git's explanation"]
    Done --> UpdateOk["OperationUpdate: command log += commands<br/>Update.refresh(model)"]
```

## Testing

- **Update functions** are pure, so most tests build a `Model` (`TestModels`), apply a `Msg` and
  assert on the next model and its commands. No git is involved.
- **`ProgramTest`** runs the real loop with queue executors and a fake provider (`StubGitProvider`
  gives every method a default).
- **The git layer** is tested against real temporary repositories: `CliGitInfoProviderTest`,
  `CliCheckoutTest`, `CliDiscardTest`, `CliDiffTest`, `CliMergeTest` and `GitProcessTest`. The parsers
  (`CliStatus.parse`, `CliBranches.parse`, `CliLog.parse`) are tested on fixed git output in
  `CliStatusTest` and `CliParseTest`.
- **UI tests** use TamboUI's `ToolkitTestRunner` and its pilot.
- **`ArchitectureTest`** checks the dependencies drawn under [Packages](#packages), and that updates are pure.
