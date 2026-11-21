# 169. Properties in the rich view

Date: 2026-11-21

## Context

The lexer of ADR 53 finds keywords, strings, comments and numbers, and annotations since ADR 113. Names are not
coloured, as a scanner cannot tell them apart reliably. Much of a Nix file is names given values, in attribute sets,
in `let` and in `rec { … }`: `services.nginx.enable = true;`, `version = "1.0";`. Editors and GitHub colour those
names, and without them a NixOS module in the rich view is mostly plain text.

Like an annotation, such a name says what it is where it stands: a name, or a path of names with points between them,
followed by one `=`. What else looks like one:

- A comparison, `a == b`: two `=`.
- A name only read, `pkgs.hello`, or a default of an argument, `{ cfg ? null }:`: no `=` after it.
- A name followed by its `=` on the next line, which a scanner of one line cannot see; rare in Nix.

Nix's names also hold dashes and quotes after their first character, `foo-bar` and `x'`, which the lexer cut at the
dash: it coloured only `bar` of `foo-bar`, and the `inherit` of `inherit-x` as a keyword.

## Decision

- A sixth kind of token, a property: a name a value is given to, with the points of its path, like
  `services.nginx.enable`. In Nix only, for now; a `Grammar` says whether its language has them
  (`withProperties()`), as it says so of annotations.
- A name is a property when one `=` follows it, after spaces and tabs on the same line, and not `==`. A name of the
  path in quotes (`"foo-bar".x = 1;`) is a string, and the rest of the path after it is not coloured.
- A `Grammar` says which characters its names hold besides letters, digits and underscores (`withNameChars`): `-` and
  `'` in Nix. A keyword is matched against the whole name, so the `then` of `then-y` is none.
- The class `syntax-property`, set in every theme that colours syntax, in a colour none of its other tokens have. The
  GitHub themes take GitHub's colour of the keys of an object, green, and their colour-blind ones its purple of
  names. Mono sets none, so a property is drawn in the plain colour there, as is every name.
- Not: the names after `inherit` (`inherit pkgs lib;`), which give values too but are told by the `inherit` before
  them rather than by what follows, so the scanner would have to carry them from word to word. Nor the arguments of a
  function, `{ pkgs, lib, ... }:`, which are only known at the `}:` after them.

## Consequences

- The properties of a Nix file are coloured, in the colour of its theme.
- A property whose `=` is on the next line is not coloured, nor are the names after `inherit` and the arguments of a
  function.
- The same rule fits the keys of TOML (`name = …`) and, with `:` in place of `=`, of YAML and JSON; a language takes
  it on with `withProperties()`, which only Nix does here.
- A theme has one more class to keep.

Amends ADR 53, which coloured no names, and ADR 113, which added the fifth kind of token.
