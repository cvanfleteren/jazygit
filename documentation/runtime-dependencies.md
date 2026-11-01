# Runtime dependencies of the fat jar

An analysis of what `jazygit-app/target/jazygit-0.1.0-SNAPSHOT-fat.jar` is made of, as of commit `ec09b6c`
(2026-11-01), and which dependencies could be dropped.

The fat jar is **7.3 MB**. About 1.5 MB of it (~20%) could go with little effort, most of it by dropping the JLine
backend (now removed). The sizes in the table below are those from before that.

## Method

- `./mvnw dependency:tree -pl jazygit-app -Dscope=runtime` for the tree.
- `unzip -v` on the fat jar, entries grouped by library, for the compressed (shipped) and uncompressed sizes.
- `grep` over `*/src/main` for how many files import each library.

## Dependency tree

```
jazygit-app
+- jazygit-process
|  +- org.zeroturnaround:zt-exec:1.13.0
|  \- org.slf4j:slf4j-api:2.0.20
+- jazygit-git
|  \- jazygit-syntax
+- jazygit-gh
|  \- jazygit-json
|     \- org.eclipse.parsson:parsson:1.1.9
|        \- jakarta.json:jakarta.json-api:2.1.3
+- io.vavr:vavr:1.1.0 (+ vavr-match, jspecify; jspecify excluded from the shade)
+- dev.tamboui:tamboui-toolkit:0.6.0 (core, widgets, tui, css)
+- dev.tamboui:tamboui-toolkit-markdown:0.6.0
+- dev.tamboui:tamboui-markdown:0.6.0
|  \- org.commonmark:commonmark:0.28.0 (+ gfm-tables, task-list-items, gfm-strikethrough)
+- dev.tamboui:tamboui-panama-backend:0.6.0
+- com.electronwill.night-config:toml:3.9.0 (+ core)
+- info.picocli:picocli:4.7.7
\- org.slf4j:slf4j-simple:2.0.20
```

## Where the bytes go

| Library                                         | Compressed | Uncompressed | Used in `src/main`                     |
|-------------------------------------------------|-----------:|-------------:|----------------------------------------|
| jazygit's own code                              |    2162 KB |      6275 KB | 1146 classes, 54 KB `messages.properties` |
| jline (via `tamboui-jline3-backend`)            |    1264 KB |      3012 KB | 0 files                                |
| tamboui (core, widgets, toolkit, css, tui, md)  |    1057 KB |      2463 KB | everywhere                             |
| vavr                                            |     979 KB |      3401 KB | 489 files                              |
| picocli                                         |     365 KB |       915 KB | 4 files: 1 `@Command`, 2 `@Option`, 1 `@Parameters` |
| night-config (core + toml)                      |     352 KB |       975 KB | 6 files, all under `settings/`         |
| commonmark (+ 3 extensions)                     |     201 KB |       450 KB | via `tamboui-markdown`, 1 file         |
| parsson + jakarta.json-api                      |     119 KB |       294 KB | 1 file (`JsonReader`)                  |
| slf4j-api + slf4j-simple                        |      65 KB |       155 KB | 8 files                                |
| zt-exec                                         |      46 KB |       102 KB | 1 file (`Exec`)                        |
| `META-INF` (poms, licenses, services)           |      52 KB |       185 KB |                                        |

## What could be dropped

### 1. The JLine backend: ~1.26 MB (done)

No code imported JLine; it was only listed as a backend provider in `META-INF/services/dev.tamboui.terminal.BackendProvider`,
next to Panama, and listed first. Only `run.sh` and the acceptance tests pinned `-Dtamboui.backend=panama`, so a plain
`java -jar` of the fat jar picked JLine, which breaks handing the terminal over to an editor: its reader thread takes the
first key typed there (`review.md`, item 7). The jar also carried JLine's native libraries for about 15 platforms, its
line reader, console and builtins, none of which jazygit uses.

`tamboui-jline3-backend` is removed from `jazygit-app/pom.xml`, so the fat jar now only has the Panama backend, which
also fixes the hand-over for `java -jar`. `run.sh --jdwp` no longer falls back to `jline3`, and the comment on
`--enable-native-access` in the `native` profile now names Panama. To check: that tamboui's Panama backend works on every
platform jazygit is meant to run on (Windows in particular).

### 2. zt-exec and slf4j: ~110 KB, and fewer classes loaded at startup

`Exec` uses zt-exec for a working directory, environment, reading the output, redirecting stderr, accepting any exit
value and a timeout. `ProcessBuilder` and `Process.waitFor(timeout)` on JDK 25 do all of that in a few dozen lines.
Without zt-exec, nothing else needs slf4j: `DebugLog`, `BugLog` and the other callers could log through
`System.Logger` or a small file writer instead.

### 3. parsson: ~120 KB

`jazygit-json` already keeps parsson behind its own values; only `JsonReader` touches the streaming parser. A
recursive-descent JSON reader is some 150–200 lines. A small win, but a contained one.

### 4. picocli: ~365 KB

Three options are easy to parse by hand, but that loses the generated `--help`/`--version`, the converters and picocli's
native-image support. Only worth it if the size of the jar really matters.

## What should stay

- **vavr**: the third biggest library, but the backbone of the code (366 imports of `Option`, 344 of `List`).
- **tamboui**: the UI itself.
- **night-config**: TOML read and written back with its comments kept, which is not trivial to replace.
- **commonmark**: needed for as long as markdown is rendered.

## Notes

- The shade plugin's `minimizeJar` could strip the unused parts of vavr and picocli, but it is fragile with
  `ServiceLoader` and reflection (the tamboui backends, parsson's provider), so it is not worth relying on.
- In the native image (`-Pnative`) unreachable code is removed anyway; the size of the jar mostly matters for the
  fat jar that is distributed.
