# Plan: GitHub can be deleted (ADR 171)

The implementation plan for [ADR 171](../adr/0171-github-can-be-deleted.md). Read the ADR first: it is the decision;
this file is how to build it. It was written from the code at `0f22717` (main, 2026-11-21). Line numbers and counts
are from then.

The work is **one pull request**. Only its end has to work, but keep it compiling at the end of each phase below, and
commit per phase: it is the only way to find where a test broke. Delete this file in the last commit of the PR.

## The goal, as a check

The PR is done when all of these hold:

1. `scripts/without-github.sh` passes. It deletes `jazygit-features/features-github`, `jazygit-core/core-gh` and
   `jazygit-github-app-tests` from a copy of the repository, removes their lines from the poms and the
   `GitHubFeature.FEATURE` line (and its import) from `jazygit-app/.../app/keys/Features.java`, and runs
   `./mvnw verify` on what is left. It runs in CI as a job of `.github/workflows/build.yml`.
2. `env -u GH_TOKEN ./mvnw verify` passes on the full repository, with `AppRules.theKernelNamesNoForge` and no
   `AppRules.REVIEW_THREADS`.
3. `python -m pytest acceptance -n 8` passes against the jar `verify` built, including a new test: in a repository with
   no remote on GitHub, nothing of GitHub shows (no `ctrl+p`, no badges, no GitHub keys in the help `?`), except its
   section in the settings panel.
4. `nix build` passes (it checks the native image and `mvnHash`).

## Conventions of this repository (read before starting)

- Work in a git worktree. In a worktree `direnv` is blocked: run Maven as
  `nix develop <worktree> -c ./mvnw ...`, with `GH_TOKEN` unset (`env -u GH_TOKEN`), as `GhTokenTest` needs that.
- Run `verify`, not `test`: checkstyle only runs in `verify`. With `-pl`, add `-am`. Spotless *applies* formatting
  locally (CI only checks), so make no edits after the last local `verify`, or run it again.
- Functional style, immutable data, Elm-like architecture: `Msg` applies itself through its feature's `XxxUpdate`
  (static pure functions); a `Cmd` runs against an `Env` and returns a `Msg`. `ARCHITECTURE.md` and `AppRules`
  (`jazygit-test-support`) are the rules; ArchUnit runs them in every module.
- No `toString()` overrides on records. Unused-case bindings may be `_`.
- Texts: each module has a bundle; its keys start with the module's root (`github.…`, `content.…`); `Bundles`-tests
  check that every key the code names exists. A key moved to another module is renamed to that module's root.
- ADRs are immutable: only add a line "Amended by ADR 171" at the end of ADR 55, 60, 62, 63, 158 and 169.
- Test placement: tests that need feature texts/keys or `Keymap.standard()` belong in the app (or, for GitHub,
  in `jazygit-github-app-tests`).
- `git status` before staging; never stage unpacked jars or `target/`.
- Open the PR ready for review (not draft). Use `/address-review` for the bot's review.

## What is there today (facts)

- `features-github` has GitHub's state (`features.github.state`), screens, stack, checks, builds, and the review
  actions (`features.github.review`: 21 files, keys on `Scopes.PULL_REQUEST_DIFF`). The kernel *stores, places and
  draws* the review threads, on `core.gh` types.
- Review data path: `features-changes` `diff/CommitFilesCmd.LoadReviewRemarks` and `LoadPicture`
  (`env.gitHub()`) → `CommitFilesMsg.RemarksLoaded`/`PictureLoaded` → `CommitFilesUpdate.remarksRead`, `moved()`,
  `pictures()` → `kernel-state` `view/CommitFilesView` (fields `remarks`, `listed`, `collapsed`, `sending`,
  `deleting`, `reacting`, `version`, `pictures`) → `kernel-content` `CommitFilesSource.remarks()` → `ShownRemarks` →
  `DiffBody` → `RichDiff` → `state/diff/PlacedRemarks.place` → `FileSections`/`UnifiedHunk`/`SplitHunk` → `Blocks` →
  `RemarkLines`. Also `Model.filtering()` (keeps hunks with threads), `state/diff/DiffStops`, `Anchor`, `Collapsed`,
  `kernel-content` `DiffCursor`, and `kernel-ui` `files/FilesPanel` (per-file counts, merged/closed titles, listed).
