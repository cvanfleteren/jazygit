# 136. The application in modules

Date: 2026-11-15

## Context

`jazygit-app` holds everything above git and gh: about 67 000 lines of main code and as many of tests, in `state`,
`ui`, `feature`, `keys`, `settings`, `lens`, `image`, `i18n`, `cli` and the root package. Its layers are kept by
`ArchitectureTest` alone: the compiler lets `state` use TamboUI, and `ui` a feature, and only a test run says no.

The layers are there already. `state` imports neither TamboUI, `ui` nor a feature; `ui` imports no feature; nothing
imports the root package. The top-level features depend on each other one way: `commit` on `stage` and `ahead`,
`worktree` on `forcedelete` and `repos`, `branch` on `worktree`, `commit` and `repos`, `github` on `branch`, `diff`,
`fetch` and `worktree`, `settings` on `diff` and `theme`.

Two things cross them the wrong way:

- `keys` depends on `feature`, the one exception ADR 127 kept: `Keymap` makes a `HelpMsg.Requested`, `GlobalKeys`
  sends the messages of seven features, `Standard` folds `Features.ALL`. `ui` and every feature use `keys`.
- One bundle, `messages.properties`, holds the text of every feature, so nearly every change to a feature changes a
  file of the views. Its keys are grouped by kind (`dialog.*`, `help.*`, `error.*`), not by feature. A feature hands
  its keys as strings to the widgets of `ui` (a `ChoiceDialog`'s labels, a hint, an operation's `logTitleKey()`, an
  error's `Phrase`), which look them up with `Messages.get`.

## Decision

1. **The application is split in modules, in layers, with the features in groups on top.** Each module has one root
   package, which its name ends in:

   | Module | Root package | Holds | Depends on |
   |---|---|---|---|
   | `jazygit-lens` | `lens` | the lens | vavr |
   | `jazygit-image` | `image` | `Picture`, `Png`, `Gif`, `Raster` | — |
   | `jazygit-settings` | `settings` | the settings and their files | lens, night-config |
   | `jazygit-state` | `state` | the model, its messages, updates and commands | lens, image, settings, process, git, gh, json, yaml |
   | `jazygit-ui` | `ui` | the views, `Feature` and `FeatureAction` in the shell, `ui.keys` (`Keymap`, `Scopes`), `ui.i18n`, `MarkdownImage` in `ui.markdown` | state, syntax, TamboUI, commonmark |
   | `jazygit-changes` | `changes` | `stage`, `files`, `discard`, `commit`, `ahead`, `cherrypick`, `stash`, `edit`, `copy`, `diff`, `fetch`, `inprogress` | ui |
   | `jazygit-branches` | `branches` | `branch`, `worktree`, `forcedelete`, `repos`, `start` | changes |
   | `jazygit-github` | `github` | what was `feature.github` | branches, changes |
   | `jazygit-chrome` | `chrome` | `help`, `error`, `theme`, `layout`, `settings` (the panel) | changes |
   | `jazygit-app` | `app` | the root package, `app.cli`, `app.keys` (`GlobalKeys`, `Standard`, `Features`) | the four groups |

   A feature package keeps its name under its group's root: `feature.stage` becomes `changes.stage`,
   `feature.github.review` becomes `github.review`. The packages of the others keep theirs.
2. **`jazygit-features` gathers the four groups**: a pom that is both their aggregator and their parent, which gives
   them the modules below them once. Each group adds the groups it uses.
3. **The keymap names no feature.** `Keymap` is given the message that asks for the help of a scope, as a function
   of the scope and its actions, by whoever makes the standard keymap; it no longer makes a `HelpMsg` itself.
   `GlobalKeys`, `Standard` and `Features` move to `app.keys`. The panels of `ui` still ask for
   `Keymap.standard()`, which finds the keymap the app gives as a service, a `Keymap.Source` in
   `META-INF/services`; without the app, as in the tests of `ui`, it is an empty keymap. Not: handing the keymap to
   every panel that reads its keys, through every constructor between the app and them. This ends the exception
   ADR 127 kept (its decision 3).
4. **Each module has its own bundle**, `net/vanfleteren/jazygit/<root>/messages.properties`, named in its own
   native-image metadata. A key starts with the root of the module whose code holds it: `github.dialog.…`,
   `branches.help.…`. `Messages`, in `ui.i18n`, finds the bundle from that first part, and looks a key up in `ui`'s
   own bundle when that part names no module with a bundle, or one without the key: a few keys of the views start
   as a module does (`branches.gone`). A key that more than one module uses goes to the lowest that all of them see;
   one used by the views alone stays without a prefix. A key made at run time from another (help's `.details`, a
   dialog's `.title`) is where that other is. A test of each module checks that every key its code names is in its
   bundle, and one of the app that no key is in two; the tests of `ui` that show the text of a feature move to the
   app.
5. **The tests go with the code they test**, with three exceptions:
   - a test that needs what its module cannot see moves to `jazygit-app`, which sees everything, and keeps its
     package: the tests of `state` and `ui` that send the messages of a feature (as `ProgramTest` and
     `BranchesPanelTest`), those of a feature that use a group beside it (as `HelpDialogTest`), and those that read
     the keys of the standard keymap, which only the app has (as `CommitsPanelTest` and `KeymapTest`); 37 in all;
   - what tests of several modules share is in a test-jar of `jazygit-state` (`TestModels`, `CursorFeedback`), of
     `jazygit-ui` (`RenderedText`, `SettledPilot`, `ThemedBorders`) and of `jazygit-branches` (`CheckoutModels`,
     `NameAsked`, `LocationAsked`). A helper of `TestModels` that used a feature or a view moves next to it
     (`ErrorTexts` in `ui`, `CheckoutModels` in `branches`, `CommitModels` in `changes`). JUnit is configured through
     surefire's `configurationParameters`, never a `junit-platform.properties` that a test-jar would pass on;
   - the acceptance tests do not change: the fat jar is still built in `jazygit-app/target`.
6. **The rules follow the modules.** A rule the module graph now keeps is dropped (the settings, the lens, the command
   line and the state knowing nothing of what is above them). The others are `AppRules`, in `jazygit-test-support`,
   which each module of the application, from `jazygit-lens` up, runs on its own classes from its
   `*ArchitectureTest`, as the core runs `CoreRules`. They name the types of the state and of the core as text, as
   test-support is below them. The cycle rules of the parts of `state`, of `ui` and of a group of features are in that
   module's test. The keys the views declare and `MarkdownImage` count as no effect in an update, as when they were
   `keys` and `image`. `FeaturesTest` is in `jazygit-app`
   and looks at the four groups.

### Not

- Not: a module per feature. The features use each other; twenty modules that depend on each other in a web, each a
  test JVM more to start, for 28 000 lines in all.
- Not: modules cut through the layers, a `github` module with `state.github` and `ui.github` in it. The `Model` names
  the type of every part (ADR 107), so each part of the state stays below it. That needs a `Model` that features plug
  their parts into, which is a decision of its own.
- Not: the messages of the features moved down into `state`, so that the tests of `state` keep sending them. A feature
  owns its messages, its errors and its settings.
- Not: the bundles of the modules registered with `Messages` by the app when it starts, or found as services. The
  first is state set once, before any text is looked up, in tests too; the second is a service in each module, for
  what the name of a key says already.

## Consequences

- The compiler keeps the layers: `state` cannot see TamboUI, `ui` no feature, `changes` neither `branches` nor
  `github`.
- A feature changes its own module, and `jazygit-state` when the model gets a part, `jazygit-ui` when it draws
  something new, git or gh for an operation of theirs, and `jazygit-app` for a new top-level feature or a global key.
- A pull request that changes only a group of features leaves the modules below it to the build cache; one that
  changes `state` or `ui` builds and tests everything above them.
- Each module with tests starts its own test JVMs. With `-T1C` the groups that do not use each other build at the
  same time.
- About 1 100 keys of text are renamed, and the code that makes keys at run time with them.
- Members used across a module are public, as they were across a package.
- `flake.nix`, `run.sh` and `jazygit-benchmarks` name the new modules.
- This amends ADR 107: the parts of the state are still one module; and ADR 127, decision 3.
