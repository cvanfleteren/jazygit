# Other libraries and formats

What other libraries, file formats and tools do that jazygit relies on, learned by testing, reading the source or docs,
or measuring. Each fact says when it was learned, so a fact that has grown old can be checked again; see README.md.

## vavr and Java records

- Comparing Java records and vavr persistent collections with `equals` is cheap after an update, as comparison stops at
  parts the two values share. *Why it matters:* comparing old and new Model on every message decides whether to draw. —
  learned 2026-11-22 · [ADR 0014](../adr/0014-redraw-on-change.md)
- Appending to a Vavr `List` copies it, so building a list of children by appending is quadratic: 40,000 sibling XML
  elements took three seconds. *Why it matters:* the XML reader gathers elements, attributes and children in vectors to
  stay linear. — learned 2026-11-22 · [ADR 0151](../adr/0151-a-version-of-xml-rendered.md)

## ArchUnit

- ArchUnit sees method calls, not what they are for. *Why it matters:* the rule forbids every call of
  `Revision.toArg()`, `BranchName.value()` and `RemoteName.value()` outside `GitArgs`, even when the result is not an
  argument. — learned 2026-11-22 · [ADR 0040](../adr/0040-typed-revisions-and-remotes.md)

## zt-exec

- An `OutOfMemoryError` thrown on zt-exec's own thread was printed over the TUI, and one thrown while the failure was
  being reported left the diff loading for good. *Why it matters:* program output is bounded, and uncaught thread errors
  go to the bug log. — learned 2026-11-22 · [ADR 0079](../adr/0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md)

## Maven and JUnit

- `mvn -pl :core-git` selects a module by artifact id, while `-pl core-git` is a path, which no longer names a module
  once modules sit in group folders. *Why it matters:* single modules must be built with the `:artifactId` form. —
  learned 2026-11-22 · [ADR 0159](../adr/0159-the-modules-in-groups.md)
- The Maven reactor only refuses a dependency cycle between modules. It does not stop a lower layer using a higher
  module that does not use it back. *Why it matters:* layering is enforced by `GroupsTest` with ArchUnit, not by the
  build. — learned 2026-11-22 · [ADR 0159](../adr/0159-the-modules-in-groups.md)
- With Maven `-T1C`, modules that do not depend on each other build at the same time. *Why it matters:* feature groups
  that do not use each other build in parallel. — learned 2026-11-22 · [ADR
  0136](../adr/0136-the-application-in-modules.md)
- A `junit-platform.properties` packaged in a test-jar is passed on to every module that depends on that test-jar. *Why
  it matters:* JUnit is configured through surefire's `configurationParameters` instead. — learned 2026-11-22 · [ADR
  0136](../adr/0136-the-application-in-modules.md)

## commonmark-java

- commonmark makes no link of a URL that is only text, nor of `#381`. *Why it matters:* jazygit rewrites the Markdown of
  a comment to link bare URLs and references. — learned 2026-11-22 · [ADR
  0096](../adr/0096-opening-the-links-of-a-comment.md), [ADR
  0098](../adr/0098-references-and-bare-links-in-a-comment.md)
- commonmark's table extension finds a table as GitHub does: also right after a line of text, and not when the first row
  has another number of cells than the delimiter row; a cell holds inline text only (a cell of `-` is a dash, not a
  list). *Why it matters:* rendered tables match GitHub's. — learned 2026-11-22 · [ADR
  0103](../adr/0103-a-version-of-markdown-rendered.md)

## TOML and the TOML library

- TOML has no null value. *Why it matters:* samples of non-Actions checks leave out `finished` and `event` instead. —
  learned 2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md)
- In TOML, an array-of-tables header such as `[[editors]]` must be on a line of its own. *Why it matters:* the example
  in the "no editor set" message is printed on separate lines so it can be pasted. — learned 2026-11-22 · [ADR
  0110](../adr/0110-a-worktree-opened-in-an-editor.md)