- `ReviewThread.Side` is used as the generic old/new side in ~12 kernel files that have nothing to do with reviews
  (`AreaFit`, `AreaKeys`, `AreaLines`, `AreaSearch`, `LineGoal`, `Marked`, `Drawn`, `DiffLines`, `Version`, …).
- Placement uses only `path` (+ rename origin), `subject` (FILE/LINE), `line`, `side`, `startLine`, `startSide`;
  drawing uses `originalLine`, `outdated`, `resolved`, `more`, and per comment `author`, `at`, `body`, `hidden`, `id`,
  `reactions`.
- The app names GitHub in: `RepositoryEnv` (gh, `GhToken`, git's credentials from the token, `GitHubStores`,
  `GitHubAccount.ON_FORGE`), `RepositorySession` (imports `features.github.state.*`), `Spinners` and `ShownClocks`
  (`ChecksDialog`, `CommentsDialog`, `PullRequestDialog`), `Launcher` (`UnseenPullRequestsFile`,
  `CheckTimingsFile`, `CollapsedFile`), `keys/Features` (`GitHubFeature.FEATURE`, which stays).
- Every `Features.*` value is static (`Standard`, `StandardContent`, `StandardBadges`, `StandardGroups`,
  `StandardScreens`, `StandardLegends`, `StandardSettings`, `Timers`, `Popups`, `Features.REACTIONS`), loaded by the
  kernel through `META-INF/services` providers. `Keymap.handle` and `Binding` never see the `Model`. The help
  (`features-chrome` `help/HelpDialog`) renders with the `Model`. Popups, content sources, badges, groupings,
  reactions, screens' rendering and `ShownClocks`/`Spinners` all run with the `Model`. Timers are scheduled once
  (`app/Timers.start`) and dispatch their `Msg` blindly. Nothing today computes "this repository has a GitHub remote";
  `GhAccount.remoteHost(urls)` can, `GitHubAccount.host` defaults to github.com. "gh is missing" is
  `GhError.GhUnavailable`; `GitHubSettingsCmd` probes `account().version(executable)`.
- `kernel-settings` holds `UnseenPullRequests*`, `CheckTimings*`, `Collapsed*` stores (TOML through nightconfig,
  which `AppRules.onlyTheSettingsReadToml` keeps in `kernel.settings`), and `FormerState`, which deletes what ADR 63
  stopped keeping (names `UnseenPullRequestsFile.FORMER_KEY`).
- `StubGitHub` is in `core-gh`'s test-jar; `TestModels` (kernel-state test-jar) and `TestApp` (app tests) build
  `new Env(git, StubGitHub.of())`. `jazygit-features/pom.xml` gives `core-gh` (and its test-jar) to every feature module;
  kernel-state, kernel-content, kernel-ui and the app depend on it too.
- `Env` has ~117 constructor calls in ~56 files, most `new Env(git, StubGitHub.of())`.

## Phase 1 — The kernel's own side of a diff

1. Add `kernel-state` `state/diff/DiffSide` (`OLD`, `NEW`).
2. Replace `ReviewThread.Side` by `DiffSide` in every kernel file and test that uses it only as a side of a diff
   (list above, plus `Anchor.OnLine`, `Version`, `DiffLines`, kernel-content `diff/*`, kernel-ui `content/Drawn`),
   and in `features-changes` (`edit/EditUpdate`, and the tests that use `Side`: `BlameUpdateTest`, `FoldUpdateTest`,
   `RenderUpdateTest`, `VersionUpdateTest`, `CopyUpdateTest`, `OpenFileUpdateTest`).
