# 122. A version of JSON rendered

Date: 2026-11-13

## Context

A version of a file (ADR 102) shows the whole new file or its original, and a version of Markdown can show rendered
(ADR 103). ADR 103 made rendering a seam: one table of extensions that have a renderer, and a renderer that is given
the text of a version, the numbers of its lines that changed, a width and the styles of the theme, and gives the rows
it is drawn as, each with the lines of the source it was drawn from.

JSON is the next text a reader would rather see drawn. Pretty-printed, its source is already a tree, and highlighted it
reads well; drawn as a tree it reads much the same. What a reader of JSON is helped by is what its source cannot do:
a minified file on one line made readable, a long array or a deep object put out of the way, an array of records read
as a table. So rendering JSON is worth it for its folding, more than for its drawing.

jazygit reads JSON already, in `jazygit-json`, as values that keep the order of the fields of an object. The values
do not say which line of the text they were read from, and a renderer needs that for each row. tamboui has a tree,
`TreeElement`, with folding of its own; jazygit uses it for the files of a commit. It scrolls and selects by itself,
draws its guides before what a row holds, and does not scroll sideways, where the diff area is drawn as rows, with
its scroll in the model, its stops from `DiffCursor` and its gutter of marks.

## Decision

### What is rendered

- A version of a JSON file can show rendered, as a version of Markdown can (ADR 103): Ctrl+T, kept with the version,
  in the same title. A file is JSON by its extension: `.json`, a line in ADR 103's table of renderers. Not: `.jsonc`
  and `.json5`, which allow comments and trailing commas. Not: `.jsonl` and `.ndjson`, a value for each line, not one
  tree. Not: `.ipynb`, which is to be a renderer of its own (ADR 103).
- The seam of ADR 103 is given which nodes of the version are closed, and each row it gives says which node it opens
  or closes, if any. Markdown is given them too, and gives no nodes. Not: a seam for JSON only.
- Text that is not JSON is shown as source, under one line saying where reading it stopped: `Not rendered: line 12,
  column 3, expected ','`. jazygit writes that line itself, from the location Parsson gives and the token it expected,
  as a character. Not: Parsson's own text, `Invalid token=CURLYCLOSE at (line no=12, ...)`. A version cut at the limit
  of ADR 79 is not JSON either: the line says it was cut. JSON nested deeper than the reader goes, `MAX_DEPTH`, is shown
  as source too: the line says how deep it may go. Not: what could be read rendered, up to where it stops, as Markdown
  is: half a value is no tree.

### How it is read

- `jazygit-json` reads JSON a second way, for rendering: each key and each value with the line it starts on, the
  line a container ends on, and a number as it is written, `1e3` staying `1e3`. Of two fields with the same key, the
  last holds, as it does when `Json` is read, and as nearly every reader of JSON has it; the lines of those before it
  are kept with it. It shares the reader that reads `Json`. Not: both fields kept, which no program reading the file
  sees, and which would give two nodes one path. `Json` stays as it is, for decoding what `gh` gives. Not: a reader of
  its own in the app.

### How it is drawn

- A tree, with guides of `├─`, `└─` and `│`, as tamboui's `GuideStyle.UNICODE` draws them, so it looks as the tree of
  files does. A field is drawn as its key and its value, an element of an array as its index and its value. Keys keep
  the order of the file. Not: sorted keys, which hide the order the author gave them, and scatter the rows of a
  change.
- A container of scalars that fits in 48 columns is drawn on one line: `audience  ["internal", "beta"]`,
  `runner  {os: "ubuntu-24.04", arch: "x64"}`. Empty ones are `{}` and `[]`.
- A container drawn on rows of its own is followed by its count: `checks [25]`, `defaults {3}`.
- A long array of scalars is packed: as many values on a row as the width of the area holds, each row named by the
  indices it holds, `33–43`. It is packed again when the width changes.
- An array of two or more objects that hold only scalars, all with the same keys, is a table: a column for each key,
  as wide as its widest cell, numbers aligned to the right, the keys as its first row, each row named by its index. A
  table wider than the area scrolls sideways, as a table of Markdown does (ADR 103).
- An object in an array is named by one of its fields: `name`, `title`, `id`, `login`, `key` or `path`, the first of
  these it has, or else its first scalar: `2  name: "acceptance" {4}`. That field is not drawn again in it.
