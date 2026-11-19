# 151. A version of XML rendered

Date: 2026-11-19

## Context

A version of JSON or YAML can show rendered, as a tree that folds (ADR 122, 123), read into one tree of the app and
drawn by one renderer. XML is the other text of configuration a repository holds: the `pom.xml` of a build, a schema,
a stylesheet, a project of .NET, a property list, a page of XHTML. Its source is a tree already, and is long: a
`pom.xml` of 400 lines, most of them tags, is 40 dependencies. What helps the reader of XML is what helps the reader of
JSON: a long list put out of the way, a list of records read as a table, the tags gone.

XML is not data as JSON and YAML are. An element has a name, attributes and children; its children are elements,
text and comments, in an order that matters; elements of the same name follow each other where JSON has an array.
Text and elements can be mixed, `<p>Hello <b>you</b>!</p>`. A comment often matters: a dependency commented out, a
note in a schema.

The JDK reads XML with StAX, with no dependency. Its location of an event is where the event ends, after the `>` of a
tag and after the `<` that follows a text, so the line a tag starts on has to be found again in the text. It stops at a
reference to an entity no DTD declares, `&nbsp;`, as XHTML has them, even with DTDs off. XML that is well formed is
simple to read: tags, attributes, text, references, comments, sections of CDATA, instructions and a DOCTYPE.

## Decision

### What is rendered

- A version of an XML file can show rendered, as a version of JSON can (ADR 122): Ctrl+T, kept with the version, its
  nodes folding, with the cursor, the keys and the marks of where it changed that JSON rendered has. A file is XML by
  its extension: `.xml`, `.xsd`, `.xsl`, `.xslt`, `.wsdl`, `.xhtml`, `.fxml`, `.plist`, `.csproj`, `.fsproj`,
  `.vbproj`, `.props` and `.targets`, a line in ADR 103's table of renderers. Not: `.html`, which is mostly not well
  formed. Not: `.svg`, mostly the data of its paths, and to be drawn as a picture if anything.
- XHTML is drawn as a tree, as any XML. Not: drawn as a page, as Markdown is, which needs a layout of HTML.
- The limits of ADR 122 hold as they are: a version cut at the limit of ADR 79 is shown as source, and so is XML
  nested deeper than it is read; rows know the lines they stand for, not their columns; while rendered, `y` and `v` do
  nothing, a thread cannot be started and no review threads are shown; Ctrl+T maps the cursor between a row and its
  line.

### How it is read

- A module of its own, `jazygit-xml`, reads XML by hand: each element with the line its start tag starts on, the line
  that tag ends on and the line it ends on, its attributes, and its children: elements, text and comments, each with
  its lines. It reads what is well formed, and nothing more: no DTD is read, no entity is declared, nothing is fetched,
  from the network or from disk. Not: StAX, which says where an event ends, not where it starts, and stops at
  `&nbsp;`. Not: a library, for what is 400 lines.
- A reference to one of the five entities of XML, `&amp;`, and to a character, `&#233;`, is undone. A reference to any
  other entity stays as it is written, `&nbsp;`.
- The declaration, `<?xml?>`, instructions and the DOCTYPE are read past, and not kept.
- Names are kept as written, prefixes too, `xs:element`. A declaration of a namespace, `xmlns:xs="…"`, is an attribute.
  Not: names resolved to the URI of their namespace.
- Text that is only white space is dropped; other text is trimmed, and its lines lose the indentation they share. A
  section of CDATA is text, kept as such.
- Text that is not well formed is shown as source, under one line saying where reading stopped and why, in jazygit's
  words: `Not rendered: line 12, column 3, </dependency> closes <version>`, `expected '>'`,
  `the text ends inside <project>`, `attribute 'id' written twice`, `more after the root element`.
- What it finds not well formed: an end tag that closes another element, or none; an element not closed; a second root,
  or text outside the root; an attribute written twice, without `=`, without quotes, or with no white space before it;
  a `<` in the value of an attribute; an `&` that starts no reference; a reference to a character XML does not allow,
  `&#0;`; a character XML does not allow anywhere, a control character, U+FFFE, U+FFFF or half of a surrogate pair;
  `--` in a comment; `]]>` in text; the declaration anywhere but at the start; a comment, a section of CDATA, an
  instruction or a DOCTYPE not closed; a name that does not start as one; elements nested deeper than 500.