3. Where GitHub's `Side` meets the kernel (features-github `review/*`: `ReviewUpdate`, comment positions), map
   LEFT↔OLD, RIGHT↔NEW in one place (a small `FromGitHub`-style helper in `features.github.remarks`, see phase 2).

## Phase 2 — Remarks are the kernel's

**Kernel types** (`kernel-state` `state/diff/`, or a new `state/remarks/` slice — keep `StateArchitectureTest`'s
"slices free of cycles"):

- `RemarkThread(Id id, String path, Option<String> origin, Subject subject, Option<Integer> line, DiffSide side,
  Option<Integer> startLine, Option<DiffSide> startSide, Option<Integer> originalLine, boolean outdated,
  boolean resolved, int more, List<Remark> remarks)`, `Subject` FILE/LINE, `Id(String)`.
- `Remark(Id id, String author, Instant at, String body, boolean hidden, List<Reaction> reactions)`,
  `Remark.Id(String)`, `Reaction(String emoji, int count, boolean given)`.
- `StatedRemarks(Loadable<List<RemarkThread>> threads, boolean atItsCommits, Set<RemarkThread.Id> sending,
  Set<Remark.Id> deleting, Map<String, Option<Picture>> pictures, Option<Ended> ended)`, `Ended` MERGED/CLOSED.
  This replaces `ShownRemarks`' inputs and `CommitFilesView.ended()`. (`Picture` is `lib-image`; the kernel already
  draws pictures.)
- A `ThreadsByPath`-equivalent for the kernel (rename-aware by `origin`), used by `PlacedRemarks` and `FilesPanel`.

**Hook** (`Part.java`, next to `stating`/`naming` from ADR 169): `Part.remarking(Remarking<S>)`,
`Option<StatedRemarks> remarks(S value, Changes changes)`; `Parts.remarks(Changes)` returns the first kept part that
states remarks for that change. Add `Model.remarks()` (for the commit files view shown) as the one place the kernel
reads them.

**Kernel users switch to the hook and kernel types**: `PlacedRemarks`, `Anchor` (`Thread(RemarkThread.Id, …)`,
`Comment` → `Remark(Remark.Id, Thread)`), `Collapsed` (keys by the kernel ids), `DiffStops`, `Filtering`,
`Model.filtering()`, kernel-content `CommitFilesSource`, `ShownRemarks`, `RemarkLines` (reactions from
`Remark.Reaction`; `RemarkLines` must not need `CommentRights`), `Blocks`, `SplitHunk`, `RichDiff`, `ContentText`,
`DiffCursor`, `DiffBody` (`cursorKeys()` → `Scopes.REMARKED_DIFF` when remarks are stated), kernel-ui `FilesPanel`
(counts, `listedTitle`, merged/closed from `StatedRemarks.ended`).

**`CommitFilesView`** keeps only the generic fields plus `listed` and `collapsed` (the kernel changes those). It loses
`remarks`, `sending`, `deleting`, `reacting`, `version`, `pictures`, `pullRequest()`, `threads()`, `withoutComment`,
`withEdited`, `withReacted`, `withComment`, `remarksAtItsCommits`, `ended()`. `listedFiles` and `Listed.keeps` take
the stated threads. The `c` binding (`ListedCycled`) stays in features-changes `CommitFilesAction`; its action is
`Option.none()` (shown disabled in the help) when no remarks are stated. `Changes.OfPullRequest` stays as it is.

**Scope**: rename `Scopes.PULL_REQUEST_DIFF` to `Scopes.REMARKED_DIFF` (id `remarkedDiff`, title key renamed in the
ui bundle). Update every binding on it (features-github `review/ReviewAction`; features-changes `CopyAction`,
`ContentSearchAction`, `FoldAction`, `FileStepAction`, `GoToLineAction`).

