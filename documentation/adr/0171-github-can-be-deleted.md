# 171. GitHub can be deleted

Date: 2026-11-21

## Context

ADR 169 took GitHub's state out of the kernel, but left one exception to the rule that `kernel..` names nothing of
`core.gh..` (`AppRules.REVIEW_THREADS`): the review threads of the rich diff, which ADR 167 left for a decision of their
own. The kernel stores them in `CommitFilesView`, places them with `PlacedRemarks`, draws them with `RemarkLines`, puts
cursor stops on them and keeps a hunk with one shown, all on `core.gh`'s `ReviewThread`. `features-changes` loads them
with gh from `Env.gitHub`, and fails with `Failure.GitHub`, whose texts are the kernel's. `ReviewThread.Side` doubles as
the kernel's side of any diff, in a dozen files that have nothing to do with reviews.

Beyond the kernel, GitHub is named by the app (`RepositoryEnv` builds gh, its token and git's credentials; `Launcher`
the files of the unseen pull requests, the check timings and the collapsed threads; `Spinners` and `ShownClocks` ask
three of GitHub's dialogs), by `kernel-settings` (those three stores), by every feature module's pom (`core-gh`), and by
the tests of the other modules.

The goal is that GitHub's feature can be deleted: removing `features-github`, `core-gh` and GitHub's tests leaves an app
that builds and works, and a repository GitHub does not apply to shows nothing of it.

## Decision

- **GitHub is deleted by removing its modules and the lines that list them**: `features-github`, `core-gh` and
  `jazygit-github-app-tests`, their lines in the poms, and the line of `GitHubFeature.FEATURE` in `Features.ALL`, the only
  place in the code that names a feature statically. No module but those three names GitHub, in its main code or its
  tests.
- **The kernel has remarks of its own**: a `RemarkThread` of `Remark`s, forge-neutral, holding what placing and drawing
  use: an opaque id; the path and the one it had before; on the file or on lines; its line and side, and its start line
  and side; the line it was made on and whether it is outdated; resolved; how many more there are; and its remarks,
  each with an opaque id, its author, its time, its body in Markdown, whether it is hidden, and its reactions (the
  emoji, how many, and whether the user gave one). It holds nothing of GitHub's rights or ids. The kernel keeps placing
  (`PlacedRemarks`), drawing (`RemarkLines`, its theme classes and the `content.remarks.*` texts), collapsing, the cursor's
  stops (`Anchor.Thread`, `Anchor.Remark`, with ids of the kernel's) and which files are listed (all, unresolved, with
  remarks), with the `c` key that cycles that in `features-changes`, shown disabled when the change has no remarks.
- **The kernel has its own side of a diff**, `DiffSide` (OLD, NEW), in place of `ReviewThread.Side`.
- **A part states the remarks of a change, the kernel keeps no copy** (as ADR 169 does for the bases): GitHub's part
  keeps what it read (the remarks, their version, the pictures, the threads being sent, the remarks being deleted or
  reacted to), and a `Part` hook states, for the change shown, its remark threads, which are being sent and deleted,
  the pictures by their URL, and whether the change ended, and how (merged or closed), for the titles of `FilesPanel`.
  The rich diff, `FilesPanel`, the cursor and `Filtering` read the remarks from that hook. `CommitFilesView` keeps the
  generic fields, and what the kernel changes itself: what is listed and collapsed.
- **`Changes.OfPullRequest` stays the kernel's**, as the commits proposed to be merged, under the name it has.
- **The cursor keys of a diff with remarks are `Scopes.REMARKED_DIFF`**, in place of `PULL_REQUEST_DIFF`, chosen when
  a part states remarks for the change; GitHub binds its review keys on it, as before.
- **GitHub learns that the files of a change were opened through `KernelEvent.ChangesOpened(Changes)`**: its reaction
  reads the remarks and the collapsed threads of a pull request. Reading them again (`R`), the check of their version,
  reading them again once the pull request moved on, and the loads of the pictures leave `CommitFilesUpdate` for
  `features-github`.
- **A feature builds what it runs against**: a hook of `Feature`, given the repository's `Git`, the settings and their
  store, how ssh asks and the paths, gives the feature's services of the `Env` (ADR 169), and the credentials git runs
  with, as gh's token. It runs again when another repository is opened. `Env.gitHub` becomes a service of GitHub's, and
  `RepositoryEnv` names no forge.
- **GitHub's stores are GitHub's**: the unseen pull requests, the check timings and the collapsed threads move to
  `features-github`, on a generic store of `kernel-settings`, a table of a feature per repository with a codec of its
  own, so that TOML is still read only in `kernel.settings`. `Env.collapsed` goes; the kernel collapses, GitHub keeps
  what is collapsed between sessions.
- **`Failure` has an open kind that says itself as a phrase**, as `Notice.Phrased` (ADR 169): `Failure.GitHub`, the texts
  of `GhError`, `accountUnavailable()` and `gone()` move to `features-github`.
- **A feature declares what keeps the spinner going and the times that wake the redraw for its popups**, as a screen
  does (ADR 168), so `Spinners` and `ShownClocks` name no dialog of a feature.
- **A feature is on or off for a repository, as it decides**: GitHub is on with a remote on a GitHub host and gh found,
  read once the repository opens, in a part of its own, and on again once gh is found or set. While a feature is off,
  nothing of it shows or runs: no screen, badges, keys, help, legends, content, timers nor reactions. Its section of the
  settings still shows, so that gh's path can be set. While it is on, an action that does not apply shows disabled with
  why, as every action does.
- **GitHub's tests are GitHub's**: the tests of features-github stay in it; the tests of the whole app with GitHub in it
  move from `jazygit-app` to a module of their own, `jazygit-github-app-tests`, which depends on the app, as
  `features-github` cannot; the tests of the other modules use a test feature, or the kernel's remarks, in place of
  GitHub. `ReviewedDiffBenchmark` draws the kernel's remarks.
- **`core-gh` goes with features-github**: the poms of the features no longer give it to every module.
- **CI proves it**: a job runs `scripts/without-github.sh`, which deletes GitHub from a copy of the repository as above
  and runs `./mvnw verify` on what is left; and an acceptance test with no remote on GitHub checks that nothing of
  GitHub shows but its settings.
- **`AppRules.REVIEW_THREADS` goes**: `theKernelNamesNoForge` has no exception.

It is built in one step: only the end of it has to work.

## Consequences

- A build without GitHub is a build of jazygit, checked on every pull request.
- A second forge brings its remarks by stating `RemarkThread`s for a change; the kernel places and draws them.
- What the kernel draws of a remark is limited to what `RemarkThread` holds; a forge's own detail is drawn by its own
  popups and dialogs.
- The settings files keep their names and places, so nothing needs migrating.
- ADR 55, 60, 62, 63, 158 and 169 get a line "Amended by ADR 171".
