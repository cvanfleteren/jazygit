# 53. Syntax in the rich view

Date: 2026-10-30

## Context

The rich view (ADR 44, 47) draws a diff with line numbers and the changed words of a changed line highlighted. The
code in it is drawn in one colour: green or red for a changed line, the default colour for a context line. Most
tools that draw diffs colour the code too, by its syntax, which makes a hunk quicker to read.

The diff is parsed, and its words compared, where it is read, on the thread that read it from git (ADR 50). What the
rich view draws is a `ParsedDiff`; drawing it only styles it.

A highlighter can be:

- A lexer of jazygit's own: one scanner, set up for each language with its keywords, comments and strings. It
  colours about as well as an editor's highlighting did before it parsed, needs no library and is fast.
- TextMate grammars, through Eclipse's tm4e: those of VS Code, for nearly every language, and accurate. It brings
  joni (a port of Oniguruma) and the grammars, which a native image has to be told about, and its regular
  expressions are much slower than a scanner.
- Tree-sitter, through `jtreesitter`: the most accurate, but a native library and a native parser for each language
  on each platform, through FFM in a native image.
- Another program, like delta or bat: its ANSI output would have to be read back into spans, losing the line numbers
  and the changed words, at the cost of a process per diff.

A hunk shows a part of a file. It can start inside a block comment or a multi-line string, which no highlighter can
tell from the hunk alone.

## Decision

### The highlighter

- A lexer of jazygit's own, in `jazygit-core`, behind an interface (`Highlighter`), so that tm4e could take its place
  later. Not: tree-sitter, nor another program.
- It finds four kinds of tokens: keywords, strings, comments and numbers. Names, types, annotations and punctuation
  are not coloured: a scanner cannot tell them apart reliably.
- A language is a constant of the enum `Language`, which holds what the scanner needs: its keywords, whether they
  are matched ignoring case, its line comments, its block comment, its kinds of strings, and the names and
  extensions of its files. Not: grammar files on the classpath, which a native image has to be told about.
- The scanner knows strings over several lines (Python's `"""` and `'''`, Java's and Kotlin's text blocks), Go's and
  JavaScript's backtick strings (a template literal is a string, `${…}` included), escapes in strings, and
  keywords matched ignoring case (SQL). Not: Rust's raw strings, nested block comments, shell heredocs, nor the
  expressions in Python's f-strings and Kotlin's `${…}`, which are coloured as the string they are in.
- XML and HTML are scanned for tags: a tag's name is a keyword, the value of an attribute a string, `<!-- -->` a
  comment.
- Markdown: a heading, and the marker of a list item or a quote, is a keyword; inline code and the lines of a fenced
  code block, its fences included, are strings; `<!-- -->` is a comment. Not: the code in a fence coloured in its
  own language, which needs one scanner inside another and goes wrong whenever a hunk starts inside a fence.
- The first languages: Java, Kotlin, JavaScript and TypeScript, Python, Go, Rust, C and C++, shell, SQL, JSON, YAML,
  TOML, XML and HTML, and Markdown.

### Which language

- A file's language follows from its path, the new one of a rename: first its name as a whole (`Makefile`,
  `Dockerfile`, `Jenkinsfile`, `.bashrc`, …), then its extension, then a shebang (`#!/usr/bin/env python3`), only
  when the first line of the file is in the diff. A file of no language known is drawn as now.
- Not: the attributes of `.gitattributes` (`diff=`, `linguist-language`), which takes a `git check-attr` for each
  diff read. Not yet: a table in the settings from paths to languages, for the extensions a team makes up; `Language`
  is found from data, so it can be added (`wanted.md`).
- An extension two languages use is given to the one it more often means, or the one that holds the other: `.h` is
  C++.

### Lexing a hunk

- A hunk is lexed as two texts: the old one, its context and removed lines, and the new one, its context and added
  lines. What the scanner is inside of at the end of a line, a block comment or a multi-line string, carries on to
  the next line of the same text. Each hunk starts outside of everything. Not: reading the whole of the old and the
  new file to know what a hunk starts inside of, which costs two more reads from git for each file. A hunk that
  starts inside a comment or a string is coloured wrong until it ends.
- A context line, which is in both texts, takes its tokens from the new one, as it is drawn as part of the file as it
  is now.
- What is not code is not lexed, and leaves the scanner where it was: notes (`\ No newline at end of file`), the
  header of a hunk, the lines of a conflicted file (ADR 47), and what comes before the first file.
- A token is a `Token`: where it starts in its line, how long it is, and its kind. `ParsedDiff` holds the tokens of
  each line of each hunk next to its words, in the same shape. They are found in `ParsedDiff.of`, where the diff is
  read (ADR 50), also while the plain view is chosen, as the words are. The scanner has no budget: it takes time in
  proportion to the text, and a diff is cut off after `CliDiff.MAX_LINES` lines.

### Drawing

- Only the rich view is coloured. The plain view stays git's text.
- A changed line keeps the background of its class (`diff-added-line`, `diff-removed-word`, …), and takes the colour
  of its text from the class of its token (`syntax-keyword`, `syntax-string`, `syntax-comment`, `syntax-number`), or
  of `syntax-text` outside a token, which is the theme's default colour unless the theme gives it one. Its marker,
  `+` or `-`, keeps the whole style of its class. A context line takes the colour of its tokens and no background.
  Where a token and a changed word overlap, the span is cut at the bounds of both.
- A changed line whose class gives it no background, as in the themes `mono` and `none`, keeps the colour of its
  class outside a token: there, that colour is what tells it is changed.
- A file none of whose lines has a token, as its language is not known, is drawn as before.
- The classes of the diff keep their colour in every theme: that is how a changed line is drawn when it is not
  coloured by its syntax, as before. Each theme gets the four classes of the syntax, with colours of its own that
  read on both its added and its removed background; the themes for colour blindness use no red and green for them.
- It is on by default. `ui.diff-syntax = false` in `config.toml` turns it off; no key switches it, as Ctrl+D switches
  the style already.

### Delivery

1. This ADR.
2. `Language` and the lexer in `jazygit-core`, tested on their own.
3. `ParsedDiff` holds the tokens.
4. `RichDiff` draws them: the classes and their colours in each theme, the setting `ui.diff-syntax`, `ARCHITECTURE.md`,
   `wanted.md`, and an acceptance test of a keyword of a staged Java file in the rich view.

## Consequences

- The rich view colours the code of the languages above; a file of another language is drawn as before.
- A hunk that starts inside a block comment or a multi-line string is coloured wrong until it leaves it.
- Reading a diff takes a little longer, also in the plain view: each line is scanned once more.
- A theme has four more classes to keep.
