# 123. A version of YAML rendered

Date: 2026-11-14

## Context

A version of JSON can show rendered, as a tree that folds (ADR 122). YAML holds the same kind of data, and is the
text of most of the configuration a repository has: workflows, compose files, manifests, templates of a cloud. What
helps a reader of JSON helps a reader of YAML: a long list or a deep mapping put out of the way, a list of records read
as a table. YAML is never minified, so what it gains over its source is the folding, the tables, and what its source
hides: a key written twice, where an anchor is used.

YAML has more than JSON. A value can be a block of lines, `run: |`. A node can have an anchor, `&common`, and be used
again by an alias, `*common`, or merged into a mapping, `<<: *common`. A node can have a tag, `!Ref`. A file can hold
more than one document. A key can be any node, and a plain value is a string, a number, a boolean or null by how it is
written: under YAML 1.1, `on` and `no` are booleans, under YAML 1.2 they are strings.

jazygit reads no YAML yet. A reader of a small part of YAML, written by hand, would not read most real files, as they
use blocks, anchors, tags and documents all the time; or it would read valid YAML wrongly, which is worse.

## Decision

### What is rendered

- A version of a YAML file can show rendered, as a version of JSON can (ADR 122): Ctrl+T, kept with the version, its
  nodes folding, with the cursor, the keys and the marks of where it changed that JSON rendered has. A file is YAML by
  its extension: `.yaml` and `.yml`, a line in ADR 103's table of renderers. A template that is no YAML, as those of
  Helm are, is shown as source under the line that says where reading it stopped.
- The limits of ADR 122 hold as they are: a version cut at the limit of ADR 79 is shown as source, and so is YAML
  nested deeper than it is read; rows know the lines they stand for, not their columns; while rendered, `y` and `v` do
  nothing, a thread cannot be started and no review threads are shown; the source of a version does not fold.

### How it is read

- A module of its own, `jazygit-yaml`, reads YAML with SnakeYAML Engine, a reader of YAML 1.2, by its events: each
  key and each value with the line it starts on, a mapping or a sequence with the line it ends on too. Only its reader
  uses SnakeYAML, as only `JsonReader` uses Parsson. Not: a reader of a part of YAML written by hand. Not: SnakeYAML's
  nodes, which put the node an alias names where the alias is, so lose where it was written, and fail on a file with
  many aliases.
- Every document of the file is read. A plain value keeps its text as written, and is a string, a number, a boolean or
  null as YAML 1.2's core schema has it: `on`, `NO` and `yes` are strings. Of two fields with the same key, the last
  holds, with the lines of those before it, as in JSON. A key that is no string is kept as it reads in flow style:
  `[eu-west-1, eu-central-1]`.
- An alias is a value of its own, `*common`, not what it names: it is never expanded. So a recursive alias is one leaf,
  and a file of aliases of aliases stays as small as it is written. Not: an alias opened as a copy of what it names.
- A tag and an anchor are kept with the node they are on, a tag of YAML's own as `!!binary`.
- A block, `|` or `>`, keeps the lines of its source, without their indentation. Not: the text a folded block, `>`,
  folds to, which no row would stand for one line of.
- Comments are not read. Ctrl+T shows the source, with them.

### How it is drawn

- JSON and YAML are read into one tree of the app, and drawn by one renderer, as ADR 122 draws JSON: guides, counts, a
  container of scalars on one line, tables, packed lists, an item named by one of its fields, folding, previews, and
  the note on a key written twice. JSON is drawn as it was.
- A plain value is drawn as written, without quotes. A quoted one keeps its quotes, `"5432"`, `'single'`, as a string
  of JSON keeps its quotes. An empty value is a dim `null`.
- A tag is drawn dim before its value, `!Ref Bucket`, also on a container: `Resource !Join [2]`. An anchor is drawn dim
  after its key, `environment &env  {LOG_LEVEL: info}`. An alias is drawn dim: `<<  *common`. A merge key, `<<`, is an
  ordinary field.
- A block is a node: a row with its key, its indicator and its count of lines, `run |6`, then a row for each of its
  lines, each standing for its own line, so a change marks the line that changed. Closed, it shows its first line.
  A block of more than 20 lines starts closed.
- A file of more than one document draws each as a node, `--- 1  kind: Deployment {4}`, named as an object in an array
  is, but by `kind` first, as Kubernetes has it: else every manifest is named by its `apiVersion`. Its path is its
  index, `/0`. A file of one document draws it as its root, as JSON is.

### Text that is not YAML

- It is shown as source, under one line that says where reading stopped and what SnakeYAML missed, in its words:
  `Not rendered: line 6, column 7, could not find expected ':'`. Not: what was found there, as ADR 122 says it for
  JSON: SnakeYAML's place is often where it noticed, a line or more after what is wrong, so the character there would
  mislead; its words say what it looked for.

## Consequences

- `jazygit-yaml` brings SnakeYAML Engine into the jar, and so into the native image, where its events need no
  reflection.
- The tree that is drawn, and folded by path, is the app's, made from what `jazygit-json` and `jazygit-yaml` read;
  neither reader knows of the other.
- A template of Helm, or any YAML made by a template, mostly shows as source.
- The words of a YAML that cannot be read are SnakeYAML's, in English only.
- Folding the source of a version, by brackets or by indentation, can still come later, with an ADR of its own.