**Kernel event**: `KernelEvent.ChangesOpened(Changes changes)`, raised where `CommitFilesUpdate.open` opens a view
(and in `moved()`'s replacement). Keep the kernel naming no forge in the event's Javadoc.

**GitHub side** (new package `features.github.remarks`, with its own `Msg`/`Update`/`Cmd` as the rules want):

- A part `RemarksState` (name `github.remarks`), by `PrNumber`: what was read (`Loadable<ReviewRemarks>`), its version,
  the pictures, sending/deleting/reacting. It `remarking`s: for `Changes.OfPullRequest(number…)` it maps `ReviewRemarks`
  → `StatedRemarks` (threads via the mapper, `atItsCommits` from what `CommitFilesView.remarksAtItsCommits` does now,
  `ended` from `PullRequestDetails.State`).
- Move from features-changes: `CommitFilesCmd.LoadReviewRemarks` (and `.after`, used by `ReviewUpdate`,
  `ReviewLookup`, `VerdictUpdate`), `LoadPicture`, `LoadCollapsed`/`SaveCollapsed`, `CommitFilesMsg.RemarksLoaded`,
  `RemarksFailed`, `PictureLoaded`, `RemarksRefreshed`, `CollapsedLoaded`, `CollapsedKept`, and from
  `CommitFilesUpdate`: `remarksRead`, `moved()`, `pictures()` (`MAX_PICTURES`), `remarksLoad`, `collapsedLoad`.
  GitHub's reaction answers `ChangesOpened` for an `OfPullRequest` by asking for the remarks and the collapsed
  threads. The `R` binding moves to features-github on `Scopes.COMMIT_FILES` and `REMARKED_DIFF`. `moved()` re-opens
  the view through a features-changes message (`CommitFilesMsg.Opened`) as `PullRequestUpdate.files()` does.
- `ReviewLookup`, `OnThread`, `ThreadUpdate`, `ReactUpdate`, `DeleteComment*`, `ReviewUpdate`, `EditUpdate`'s callers,
  `VerdictUpdate.fromRemarks`, `BrowseRemarkCmd` read GitHub's `ReviewRemarks` from `RemarksState`, and map the
  kernel's anchor ids back to GitHub's (`ReviewThread.Id` ↔ `RemarkThread.Id` by value).
- The mapper `ReviewThread` → `RemarkThread`: GitHub node ids as the opaque ids; `LEFT`/`RIGHT` → `OLD`/`NEW`;
  `Reactions` → `Remark.Reaction` with the emoji text the kernel draws today.

## Phase 3 — Failure says itself

- `Failure`: add `non-sealed interface Phrased extends Failure { Phrase phrase(); }` (as `Notice.Phrased`,
  ADR 169). `ErrorText.of(Failure)` renders it with `ErrorText.of(Phrase)`.
- Move `Failure.GitHub`, `Failure.of(GhFailure)`, `Failure.of(GhError)`, `accountUnavailable()`, `gone()` to a
  features-github sealed `GitHubFailure extends Failure.Phrased` (record with `GhError error`, `commands`,
  `cleanupFailed`). Its phrase replaces `ErrorText.of(GhError)`; the `gh.*` texts move from the ui bundle to the
  github bundle as `github.gh.*`. Callers: 53 uses in 34 features-github files (`Failure::of` on a `GhFailure`,
  `new Failure.GitHub(`, `Failure.GitHub::new`, `.gone()`, `.accountUnavailable()`); `ErrorText.of(GhError)` is
  called from features-github dialogs and the app `Launcher` — give GitHub a `GitHubFailure.text(...)` instead.

## Phase 4 — A feature builds what it runs against

- `Feature` gets `running(Running running)`: `Running` is `Feature.Running` (kernel-ui) taking a `RepositoryOpening`
  (`Git git`, `Settings settings`, `SettingsStore store`, `SshAsking ssh`, `Path repositories`, `Path cacheDir`) and
  giving `Contribution(Env.Services services, Option<Supplier<Map<String, String>>> gitCredentials)`.
- `RepositoryEnv.of`/`reopen` (app) run every feature's `running` (`Features.ALL`), merge the services, and wrap git
  with `CliGit.withCredentials` over the merged credentials (when any); it names no forge. `Launcher` no longer builds
  any store but the settings and the recent repositories.
- `Env` loses `gitHub` and `collapsed`: canonical `Env(Git git, SettingsStore settings, RecentRepositories recent,
  Repositories repositories, Terminal terminal, Services services)`, and the short constructors without `GitHub`
  (`Env(Git)`, `Env(Git, SettingsStore)`, …). The ~117 calls drop the `StubGitHub.of()` argument. features-github gets
  `GitHubServices.GH` (`Env.Service<GitHub>`, none = a GitHub that fails every call with `GhUnavailable`) and its 48
  `env.gitHub()` calls become `env.service(GitHubServices.GH)` (or a small `GitHubServices.of(env)`). Its tests use a
  helper `GitHubEnvs.with(env, StubGitHub…)` in features-github's test sources.
- GitHub's `running` builds what `RepositoryEnv.of` builds now: `GhToken`, `CliGitHub.of(...)` (with
  `GitHubSettings.gh(settings)`, `GitHubAccount.host`, the browsers from the store, ssh, cacheDir), the stores, and
  `Env.FORGE_ACCOUNT` → `GitHubAccount.ON_FORGE`; its credentials are the token's environment.
