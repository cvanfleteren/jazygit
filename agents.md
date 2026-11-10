## code style
- prefer modern, functional java, preferring things like recursion, immutable collections and stream processing over
  things like javabeans and for loops

The ArchUnit tests (`*ArchitectureTest`) enforce these rules; a build that breaks one fails.

### The Elm loop
- Messages, commands and popups are values: records, or enums. Each feature's `XxxMsg` is a sealed interface, so its
  update switches over every message without a `default`.
- An `XxxUpdate` is a final class with a private constructor and only static methods and static fields: it is only
  functions.
- Updates make commands; only `Program` (and `StartApp`, for the start screen) runs them through `Cmd.run` or `perform`.
  A command may run another as part of itself.
- Views, keys and dialogs only send messages: they never construct a command or call `Msg.apply`. They may read a
  command as data.
- A method reference (`Cmd::run`, `Msg::apply`, `Instant::now`, `XxxCmd::new`) counts as a call.

### Pure code
The updates, messages, popups and the model are pure. Pure code:
- returns values: no `void` methods
- fails with a value, never by throwing
- never fills a mutable collection or a `StringBuilder`; it builds new values
- never reads the clock, the environment, the machine's defaults or chance: no `java.time` `now`,
  `System.currentTimeMillis`/`nanoTime`/`getenv`/`getProperty`, `ZoneId.systemDefault`, `Clock.system*`,
  `Locale`/`TimeZone.getDefault`, `Runtime`, `ProcessHandle`, `Random` or any other `RandomGenerator`,
  `UUID.randomUUID`. That is IO, for a command; the model is told the time.

### Values all the way down
- A field in state, feature, settings, cli and the git and gh models holds a value that never changes: a primitive,
  `String`, a boxed primitive, `Class`, `java.time`, `Path`, `FileTime`, `Pattern`, a function, an enum, a record, a
  Vavr type, or an interface of jazygit. Arrays, `Object`, `CharSequence`, java.util collections, `StringBuilder` and
  `java.util.concurrent` types are not. What a type is of counts too: `List<StringBuilder>` is no value, though `Object`
  there says no more than a type variable, and a function's or a `Class`'s type arguments are not looked at.
  (`Failure.Bug` keeps what a command threw, to report it.)
- Fields are final; static fields are final too, and never of a mutable type.
- Collections are Vavr's: no method takes or returns one of java.util's, but where a library's interface says so.
- A missing value is Vavr's `Option`, not `java.util.Optional`, but where a library's interface says so (TamboUI,
  picocli in `cli`).
- Classes are built of records and interfaces: no class in state, feature or the models extends another.
- Commands, and the public methods of the core, declare no exceptions, checked or unchecked, of any type: failures are
  values (`Either`, `Failure`).

### Small rules
- Records keep their default `toString()`. Where a wrapper record (`Branch.Name`, `Sha`) must become a `String`, call
  `.value()`: in concatenation, `formatted`, git arguments and `Messages.get(...)`, whose `Object...` arguments compile
  without it.
- An unused binding in a `case` label is `_` (`case Outside _ when … ->`). Elsewhere, let Spotless confirm it formats
  `_` before relying on it.
- Nested types come after all methods (checkstyle's InnerTypeLast).
- Main code decodes images without AWT: `ImageIO` and tamboui-image make the native image need extra `.so` files, and
  jazygit ships one file (ADR 84). Tests may use `ImageIO`.

## UI conventions
- An action that does not apply right now shows disabled, with the reason, in its menu or dialog; it stays visible.
- Of the movement keys, only the arrows cross into another panel; h/j/k/l scroll or move the cursor inside the focused
  panel. Enter, Escape, Tab and clicks move focus too.
- Pane cursors and focus are view state, kept by the TamboUI widgets; the model holds what an update reads (ADR 2). The
  rich diff's cursor is the exception: updates act on it, so it lives in the model.
- Help texts (`help.*.details`) are one sentence saying what the key does, in the third person ("Lists…", "Adds…"),
  under ~160 characters. Add a second sentence only for a warning that matters, such as lost work. Leave out what the
  popup's own choices explain, how conflicts or diffs are handled (that is the same everywhere), and rare edge cases.

## TamboUI gotchas
- A style value set in code wins over CSS; CSS only fills properties the code leaves null. `DialogElement` sets a border
  type itself, so theme borders need `.borderType(null)` (`DialogFrame.themed`).
- Key routing: for the focused id, each element's `handleKeyEvent` runs before its `onKeyEvent` lambda, and global
  handlers come last. To pre-empt a key a text field swallows, register a wrapper under the same id first
  (`SubmittableTextArea`). Tab and Shift+Tab only move focus; a popup's "switch" key is a free Ctrl chord.
- The standard binding set is in use: `Actions.MOVE_UP/DOWN` match the arrows only, so a widget that wants j/k checks
  `event.isChar` itself.
- `RichTextAreaElement` ignores its horizontal scroll; `ContentPanel` cuts the columns off the lines itself.
- A click focuses the clicked pane through the app's global mouse handler (`FocusKeeper.clicked`), since the router
  stops at the container that forwards the click.


## Process

### ADR

- Decisions with a big impact on the architecture or were impactful design decisions had to be made are recorded in an
  ADR (Architecture Decision Record) in `documentation/adr`. Each ADR is a markdown file with a name like `0001-foo.md`
  (Context / Decision / Consequences), where the number is the next available integer.
- The next number is the next free one on `origin/main` (after `git fetch`) *and* in the open PRs (`gh pr list`,
  `gh pr view N --json files`): parallel branches claim numbers all the time. Check again before opening the PR.
- A plan settled in a grilling session becomes an ADR before implementation starts.
- View tweaks, visual rearrangements and shortcut changes need no ADR, even when an older ADR spelled out the old
  detail: change the code and Javadoc.
- ADR's are immutable once they are in the main branch. They can be amended in a new ADR, but the old one is never
  changed apart from a reference to a newer one with a sentence like "Amended by ADR XX: this is why bla bla bla"
- The ADR's are a historical record of the decisions made, and the reasons for them. They are not a design document, and
  they are not a specification. They are a record of the decisions made, and the reasons for them.


## Testing

### Unit Tests

- all functionality needs to be covered by unit tests
- the acceptance tests are written in python. Their main focus in validating the state of the git repository after a
  command has been run, and that the output is as expected. They are not meant to be a complete test of the
  functionality, but rather a sanity check that the core is working as expected.
- integration tests are written in java, use a Pilot from Tamboui, and test the rendering and interaction of the UI.
- A rendering or scrolling test asserts on the rendered screen, not only on the message it dispatched. Click behaviour
  is tested at app level (`JazygitAppReportsTest`), where the full layout routes the click.
- Per-module JUnit configuration goes in that module's surefire `configurationParameters`. jazygit-git and jazygit-gh
  ship test-jars, so their `src/test/resources` lands on the app's test classpath.

### Before committing
1. `./mvnw verify`, as the last build: checkstyle only runs in verify, and Spotless reformats files locally while CI
   only checks them. Commit what it reformatted; after any later edit, run `./mvnw spotless:apply`.
2. After a UI change (pane layout, row text): `python3 -m pytest acceptance -q -n 8`, against the jar verify built. CI
   runs these; verify does not.
3. After a Markdown change: `markdownlint-cli2` (in the dev shell). Prose wraps at 120 columns; tables, code blocks,
   headings and long links may be wider.