- A field whose key the object has more than once is drawn once, with its last value, and a dim note of the lines of
  the others: `name  "build"  (also on line 4, ignored)`. A key written twice is most likely a mistake, and is not
  hidden.
- A string is drawn in quotes, its newlines as `↵`. A long string is not wrapped: it scrolls sideways, as the lines of a
  diff do.
- A root that is an array is drawn as any array: an array of records is a table. A root that is a scalar is one row.
- The colours are those of the syntax of the rich view (ADR 53): the kinds of token that colour JSON as source colour
  its keys, strings, numbers and literals, so Ctrl+T shows the same colours either way. Guides, counts, indices and
  previews are dim. Not: styles of the theme of their own.

### Folding

- A rendered JSON version folds: a node, an object or an array drawn on rows of its own, can be closed, and is then one
  row, its count followed by a preview: its first values, or the keys of its objects,
  `checks [25]  {name, conclusion, durationSeconds}`.
- A node of more than 20 entries starts closed. Every other node starts open.
- A node is named by its path from the root, as a JSON Pointer: `/checks/9`, `/jobs/2/steps`. As the last of two
  fields with the same key holds, a path names one node.
- What the user chose for a node, open or closed, is kept by its path in the model, with the version, as rendered is
  (ADR 103), and dropped with it. A node the user did not choose for starts as above. Read again, as the file in the
  working tree changes or a pull request is pushed, the version keeps the choices whose path is still there, and a
  node keeps what was chosen for it however many entries it now has. The new version and the original share them, so
  Ctrl+O shows a node the user chose for folded alike on both sides; a path only one side has holds there only. Not:
  kept as nodes toggled away from how they start, which shows a node open on one side and closed on the other when it
  starts closed on one of them only. Not: kept by the widget, as the tree of files keeps its own: which nodes are
  closed says which rows there are, and the scroll and the stops of the area are made from the rows.
- Not now: folding the source of a version, by its brackets, or YAML and code by their indentation. They would be for
  every version, not for JSON rendered, and are for an ADR of their own.
- Not: tamboui's `TreeElement`, which scrolls and selects by itself, puts a mark of a change after the guides rather
  than in the gutter, and does not scroll sideways.

### The cursor and the keys

- A rendered JSON version has a cursor going from row to row, `j` and `k`, where a version of Markdown rendered has
  none and scrolls (ADR 103): a row of JSON can be acted on. Ctrl+T shows the source again with the cursor on the line
  of the row it was on, and rendering puts it on the row of the line it was on.
- Enter on a node closes it, or opens it. On a row that is no node it does nothing. Enter shows the lines a marker
  leaves out (ADR 77) only on a marker, and a rendered version has none.
- `z` closes every node below the root while any is open, and opens them all when none is.
- A cursor on a row that closing hides goes to the row of the node that closed over it.
- Both are listed by `?` with the keys of the diff, and do nothing where they do not apply. Not: `h` and `l` to close
  and open, as a tree has them: in the area they scroll sideways.

### Where it changed

- A row is marked as ADR 103 marks a block: a bar, `▎`, in the gutter, in the colour of an added line in the new
  version, of a removed one in the original. A row stands for all the lines of what it draws: a closed node for all of
  its lines, a packed row for those of its values, a row of a table for those of its object. So a closed node holding a
  change is marked.
- A node that starts closed stays closed when a change is in it. Not: opened for the change, which would open an array
  of 500 values for one of them.

### Review threads and copying

- As in a version of Markdown rendered (ADR 103): while rendered, `y` and `v` do nothing, a thread cannot be started,
  and no review threads are shown. Ctrl+T shows the source, with its threads.

## Consequences

- The seam of ADR 103 holds folding: a renderer is given closed nodes, and its rows may name one.
- A version holds, by path, the nodes the user opened or closed, next to whether it is rendered.
- `jazygit-json` has a reader that keeps lines and numbers as written, besides `Json`.
- Rows know the lines they stand for, not the columns, and a diff knows which lines changed, not which parts of them.
  So in JSON written on one line, a change anywhere marks every row, and Ctrl+T puts the cursor on the first row, or on
  that one line. Columns in the reader would help the cursor only, not the marks: they are left out.
- A rendered version has a cursor row by row when its renderer gives nodes; Markdown keeps scrolling.
- JSON larger than the limit of ADR 79, as a big `package-lock.json`, is cut, and so shows as source.
- Folding the source of any version, by brackets or indentation, can come later with an ADR of its own.
