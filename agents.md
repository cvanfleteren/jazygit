# Working on jazygit

jazygit is modern, functional Java: an Elm loop over immutable values (Vavr) on TamboUI. Prefer recursion, streams and
records to JavaBeans and loops. `ARCHITECTURE.md` maps the modules, the runtime loop and the anatomy of a feature: read
it before adding a feature or moving code between modules.

## Rules the build enforces

The ArchUnit rules in `jazygit-test-support` (`AppRules`, `CoreRules`, `ModelRules`) fail the build when broken; their
names and Javadoc are the exact rules. Write to them from the start:

- **Pure**: updates, messages, popups and the model return values, fail with values (`Either`, `Failure`) and build new
  values rather than filling collections or a `StringBuilder`. Reading the clock, the environment, the machine's
  defaults or randomness is IO, for a command; the model is told the time.
- **Values all the way down**: fields, static ones too, are final and hold immutable values. Collections are Vavr's and
  a missing value is `Option`; java.util appears only where a library's interface demands it (TamboUI, picocli).
  Classes are built of records and interfaces; none extends another.
- **Elm loop**: messages, commands and popups are records or enums. A feature's `XxxMsg` and its errors
  (`XxxError extends AppError`) are sealed, so its update switches without a `default`. An `XxxUpdate` is a final class
  of static functions. Updates make commands and only `Program` (and `StartApp`) runs them; views, keys and dialogs
  only send messages. A method reference (`Cmd::run`, `Msg::apply`, `Instant::now`) counts as a call.
- **Errors** say themselves as a `Phrase` (a message key and its arguments), and live in the feature's own error set,
  so a new error leaves `AppError` and `ErrorText` untouched.
- Commands and the core's public methods declare no exceptions: failures are values.

## Features are compile-time plugins

- A feature declares `XxxFeature.FEATURE` and is wired in by one line in `Features.ALL`, in the order its keys list in a
  panel; nothing outside its package names it (`FeaturesTest`, ADR 127). The native image needs this closed world.
- It lives in a group module: `changes`, `branches`, `github`, `forgejo` or `chrome` (ADR 136). A group uses only the
  groups below it (`branches` uses `changes`); the forges never use each other, so either can be deleted (ADR 171, 174).