- **Generic per-repository store** in `kernel-settings`: `RepositoryTable<T>` (a file name in the repository's
  directory under `SettingsPaths.repositories()`, and a `Codec<T>`/`TableCodec` of the existing kind), with
  `load(repository): Option<T>` and `change(repository, UnaryOperator<T>, T empty): Try<T>`. Same file format and
  names as today (`unseen-pull-requests.toml`, `check-timings.toml`, the collapsed file) — nothing migrates.
  `UnseenPullRequestsFile`, `CheckTimingsFile`, `CollapsedFile` and their stores/values move to features-github on top
  of it (their tests too: `UnseenPullRequestsFileTest`, `CheckTimingsFileTest`, `CollapsedFileTest`).
  `FormerState`'s GitHub part (the former unseen key, the former check-timings file) moves to GitHub's `running`.

## Phase 5 — Spinners and clocks through the feature

- `Feature` gets `spinning(Predicate<Model>)` and `clocking(Function<Model, List<ShownTime>>)`. `Spinners.shown` and
  `ShownClocks.next` fold `Features.ALL` instead of naming `ChecksDialog`, `CommentsDialog`, `PullRequestDialog`;
  GitHub registers those three dialogs' `showsSpinner`/`shownTimes`. (Kernel parts like `BranchesPanel`,
  `FetchLine`, `ContentView`, `FilesPanel` stay as they are.)

## Phase 6 — A feature is on or off for a repository

Design inside the ADR's decision (adapt if the code says otherwise, but keep the contract):

- `Feature` gets an `id` (`"github"`) and an optional detection: `Feature.applying(Detect detect)`, where `Detect` gives
  the `Cmd` that answers whether the feature applies to the repository (`FeatureMsg.Applies(id, boolean)`).
  A feature without detection is always on.
- The `Model` holds which features are on (`Model.on(String id)`; default: a feature with detection is off until it
  answers). On `RepositoryOpened` the kernel launches every detection. `FeatureMsg.Applies` sets it and raises
  `KernelEvent.Switched(id, on)`; GitHub's reaction loads the pull requests on `Switched("github", true)` instead of
  on `RepositoryOpened`.
- GitHub's detection: a remote on a GitHub host (`GhAccount.remoteHost` over `git.remotes().urls()`, *without* the
  github.com default) and gh found (`account().version(executable)` succeeds). Setting gh's executable in the settings
  panel (`GitHubSettingsCmd`) asks for the detection again.
