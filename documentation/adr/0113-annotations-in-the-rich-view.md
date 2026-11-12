# 113. Annotations in the rich view

Date: 2026-11-12

## Context

The lexer of ADR 53 finds keywords, strings, comments and numbers. Names, types, annotations and punctuation are not
coloured, as a scanner cannot tell them apart reliably. GitHub colours annotations, and a Java diff of a Spring or a
Lombok class has many of them: `@Override`, `@Component`, `@RequiredArgsConstructor`.

Unlike a name or a type, an annotation says what it is where it starts: an `@` followed by a name. What else looks
like one:

- An address, like `mail@example.com`: in a string or a comment already, and after a word.
- The tags of a doc comment, like `@param` and `{@link Foo}`: in a comment, unless a hunk starts inside one, which
  ADR 53 lexes from outside of everything.
- Python's operator of matrix products, `a @ b`.

## Decision

- A fifth kind of token, an annotation: an `@` followed by a name, and points followed by names, like
  `@lombok.Getter`. Its arguments are not part of it. In Java, Kotlin, JavaScript and TypeScript (decorators) and
  Python (decorators); a `Grammar` says whether its language has them.
- An `@` starts no annotation:
  - right after a letter, a digit or an underscore, as in an address;
  - right after `{`, as in `{@link Foo}` and `{@code x}`;
  - on a line that starts with `*` after spaces, but not with `*/`: a line of a doc comment, as in ` * @param x`.
  So the tags of a doc comment in a hunk that starts inside it are drawn as before.
- The class `syntax-annotation`, set in every theme that colours syntax: in its colour of numbers, as GitHub does,
  and in Darcula in IntelliJ's yellow. A theme without it draws annotations in the plain colour.

## Consequences

- Annotations and decorators are coloured in the languages above.
- Python's `a @b`, a matrix product without a space after the operator, is coloured as a decorator.
- In a hunk that starts inside a doc comment, a tag on a line that does not start with `*` and is not in braces is
  coloured as an annotation; Javadoc lines nearly always start with `*`.
- A theme has one more class to keep.

Amends ADR 53, which coloured no annotations.
