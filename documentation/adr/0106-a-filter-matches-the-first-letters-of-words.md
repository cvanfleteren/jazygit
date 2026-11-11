# 106. A filter matches the first letters of words

Date: 2026-11-11

## Context

`/` filters the files pane (ADR 105) and the lists of popups through `ListFilter.matches`: a substring, ignoring case
unless the filter has an uppercase letter. Finding `CapitalCaseMatching.java` means typing a part of its name, such
as `Matching`, which may be in many other files too. IDEs find a class by the first letters of its words: `CCM`.

That is how Java and its kin name things, but files and branches elsewhere join words with `-` or `_`:
`can-can-mama.txt`, `feature/add_login`.

## Decision

A filter with an uppercase letter also matches when it is the first letters of words following each other in the
text, ignoring case.

- A word starts at the start of the text, after anything that is not a letter or digit (`-`, `_`, `.`, `/`, a
  space), at an uppercase letter after a lowercase one or a digit, and at the last of a run of uppercase letters that
  a lowercase one follows: `HTTPServer` is `HTTP` and `Server`.
- The words follow each other, with no word left out between them: `CCM` matches `CapitalCaseMatching.java`,
  `CanCanMamaHere.txt` and `can-can-mama.txt`, not `CanCanDaddy.java` nor `CapitalFooCaseMatching.java`.
- The words may run over a `/` or a `.`: in the files pane the whole path is matched (ADR 105), so `SMJ` matches
  `src/main/java/…` and `FJ` matches `Foo.java`.
- Each letter typed is the first letter of a word, nothing more: `CCMat` does not match `CapitalCaseMatching`.
- A filter of lowercase letters only matches as a substring, as before: `ccm` does not match
  `CapitalCaseMatching.java`.
- It holds everywhere `ListFilter.matches` does: the files pane and the lists of popups. Searching the commits pane
  (ADR 4) does not change.

### Why this way

- Only with an uppercase letter: typing uppercase already says the case of the letters matters, and a short
  lowercase filter such as `te` would otherwise also show every `TreeElement`. It keeps the filter as it was for
  those who never type uppercase.
- The first letters ignore case: `can-can-mama.txt` has no uppercase letter to match `CCM` against.
- Anything but a letter or digit separates words, not only `-` and `_`: a path joins its parts with `/` and `.`.
- Words following each other, without a letter after each initial: as simple to explain as a substring, and the
  initials of a name are what one remembers of it.

## Consequences

- A filter with an uppercase letter shows rows it did not before; it hides none it showed.
- Nothing shows which letters matched, as for a substring.
- Matching the first letters and more of a word, `CCMat`, or words with others between them, is left for later.