- TOML refuses a plain key beside an array of tables of the same name (`diff-filters = [...]` next to
  `[[diff-filters]]`). *Why it matters:* the enabled filters are stored as `ui.diff-filters` rather than under
  `diff-filters`. — learned 2026-11-22 · [ADR 0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- TOML literal strings (single quotes) keep a backslash as typed. *Why it matters:* regex line patterns of diff filters
  are written as literal strings. — learned 2026-11-22 · [ADR 0144](../adr/0144-filters-that-hide-hunks-of-a-diff.md)
- The TOML library jazygit uses keeps the comments of keys it leaves alone, but a value it writes anew loses the
  comments inside it. An array of tables written back loses the comments between and inside its tables, and the blank
  lines between them. *Why it matters:* editing a list from the settings panel loses those comments, and the panel warns
  about it. — learned 2026-11-22 · [ADR 0164](../adr/0164-the-panel-edits-a-list-of-the-settings.md)

## JSON and Parsson

- `.jsonc` and `.json5` allow comments and trailing commas, and `.jsonl`/`.ndjson` hold one JSON value per line rather
  than one tree. *Why it matters:* only `.json` is rendered as JSON. — learned 2026-11-22 · [ADR
  0122](../adr/0122-a-version-of-json-rendered.md)
- Nearly every JSON reader takes the last of two fields with the same key. *Why it matters:* rendered JSON keeps the
  last value and notes the ignored ones. — learned 2026-11-22 · [ADR 0122](../adr/0122-a-version-of-json-rendered.md)
- When Parsson stops on invalid JSON, its location is the last character of the token it stopped at, or past the end of
  the text, not where that token starts. *Why it matters:* jazygit reads the token's start back from the text to report
  the line and column. — learned 2026-11-22 · [ADR 0122](../adr/0122-a-version-of-json-rendered.md)
- Parsson says what it expected only in the words of its message, often as more than one token, and not at all for a
  character it cannot read. Its own message reads like `Invalid token=CURLYCLOSE at (line no=12, ...)`. *Why it
  matters:* jazygit writes its own `Not rendered: line 12, column 3, unexpected ']'` line instead. — learned 2026-11-22
  · [ADR 0122](../adr/0122-a-version-of-json-rendered.md)

## YAML and SnakeYAML Engine

- SnakeYAML Engine reads YAML 1.2, where `on`, `NO` and `yes` are strings under the core schema; under YAML 1.1 `on` and
  `no` are booleans. *Why it matters:* plain values are typed per the YAML 1.2 core schema. — learned 2026-11-22 · [ADR
  0123](../adr/0123-a-version-of-yaml-rendered.md) · YAML 1.2
- SnakeYAML's node API puts the node an alias names where the alias is, losing where it was written, and fails on a file
  with many aliases. *Why it matters:* jazygit reads YAML through SnakeYAML Engine's events and never expands an alias.
  — learned 2026-11-22 · [ADR 0123](../adr/0123-a-version-of-yaml-rendered.md)
- The position SnakeYAML reports for a syntax error is often where it noticed the problem, a line or more after what is
  wrong. Its messages are in English only. *Why it matters:* the not-rendered line shows SnakeYAML's own words, such as
  `could not find expected ':'`, rather than the character at that position. — learned 2026-11-22 · [ADR
  0123](../adr/0123-a-version-of-yaml-rendered.md)
- Helm templates (and other templated YAML) are not valid YAML. *Why it matters:* they mostly show as source, under the
  not-rendered line. — learned 2026-11-22 · [ADR 0123](../adr/0123-a-version-of-yaml-rendered.md)
- In multi-document Kubernetes YAML, `apiVersion` is the first scalar of every manifest, and `kind` is what tells
  manifests apart. *Why it matters:* documents are named by `kind` first, because otherwise every one would be named by
  its `apiVersion`. — learned 2026-11-22 · [ADR 0123](../adr/0123-a-version-of-yaml-rendered.md)

## XML

- An XML element's children (elements, text and comments) come in an order that matters, siblings of the same name stand
  in for JSON's arrays, text and elements can be mixed (`<p>Hello <b>you</b>!</p>`), and comments often carry meaning
  (for example a dependency commented out). *Why it matters:* XML is drawn as its own node kinds, not converted to
  mappings and sequences. — learned 2026-11-22 · [ADR 0151](../adr/0151-a-version-of-xml-rendered.md)
- XML-to-JSON converters represent XML as mappings with `@attribute` and `#text` keys, which loses order and reads
  oddly. *Why it matters:* that representation was rejected for rendering. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)