- What it does not find, and draws as a tree: a reference to an entity no DTD declares, `&nbsp;`, on purpose, as
  XHTML has them; a name with a character XML does not allow in one, other than at its start, as its rules of the
  characters of names are approximated by those of letters and digits; a prefix no `xmlns` declares, or any other rule
  of namespaces; what the declaration says, its version, encoding and `standalone`; a DOCTYPE that is not as XML has
  it, or written twice, as it is only read past, its strings, comments and instructions with it.
- What it does not do, as it reads no DTD: an entity the DTD declares is drawn as it is written, `&e;`, not as what it
  stands for; an attribute the DTD gives a default is not drawn. The value of an attribute is not normalized: a line
  break in it is drawn as `↵`, not as a space. The text is read as git gives it: the encoding the declaration names is
  not read. XML 1.1 is read as XML 1.0.
- It reads in time linear in the text: its elements, attributes and children are gathered in vectors, as appending to a
  list copies it, which made 40,000 siblings take three seconds.

### How it is drawn

- XML is read into the tree of the app that JSON and YAML are read into, as nodes of its own: an element, a text and a
  comment. One renderer draws them all, with the guides, the counts, the tables, the previews and the folding of
  ADR 122. Not: XML made into mappings and sequences, `@attribute` and `#text`, as converters of XML to JSON do, which
  loses order and reads oddly. Not: a renderer of its own.
- The root element is a row, as its name and attributes say what the file is: `project xmlns="…" {15}`. The comments
  before and after it are rows beside it.
- An element is its name, then its attributes as written, `name="value"`, then what it holds. Of one that holds
  elements, text or comments, the count follows: `[12]` when all are elements of one name, `{4}` otherwise, and closed,
  a preview of the names of its children, or of the children of its first: `dependencies [12]  {groupId, artifactId…}`.
  A declaration of a namespace is dim.
- An element that holds nothing is one row, `br`, `xs:element name="id" type="xs:int"`. One that holds text on one
  line is one row, its text after it unquoted: `version  1.2.3`. One that holds text of more lines is a node, with a
  count of its lines, `description ≡5`, and a row for each line, which stands for its own line.
- In an element that holds elements and text, mixed, each text is rows in its place among the elements, a row for
  each of its lines. A text does not fold.
- A section of CDATA is drawn as its text, with a dim `CDATA` before it.
- A comment is a dim row, `<!-- a note -->`. One of more lines is a node, `<!-- ≡15`, with a row for each line; closed,
  it shows its first line.
- Elements of one name in an element that holds nothing else, but comments of one line, are a table when there are two
  or more, each holding only attributes and elements of text on one line or of none, of names it has once, with no
  attributes: a column for each attribute, `@scope`, and each element, in the order they are first seen, the cells of
  an element that lacks one blank. Each row is named by its index, from 1, and stands for the lines of its element; a
  comment between is a row of its own. Not: the same names in the same order in each, as ADR 122 has it for JSON, as an
  element left out, `<scope>`, is how XML is written. JSON keeps its rule.
- An element is not named by an attribute or a child, as an object in an array is: its attributes are on its row, and
  records are a table.
- A long row, of many attributes, scrolls sideways; it is not wrapped.
- The colours are those of the syntax of XML as source (ADR 53): the names of elements as tags, the values of
  attributes as strings. The names of attributes and text are plain. Guides, counts, previews, comments, declarations
  of namespaces and `CDATA` are dim. No styles of the theme of their own.

### Folding

- A node is named by a path, as XPath writes one: the names of its elements, an index from 1 where it has siblings of
  the same name, `/project/dependencies/dependency[3]`; a comment as `comment()`, a text as `text()`. What the user
  chose for a node is kept by its path, as for JSON.
- A node of more than 20 children, or lines, starts closed, as in JSON. The root element starts open, whatever it
  holds, and can be closed. `z` closes and opens every node below the root element.

## Consequences

- The tree of the app holds elements, texts and comments beside mappings and sequences; its renderer draws both.
- `jazygit-xml` is a reader of XML written by hand, which reads well formed XML and says where most other XML is not;
  it brings no dependency, and nothing of it needs reflection in the native image. Some XML that is not well formed is
  drawn as a tree, as listed above: a version rendered is no validator, and its source is a Ctrl+T away.
- Each rule of well-formedness it checks is code jazygit keeps; a rule of XML it misses is found by a file drawn
  wrongly, not by a library's tests.
- An entity declared in a DTD is drawn as it is written, not as what it stands for.
- A `pom.xml` reads as its dependencies in a table, as long as no dependency holds its exclusions.