- Gate every contribution of a feature that is off:
  - **keys**: tag each `Binding`/`Global` with the id of the feature that added it (in `Feature.keys`, e.g. a
    `Keymap.owned(id, …)` fold step). `Keymap.handle`/`handleGlobal` and `actions` take the ids that are off (the
    views and `JazygitApp.handleGlobalKey` have the model); the help (`HelpDialog.render`) leaves those bindings out.
  - **popups, content sources, badges, groupings, legends, screens' titles and switching** (`ScreenUpdate` refuses a
    screen of a feature that is off): wrap them in the `Standard*` providers and `Popups` with `model.on(id)`.
  - **timers**: `Timers` dispatches `FeatureMsg.Timed(id, msg)`, which applies `msg` only while the feature is on.
  - **reactions**: `Reactions` keeps the owner of each reaction and folds only those that are on (`started` too).
  - **spinners, clocks**: only while on.
  - **settings sections**: always shown.
- Inside a feature that is on, actions that don't apply keep showing disabled (`Option.none()` → struck through).

## Phase 7 — Modules and tests

- Poms: remove `core-gh` (and its test-jar) from `jazygit-features/pom.xml`, `kernel-state`, `kernel-content`,
  `kernel-ui` and `jazygit-app`; add them to `features-github`'s pom. The app keeps `features-github` (compile) only.
- New module `jazygit-github-app-tests` (listed in the root pom), test code only, depending on `jazygit-app`,
  the app's test-jar (add a `test-jar` goal to the app: `TestApp`, `ContentViews` and the other helpers),
  `features-github` and its test-jar, `core-gh`'s test-jar.
- Move the tests (inventory at `0f22717`; "C+A" files keep their kernel parts in place and move only their GitHub
  tests):
  - **to `jazygit-github-app-tests`** (whole app with GitHub): `app/JazygitAppBuildsTest`,
    `app/JazygitAppPullRequestsTest`, `app/JazygitAppWiringTest`, `app/keys/StandardBadgesTest`,
    `StandardLegendsTest`, `StandardScreensTest`, `StandardSettingsTest`, `features/chrome/settings/SettingsPanelTest`
    (its GitHub section), `kernel/content/ContentPanelTest`, `ContentViewTest`, `PullRequestContentTest`,
    `features/github/builds/content/BuildsEveryBranchTest`, `BuildsWorkflowsTest`, `BuildsFixtures`,
    `features/github/review/ReviewThreadTimesTest`; and the GitHub tests of the C+A files: `ShownClocksTest`,
    `SpinnersTest`, `keys/FeatureReactionsTest`, `keys/KeymapTest` (ctrl+p, shift+O, `i`),
    `keys/SettingsRoundTripTest`, `keys/StandardContentTest`, `kernel/content/ContentSearchTest` (builds),
    `kernel/state/OperationUpdateTest` (GitHub reads again), `ProgramTest` (GitHub in the background),
    `kernel/ui/FetchLineTest` (PRs read), `StatusPanelTest` (new PRs counted).
  - **to `features-github`** (feature level): `features/github/builds/content/BuildsContentTest`, `BuildsPickTest`,
    `features/github/stack/StackUpdateTest`, `features/github/state/CheckTimesUpdateTest`,
    `kernel/state/BackgroundCmdTest` (its GitHub commands), `kernel/state/RemoteRefsGuardTest` (the PR checkout),
    `kernel/ui/branches/StackRowsTest` (or to the app-tests module if it needs the app), the remark-loading tests of
    features-changes `diff/CommitFilesUpdateTest` (R, version check, pictures, moved).
  - **rewrite in place** without gh (drop `StubGitHub` from the `Env`, use `DiffSide`, kernel `RemarkThread`
    fixtures, a test part that states remarks, or a test feature): `TestModels`, `TestApp`; app
    `BorderDragTest`, `JazygitAppAgentsTest`, `JazygitAppFileStepTest`, `JazygitAppReportsTest`,
    `kernel/content/AreaStateTest`, `UnchangedContentTest`, `RichDiffContentTest`, `FileDiffContentTest`,
    `GoToLineTest`, `FilesContentViewTest`, `kernel/state/IndexRefreshUpdateTest`, `LeftPanesTest`,
    `WorktreeChangesTest`, `kernel/ui/CommandLogPanelTest` (use a non-GitHub command), `files/FilesPanelTest`;
    kernel-content `DiffBodyTest`, `diff/*Test` (10); kernel-state `BaseDistanceUpdateTest`, `load/AheadUpdateTest`,
    `ReadBlameTest`, `ReadWholeFileTest`, `preferences/SettingsUpdateTest`, `diff/DiffLinesTest`, `VersionTest`,
    `CollapsedTest`, `DiffStopsTest`, `PlacedRemarksTest`, `view/CommitFilesViewTest`; features-changes (12),
    features-branches (11, all only `StubGitHub` in an `Env`), features-chrome `SettingsPanelUpdateTest`,
    `ThemeCmdTest`; features-agent `InvestigateCmdTest`, and `AgentArchitectureTest` (drop the `core.gh`/
    `features.github` packages from its rule if they no longer exist on its classpath; keep the intent: the agent
    knows no forge).
  - Screens and the PR screen in app tests that only need "a second screen" (`BorderDragTest`, `LeftPanesTest`):
    use a test `ScreenSpec`.