- XML predefines exactly five entities (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`), plus numeric character references
  such as `&#233;`. Other entities need a DTD. *Why it matters:* only those are decoded, and others such as `&nbsp;` are
  shown as written. — learned 2026-11-22 · [ADR 0151](../adr/0151-a-version-of-xml-rendered.md)
- Well-formed XML forbids: `--` inside a comment; `]]>` in text; `<` in an attribute value; an `&` that starts no
  reference; character references to characters XML disallows (`&#0;`); control characters, U+FFFE, U+FFFF and unpaired
  surrogates anywhere; a declaration anywhere but at the start; duplicate attributes; attributes without `=`, without
  quotes, or without preceding whitespace; more than one root element, or text outside the root. *Why it matters:* these
  are the well-formedness rules the hand-written reader checks. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)
- XML whitespace is only space, tab and line break, not every Unicode space. *Why it matters:* trimming and dropping
  whitespace-only text uses XML's definition. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)
- Visual Studio writes line breaks inside attribute values as `&#xD;&#xA;`. *Why it matters:* referenced line breaks
  stay on the source line they are written on and are drawn as `↵`. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)
- HTML files are mostly not well-formed XML, and SVG files are mostly path data. *Why it matters:* `.html` and `.svg`
  are not rendered as XML trees. — learned 2026-11-22 · [ADR 0151](../adr/0151-a-version-of-xml-rendered.md)
- A typical `pom.xml` of 400 lines, most of them tags, holds about 40 dependencies. *Why it matters:* a folded tree with
  a table of records reads far better than the source. — learned 2026-11-22 · [ADR
  0151](../adr/0151-a-version-of-xml-rendered.md)

## Markdown files

- `.mdx` files hold JSX, not plain Markdown. *Why it matters:* `.mdx` is not rendered as Markdown. — learned 2026-11-22
  · [ADR 0103](../adr/0103-a-version-of-markdown-rendered.md)

## Nix

- Much of a Nix file is names given values with `=` (in attribute sets, `let` and `rec { … }`), often as dotted paths
  (`services.nginx.enable = true;`). Editors and GitHub colour those names. *Why it matters:* without a property token,
  a NixOS module in the rich view is mostly plain text. — learned 2026-11-22 · [ADR
  0170](../adr/0170-properties-in-the-rich-view.md)
- Nix names may contain `-` and `'` after their first character (`foo-bar`, `x'`), so `inherit-x` and `then-y` are
  names, not keywords. *Why it matters:* the lexer must treat those characters as part of names in Nix, or it splits the
  name and mis-colours keywords. — learned 2026-11-22 · [ADR 0170](../adr/0170-properties-in-the-rich-view.md)
- In Nix, `a == b` is a comparison, `pkgs.hello` only reads a name, `{ cfg ? null }:` gives an argument a default,
  `inherit pkgs lib;` binds names without `=`, and function arguments `{ pkgs, lib, ... }:` are only known at the `}:`
  after them. Path segments can be quoted (`"foo-bar".x`) or interpolated (`${n}.x`). *Why it matters:* these define
  which names a one-line scanner can safely colour as properties. — learned 2026-11-22 · [ADR
  0170](../adr/0170-properties-in-the-rich-view.md)

## Other programming languages and syntax colours

- Some words are keywords only in some places: Java's module words (`module`, `requires`, `to`, `with`, …), Kotlin's
  soft keywords (`value`, `data`, `get`, `set`, `by`, …), TypeScript's `get`, `set`, `type`, `from`, `of`, and Python's
  `type` and `match`. *Why it matters:* they are not coloured as keywords, as they are far more often plain names. —
  learned 2026-11-22 · [ADR 0053](../adr/0053-syntax-in-the-rich-view.md)
