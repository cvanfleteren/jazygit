# Java and GraalVM native image

What Java and GraalVM native image do that jazygit relies on, learned by testing, reading the source or docs, or
measuring. Each fact says when it was learned, so a fact that has grown old can be checked again; see README.md.

## Native image

- A native library (e.g. SQLite's) complicates the GraalVM native image. *Why it matters:* check history is kept in a
  TOML file, not SQLite. — learned 2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md)
- Tree-sitter via `jtreesitter` needs a native library and a native parser per language per platform, reached through
  FFM in a native image. *Why it matters:* tree-sitter was rejected for syntax highlighting. — learned 2026-11-22 · [ADR
  0053](../adr/0053-syntax-in-the-rich-view.md)
- Resource files on the classpath, such as grammar files, have to be registered for a native image. *Why it matters:*
  language grammars are Java enum data, not files. — learned 2026-11-22 · [ADR
  0053](../adr/0053-syntax-in-the-rich-view.md)
- Each module's resource bundle (`messages.properties`) must be named in that module's native-image metadata. *Why it
  matters:* every module that has text declares its bundle for the native image. — learned 2026-11-22 · [ADR
  0136](../adr/0136-the-application-in-modules.md)
- SnakeYAML Engine's event API needs no reflection in the native image. *Why it matters:* adding `jazygit-yaml` needs no
  reflection configuration. — learned 2026-11-22 · [ADR 0123](../adr/0123-a-version-of-yaml-rendered.md)
- In the native image, AWT (`ImageIO`, `Desktop.browse`) only works with eight JDK libraries next to the executable,
  about 5 MB. On its own the executable fails with `UnsatisfiedLinkError: Can't load library: awt`. *Why it matters:*
  jazygit ships as one file, so it decodes PNG and GIF itself and starts the browser itself. — learned 2026-11-22 · [ADR
  0082](../adr/0082-an-image-in-a-comment-is-a-placeholder.md), [ADR
  0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md), [ADR
  0085](../adr/0085-a-review-remark-opens-in-the-browser.md)
- A native image that talks to Forgejo needs the JDK's HTTP client and TLS included. *Why it matters:* the Nix build
  checks that the native image can make https requests. — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md)

## Strings, records and collections

- Java `String` positions and lengths count UTF-16 `char`s. *Why it matters:* token and word positions are in UTF-16
  units of the line. — learned 2026-11-22 · [ADR 0053](../adr/0053-syntax-in-the-rich-view.md)
- A Java `String` cannot be wiped, and the garbage collector may copy it. Off-heap memory is not copied by the
  collector, but without `mlock` it can still be swapped out. *Why it matters:* the passphrase is kept in
  `char[]`/`byte[]` buffers that are cleared after use, never a `String`. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- Concatenating a Java record into a string calls its generated `toString`, e.g. `"x" + remote` writes
  `RemoteName[value=origin]`. *Why it matters:* such misuse escapes the ArchUnit rule and is caught only by command-line
  tests. — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)
- `LinkedHashMap` keeps a key at its first position when it is put again. *Why it matters:* the reader for rendering
  does not build one, so that a duplicate key is drawn where its last occurrence is written. — learned 2026-11-22 · [ADR
  0122](../adr/0122-a-version-of-json-rendered.md)

## Processes, sockets, files and HTTP

- `ProcessHandle.current().info().command()` gives the path of the running executable. *Why it matters:* the native
  binary can name itself as the `SSH_ASKPASS` helper. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- Under `java -jar`, each askpass prompt starts a new JVM, which adds a fraction of a second before the popup opens. The
  native binary starts at once. *Why it matters:* askpass latency depends on how jazygit is run. — learned 2026-11-22 ·
  [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- Java has no pseudo-terminal API. pty4j (ConPTY on Windows) brings native libraries, which a single native-image binary
  cannot carry. *Why it matters:* reading ssh's prompts through a pty, as lazygit does, was rejected. — learned
  2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- Java supports Unix domain sockets on Linux, macOS and Windows 10 and later. Whether the GraalVM native image supports
  them on Windows is untested. *Why it matters:* one askpass channel can serve every platform. — learned 2026-11-22 ·
  [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- A JVM that is killed or crashes never runs its shutdown hooks. *Why it matters:* files and sockets made at startup are
  left behind in the temp directory, which is why the askpass socket is created lazily. — learned 2026-11-22 · [ADR
  0153](../adr/0153-the-askpass-socket-opens-when-ssh-may-ask.md)
- Java's `WatchService` only polls on macOS. *Why it matters:* the index is watched with a stamp each tick instead of a
  file watcher. — learned 2026-11-22 · [ADR 0097](../adr/0097-the-index-written-anew-once-it-changed.md)
- `FileSystem.getPathMatcher("glob:…")`: `*` does not cross a `/` and `**` does. A glob starting with `**/` requires the
  `/` after `**`, so `**/__snapshots__/*.snap` does not match `__snapshots__/a.snap` at the root, whereas git's `**/`
  does. *Why it matters:* jazygit's path filters also try a `**/` glob without that prefix. — learned 2026-11-22 · [ADR
  0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- An HTTP request to a host that does not answer takes the full connect timeout. *Why it matters:* Forgejo detection,
  which waits on every remote's host, is delayed by that timeout, in the background. — learned 2026-11-22 · [ADR
  0174](../adr/0174-how-the-last-build-on-forgejo-went.md), [ADR
  0177](../adr/0177-the-forge-of-a-repository-decided-or-asked.md)
- Measured idle for 60 s with the status read every second, the JVM used 1.3-2.0 s of CPU per minute, mostly starting
  the git reads and handling what they returned (2.8-3.9 % of a core in all, with git). *Why it matters:* the status
  read moved from every 1 s to every 3 s. — learned 2026-11-22 · [ADR
  0125](../adr/0125-reading-the-status-every-3-s-and-turning-the-timers-off.md)

## StAX

- The JDK's StAX parser needs no extra dependency, but the location it reports for an event is where the event ends
  (after a tag's `>`, after the `<` that follows a text), not where it starts. *Why it matters:* the line a tag starts
  on must be found again, one reason the XML reader is hand-written. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)
- StAX stops at a reference to an entity no DTD declares (such as XHTML's `&nbsp;`), even with DTDs turned off. *Why it
  matters:* StAX cannot render XHTML, so jazygit wrote its own lenient reader. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)