- The kernel knows git, not forges (#588, `theKernelNamesNoForge`). Forge code talks to the rest through forge-neutral
  models such as `FailedBuild`: the forge's ids, URLs and commands travel through them only as opaque values the forge
  side writes, and what only the forge needs (a GitHub workflow run) stays in its feature (`FailedBuilds`).
- New feature state is the feature's own `Part` (ADR 157), kept out of the kernel's records.
- A feature reaches what its module may not depend on only through an extension point (ADR 159, 161): `Feature`'s
  builder methods (`showing`, `keeping`, `filling`, `screen`, `explaining`, `reacting`, …) and `Keymap.host`. Adding
  a feature starts with choosing its extension point. When none fits, add a general one rather than a special case,
  and enforce the boundary with module dependencies. Reference pattern: the failed-checks agent action consumes a
  neutral subject without knowing which forge produced it.
- A feature's dialogs and panes draw with its `widgets` package.

## Small rules

- Records keep their default `toString()`. Turn a wrapper record (`Branch.Name`, `Sha`) into a `String` with
  `.value()`: in concatenation, `formatted`, git arguments and `Messages.get(...)`, whose `Object...` compiles without it.
- An unused `case` binding is `_` (`case Outside _ when … ->`); elsewhere, check that Spotless formats `_` first.
- Nested types come after all methods (checkstyle's InnerTypeLast).
- A message key starts with the root of the module whose code names it (`github.dialog.…`) and lives in that module's
  `messages.properties`; a key several modules use goes in the lowest one they all see (ADR 136). `*BundleTest` catches
  a missing one.
- Main code decodes images without AWT: `ImageIO` and tamboui-image make the native image need extra `.so` files, and
  jazygit ships one file (ADR 84). Tests may use `ImageIO`.

## UI conventions

- An action that does not apply right now stays visible, disabled, with the reason, in its menu or dialog.
- Only the arrows cross into another panel; h/j/k/l move inside the focused one. Enter, Escape, Tab and clicks move
  focus too.
- Pane cursors and focus are view state, kept by the TamboUI widgets (ADR 2); the model holds what an update reads. The
  rich diff's cursor is the exception: updates act on it, so it lives in the model.
- A help text (`<root>.help.*.details`) is one sentence, third person ("Lists…", "Adds…"), under ~160 characters. Add a
  second only to warn of lost work or the like. Leave the popup's own choices, conflict and diff handling, and rare edge
  cases to the popup.

## TamboUI gotchas

- A style value set in code wins over CSS; CSS only fills properties the code leaves null. `DialogElement` sets a border
  type itself, so theme borders need `.borderType(null)` (`DialogFrame.themed`).
- Key routing: for the focused id, each element's `handleKeyEvent` runs before its `onKeyEvent` lambda, and global
  handlers come last. To pre-empt a key a text field swallows, register a wrapper under the same id first
  (`SubmittableTextArea`). Tab and Shift+Tab only move focus; a popup's "switch" key is a free Ctrl chord.
- `Actions.MOVE_UP/DOWN` match the arrows only, so a widget that wants j/k checks `event.isChar` itself.
- `RichTextAreaElement` ignores its horizontal scroll; `ScrollArea` (`content`) cuts the columns off the lines itself.
- A click focuses the clicked pane through the app's global mouse handler (`FocusKeeper.clicked`), since the router stops
  at the container that forwards the click.

## Navigating the code

- For questions about Java types, use JavaLens: `find_implementations` and `get_type_hierarchy` (feature messages reach
  `Msg` through a per-feature interface, so grep finds under a quarter of them), `get_call_hierarchy_incoming`,
  `analyze_change_impact`, `find_affected_tests`, and `rename_symbol` / `change_method_signature` for those changes.
  Set `maxResults` on broad queries: an unbounded `Msg` hierarchy is ~190 KB.
- grep suits distinctive names, strings, message keys and non-Java files.

## Tests

- Every behaviour has a unit test, in the module of what it tests. A test that needs a feature's messages or text, or
  the app's keymap (`Keymap.standard()` is empty without the app), goes in `jazygit-app`, in the package of what it
  tests.
- UI tests drive TamboUI's Pilot and assert on the rendered screen, not only on the dispatched message. Clicks are
  tested at app level (`JazygitAppReportsTest`), where the full layout routes them.
- The Python tests in `acceptance/` sanity-check the repository and output after a command (`acceptance/README.md`).
- Per-module JUnit configuration goes in that module's surefire `configurationParameters`. A module whose pom has the
  `test-jar` goal puts its `src/test/resources` on the test classpath of the modules above it.

## Before committing

1. `./mvnw verify`, as the last build: checkstyle only runs in verify, and Spotless reformats locally while CI only
   checks. Commit what it reformatted; after any later edit, run `./mvnw spotless:apply`.
2. After a UI change (pane layout, row text): `python3 -m pytest acceptance -q -n 8`, against the jar verify built. CI
   runs these; verify does not.
3. After a Markdown change: `markdownlint-cli2` (in the dev shell). Prose wraps at 120 columns; tables, code blocks,
   headings and long links may be wider.

## ARCHITECTURE.md and ADRs

- `ARCHITECTURE.md` is the shape of the code: modules, layers, the runtime loop, what a feature is made of. Update it
  when that shape changes; feature details belong in the code, its Javadoc and the ADRs.
- An ADR in `documentation/adr/NNNN-slug.md` (Context / Decision / Consequences) records each decision that shapes the
  architecture or the design, and each plan settled in a grilling session, before implementation starts. View tweaks,
  rearrangements and shortcut changes take none, even when an older ADR spelled out the old detail: change the code and
  Javadoc.
- Its number is the next one free on `origin/main` (after `git fetch`) *and* in the open PRs (`gh pr list`,
  `gh pr view N --json files`): parallel branches claim numbers all the time. Check again before opening the PR.
- ADRs on main are a historical record, immutable: a new ADR amends one, and the old one gains only a line such as
  "Amended by ADR NNNN: <why>".