- `.h` is used by both C and C++. *Why it matters:* it is lexed as C++, which holds C. — learned 2026-11-22 · [ADR
  0053](../adr/0053-syntax-in-the-rich-view.md)
- Javadoc's inline tags (`{@link}`, `{@code}`) are all lowercase, while Java annotations start uppercase, also in braces
  (e.g. `{@UniqueConstraint(…)}`); Python's `@` is also the matrix-product operator; MobX uses lowercase decorators such
  as `{@observable x}`. *Why it matters:* these decide when an `@` is coloured as an annotation, and the known false
  positives and negatives. — learned 2026-11-22 · [ADR 0113](../adr/0113-annotations-in-the-rich-view.md)
- IntelliJ's Darcula theme colours annotations yellow. *Why it matters:* the Darcula theme does the same. — learned
  2026-11-22 · [ADR 0113](../adr/0113-annotations-in-the-rich-view.md)
- GitHub's syntax colours show object keys in green, and its colour-blind themes show names in purple. *Why it matters:*
  jazygit's GitHub themes use those colours for `syntax-property`. — learned 2026-11-22 · [ADR
  0170](../adr/0170-properties-in-the-rich-view.md)

## Syntax highlighters and diff tools

- Eclipse tm4e brings VS Code's TextMate grammars, accurate for nearly every language, but pulls in joni (a port of
  Oniguruma) and the grammars, which a native image must be told about, and its regular expressions are much slower than
  a hand-written scanner. *Why it matters:* jazygit uses its own lexer behind a `Highlighter` interface, so tm4e could
  replace it later. — learned 2026-11-22 · [ADR 0053](../adr/0053-syntax-in-the-rich-view.md)
- delta and bat output ANSI escapes, which would have to be read back into spans, losing line numbers and changed words,
  at a process per diff. *Why it matters:* external highlighters were rejected. — learned 2026-11-22 · [ADR
  0053](../adr/0053-syntax-in-the-rich-view.md)

## Editors and other programs

- Editors differ in what they open: IntelliJ and VS Code open a directory or a file, while Helix or an `nvim` started on
  a file opens only a file. Each takes a line its own way: `code -g {file}:12`, `nvim +12 {file}`, `idea --line 12
  {file}`. *Why it matters:* an editor entry has a `dir-command` and a `file-command` with `{file}` and `{line}`
  placeholders. — learned 2026-11-22 · [ADR 0145](../adr/0145-a-file-opened-in-an-editor-at-a-line.md)
- `claude agents` exits with code 1 when not run on a terminal, printing `'claude agents' requires an interactive
  terminal` to stderr. *Why it matters:* this was the case where an editor that exits as it starts turned out to need
  `terminal = true`, which jazygit's error now hints at. — learned 2026-11-22 · [ADR
  0146](../adr/0146-a-failed-editor-in-the-command-log.md)

## lazygit

- lazygit names a repository after the main worktree's directory, or for a linked worktree after the directory holding
  the common git directory (`…/jazygit/.git` → `jazygit`, `…/proj/.bare` → `proj`); for a bare `app.git` it takes the
  directory above. Its header shows a linked worktree as `repo(worktree) → branch`. *Why it matters:* jazygit follows
  the same naming, except it names `app.git` as `app`. — learned 2026-11-22 · [ADR 0009](../adr/0009-worktree-ux.md)
- lazygit uses `t` for reverting a commit. *Why it matters:* jazygit uses the same key. — learned 2026-11-22 · [ADR
  0013](../adr/0013-revert-commit.md)
- lazygit reads the files every 10 s and fetches every 60 s by default, and lets both be turned off (`git.autoRefresh`,
  `git.autoFetch`). *Why it matters:* jazygit's `git.auto-refresh` and `git.auto-fetch` switches follow this precedent.
  — learned 2026-11-22 · [ADR 0125](../adr/0125-reading-the-status-every-3-s-and-turning-the-timers-off.md)
- lazygit asks for an ssh passphrase in a prompt of its own, reading ssh's prompts from git's output through a
  pseudo-terminal. *Why it matters:* it is the reference behaviour, but its pty approach cannot be used in Java. —
  learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
