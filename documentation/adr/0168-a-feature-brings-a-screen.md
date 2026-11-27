# 168. A feature brings a screen

Date: 2026-11-21

## Context

The left of the window shows one of two screens: the repository (files, branches, commits, stash) or the pull requests
on GitHub. `Screen` is a kernel enum with exactly those two, and `other()` switches between them. The pull requests
screen is GitHub's, but all of it is in the kernel:

- its three panes, `PullRequestsPanel` in `kernel-ui.github`, and their scopes (`PR_REVIEW_REQUESTED`, `PR_AUTHORED`,
  `PR_REPOSITORY`) in `Scopes`, typed by `core.gh`'s `PullRequestSummary`;
- its state, `Model.prOverview` (`PrOverview`), and its messages, update and loads in `kernel-state.github.prs`;
- the preview of a pull request in the content panel, `PullRequestSource`, registered by `kernel-content` itself;
- its layout settings, `ui.pull-requests-width` and `ui.pull-requests-heights`, kernel keys;
- the symbols and texts of pull requests and checks (`PullRequestSymbols`, `CheckTimesText`, `CheckRows`,
  `ProgressBar`, `BasesField`), kept in `kernel-ui.github` because the screen draws them, and the help's legend of the
  branches, a list in `features-chrome` that names them.

Besides, the kernel asks "is it the pull requests screen?" in a dozen places: the status title (the unseen count), the
fetch line (when the pull requests were read), the pane a screen starts with, what the content panel mirrors, the
times that wake the redraw, the focus it remembers.

So a forge's screen, or any screen that is not the repository's, needs a kernel edit, and the kernel cannot leave
GitHub's state to `features-github` (ADR 167, step 6) while it draws GitHub's screen.

## Decision

- **Only the pull requests screen becomes a feature's.** The repository screen stays the kernel's own, with its panes
  as they are wired; making its panes features' is not needed yet.
- **A screen is an open value**, like `PaneId` and `Keymap.Scope`: an id, declared next to its owner.
  `Screen.REPOSITORY` is the kernel's. A feature adds a screen with `Feature.screen(...)`, a spec that gives:
  - its panes, in order, and their scopes, any number of them;
  - its default heights, and the setting keys its width and heights are kept under;
  - the pane it starts with;
  - a text after its name in the status title, if any;
  - its freshness line, for the fetch line;
  - what the content panel mirrors on it: the pane in use's selection;
  - the times it shows, which wake the redraw (ADR 124).
- **Switching is neutral**: `ScreenMsg.Shown(screen)`. `other()` goes; GitHub's `ctrl+p` (its own global key since
  #601) toggles between the repository screen and its own.
- **The layout keeps its file paths.** `ui.pull-requests-width` and `ui.pull-requests-heights` stay as they are in
  `config.toml`, so nothing needs migrating; their keys are the screen's, kept by `features-github` (`keeping`, ADR
  163). `Preferences` stays keyed by screen, now the open value; the repository screen keeps its keys in the kernel.
- **The screen's state is the feature's**: `PrOverview` becomes a `Part` of `features-github` (ADR 157), with
  `PrOverviewMsg`, `PrOverviewUpdate` and the loads of the pull requests. The kernel keeps, per screen, the pane its
  focus was on, so each screen gets its focus back when shown again.
- **The preview is a feature's content**: `PullRequestSource` moves to `features-github`, through `Feature.contents`
  (ADR 154).
- **What is left of the kernel's checks is about the repository screen**: `screen == Screen.REPOSITORY`, for what
  only it has (the stash's sizing, the worktree changes read while that tab shows). No kernel code names the pull
  requests screen.
- **The symbols leave the kernel with their last drawer**: `PullRequestSymbols`, `CheckTimesText`, `CheckRows`,
  `ProgressBar` and `BasesField` move to `features-github`, and `kernel-ui.github` is gone. The help's legend becomes
  a section features add per scope, as the settings panel's are (ADR 163): `features-chrome` keeps what git gives
  (gone, ahead, behind, worktree), and `features-github` adds the pull request, checks, review, merge and stack
  symbols to the branches' legend.

It is built as stacked pull requests:

1. `Screen` an open value, the screen spec with its hooks, and `ScreenMsg.Shown`; the pull requests screen is still
   declared in the kernel. This ADR.
2. The pull requests screen moves to `features-github`: its panes and scopes, `PrOverview` as a part with its messages,
   update and loads, `PullRequestSource`, and its layout keys, with the same paths.
3. The symbols and the help's legend section; `kernel-ui.github` goes.

## Consequences

- A second forge, or a screen that is no forge's, is a feature's `Feature.screen(...)`; the kernel is not edited.
- With no screen of GitHub's in the kernel, GitHub's state can move into `features-github` (ADR 167, step 6), and the
  architecture rule that `kernel..` imports nothing from `core.gh..` (ADR 167, step 7) can come after.
- Users' `config.toml` is read as before: the paths of the layout settings do not change.
- The help of the branches lists GitHub's symbols only when `features-github` is in the build.
- The repository screen's panes stay wired by the kernel and the app; if a feature ever needs to add one, that is its
  own decision.
- ADR 158 and ADR 167 get a line "Amended by ADR 168".

Amended by ADR 211: the features bring the panes of every screen, the repository's too, with `Feature.pane`, and
`ScreenSpec` no longer makes them.