- `jazygit-benchmarks` `ReviewedDiffBenchmark`: build `RemarkThread`s.
- `jazygit-test-support`: `ModelRules` (5 rules list `..core.gh.model..`) — keep them as "values" rules, but they must
  not need gh to compile (they name packages as text, which is fine); `AppRules`: remove `REVIEW_THREADS`, make
  `theKernelNamesNoForge` strict; keep the package lists of `features.github` (text) as they are harmless without the
  module. Consider adding `AppRules.onlyGitHubNamesGitHub`: no class outside `..features.github..`, `..core.gh..` and
  `..app.keys.Features` depends on them (it holds in every module).

## Phase 8 — Proof and documentation

- `scripts/without-github.sh` (see "The goal"); CI job `without-github` in `build.yml` running it with Java from the
  same setup as `verify`. Keep it idempotent and working on a copy (`git worktree add` into `$RUNNER_TEMP`, or `cp -r`
  without `target/`).
- Acceptance test (`acceptance/`, Python): a repository without a GitHub remote → no `ctrl+p` screen title, no badge,
  no GitHub key in `?` for the branches pane, the GitHub section in the settings panel; and, if the harness allows,
  gh missing (`fake_gh.py` absent from `PATH`) with a GitHub remote → the same.
- `ARCHITECTURE.md`: the kernel names no forge without exception; remarks are the kernel's; the feature hooks
  (`running`, `remarking`, `spinning`, `clocking`, `applying`); `jazygit-github-app-tests`; deleting GitHub.
- "Amended by ADR 171" on ADR 55, 60, 62, 63, 158, 169.
- Delete this plan.

## Risks and things to watch

- **Behaviour must not change** for a GitHub repository: the acceptance suite (`fake_gh.py`) is the safety net; run
  it after phases 2, 4 and 6.
- **Read ids (ADR 119)** are given in `GitHubReactions.REACTION.started`; keep `GitHubReactionsTest` and
  `UpdateTest`'s stamping test green.
- **Detection latency**: until GitHub answers, nothing of it shows; the first frame of a GitHub repository is
  briefly without badges. Acceptable; don't block the open on it.
- **Key ownership**: a binding a feature adds on a kernel scope (`REMARKED_DIFF`, `BRANCHES`) must carry the
  feature's id, or it would stay active while the feature is off.
- **Settings file keys** (`github.executable`, `github.browsers`, `ui.pull-requests-*`) are GitHub's already
  (ADR 163, 168): with the module deleted they are keys jazygit does not know, which `SettingsFile` keeps as they are
  when it saves (checked). Nothing to do but not to break that.
- **`nix build`** after the pom changes (memory: a stale `mvnHash` doesn't heal itself).
