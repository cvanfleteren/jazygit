## code style
- prefer modern, functional java, preferring things like recursion, immutable collections and stream processing over things like javabeans and for loops

The ArchUnit tests (`*ArchitectureTest`) enforce these rules; a build that breaks one fails.

### The Elm loop
- Messages, commands and popups are values: records, or enums. Each feature's `XxxMsg` is a sealed interface, so its update switches over every message without a `default`.
- An `XxxUpdate` is a final class with a private constructor and only static methods and static fields: it is only functions.
- Updates make commands; only `Program` (and `StartApp`, for the start screen) runs them through `Cmd.run` or `perform`. A command may run another as part of itself.
- Views, keys and dialogs only send messages: they never construct a command or call `Msg.apply`. They may read a command as data.
- A method reference (`Cmd::run`, `Msg::apply`, `Instant::now`, `XxxCmd::new`) counts as a call.

### Pure code
The updates, messages, popups and the model are pure. Pure code:
- returns values: no `void` methods
- fails with a value, never by throwing
- never fills a mutable collection or a `StringBuilder`; it builds new values
- never reads the clock, the environment, the machine's defaults or chance: no `java.time` `now`, `System.currentTimeMillis`/`nanoTime`/`getenv`/`getProperty`, `ZoneId.systemDefault`, `Clock.system*`, `Locale`/`TimeZone.getDefault`, `Runtime`, `ProcessHandle`, `Random` or any other `RandomGenerator`, `UUID.randomUUID`. That is IO, for a command; the model is told the time.

### Values all the way down
- A field in state, feature, settings, cli and the git and gh models holds a value that never changes: a primitive, `String`, a boxed primitive, `Class`, `java.time`, `Path`, `FileTime`, `Pattern`, a function, an enum, a record, a Vavr type, or an interface of jazygit. Arrays, `Object`, `CharSequence`, java.util collections, `StringBuilder` and `java.util.concurrent` types are not. What a type is of counts too: `List<StringBuilder>` is no value, though `Object` there says no more than a type variable, and a function's or a `Class`'s type arguments are not looked at. (`Failure.Bug` keeps what a command threw, to report it.)
- Fields are final; static fields are final too, and never of a mutable type.
- Collections are Vavr's: no method takes or returns one of java.util's, but where a library's interface says so.
- A missing value is Vavr's `Option`, not `java.util.Optional`, but where a library's interface says so (TamboUI, picocli in `cli`).
- Classes are built of records and interfaces: no class in state, feature or the models extends another.
- Commands, and the public methods of the core, declare no exceptions, checked or unchecked, of any type: failures are values (`Either`, `Failure`).


## Process

### ADR

- Decisions with a big impact on the architecture or were impactful design decisions had to be made are recorded in an ADR (Architecture Decision Record) in `docs/adr`. Each ADR is a markdown file with a name like `0001-foo.md`, where the number is the next available integer. 
- ADR's are immutable once they are in the main branch. They can be amended in a new ADR, but the old one is never changed apart from a reference to a newer one with a sentence like "Amended by ADR XX: this is why bla bla bla"
- The ADR's are a historical record of the decisions made, and the reasons for them. They are not a design document, and they are not a specification. They are a record of the decisions made, and the reasons for them.


## Testing

### Unit Tests

- all functionality needs to be covered by unit tests
- the acceptance tests are written in python. Their main focus in validating the state of the git repository after a command has been run, and that the output is as expected. They are not meant to be a complete test of the functionality, but rather a sanity check that the core is working as expected.
- integration tests are written in java, use a Pilot from Tamboui, and test the rendering and interaction of the UI.
