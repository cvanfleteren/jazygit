# 106. A filter matches the first letters of words

Date: 2026-11-11

## Context

`/` filters the files pane (ADR 105) and the lists of popups through `ListFilter.matches`: a substring, ignoring case
unless the filter has an uppercase letter. Finding `CapitalCaseMatching.java` means typing a part of its name, such
as `Matching`, which may be in many other files too. IDEs find a class by the first letters of its words, `CCM`, or
by the starts of them, `CCMat`.

That is how Java and its kin name things, but files and branches elsewhere join words with `-` or `_`:
`can-can-mama.txt`, `feature/add_login`.

## Decision

A filter of two words or more also matches when its words start words following each other in the text.

- An uppercase letter in the filter starts a word: `CCMat` is `C`, `C` and `Mat`; `CCM` is `C`, `C` and `M`.
- It takes two words or more, of letters and digits only. A filter of one word, such as `Feat`, `Test` or `M`, and
  one with a `-`, `/` or other separator, match only as a substring, as before.
- The first letter of a word typed matches ignoring case, the letters after it as typed: `CCM` matches
  `can-can-mama.txt`, `CCMat` matches `CapitalCaseMatching.java`, `CCMAt` does not.
- The words follow each other, with no word left out between them: `CCM` matches `CapitalCaseMatching.java`,
  `CanCanMamaHere.txt` and `can-can-mama.txt`, not `CanCanDaddy.java` nor `CapitalFooCaseMatching.java`.
- A word of the text starts at the start of the text, after anything that is not a letter or digit (`-`, `_`, `.`,
  `/`, a space), at an uppercase letter after a lowercase one or a digit, and at the last of a run of uppercase
  letters that a lowercase one follows: `HTTPServer` is `HTTP` and `Server`.
- The words may run over a `.`, not over a `/`: they follow each other in one part of the text between `/`s. In the
  files pane the whole path is matched (ADR 105), so `FJ` matches `Foo.java` and `GC` a directory `github-checks/`,
  but `CM` does not match `commit/MessagePopup.java` nor `SMJ` `src/main/java/…`.
- A filter of lowercase letters only matches as a substring, as before: `ccm` does not match
  `CapitalCaseMatching.java`.
- It holds everywhere `ListFilter.matches` does: the files pane and the lists of popups. Searching the commits pane
  (ADR 4) does not change.

### Why this way

- Uppercase letters start the words typed, as in IDEs: `CCMat` narrows `CCM` without a separator to type.
- Two words or more: one word typed is a substring already, save for the case of its first letter. Matching that
  first letter ignoring case would make `Feat` match `feature` and `M` every path under `src/main/`, though an
  uppercase letter says the case matters. In the files pane, the rows shown are what staging a directory acts on
  (ADR 105), so rows shown by mistake would be staged.
- The first letter ignores case: `can-can-mama.txt` has no uppercase letter to match `CCM` against. The letters
  after it keep the case, as a filter with an uppercase letter does.
- Anything but a letter or digit separates words of the text, not only `-` and `_`: a path joins its parts with `/`
  and `.`.
- Not over a `/`: one types the starts of a file's name or of a directory's, not of the end of one and the start of
  the next. Running over it, `CM` matched `commit/MessagePopup.java`, a row one does not expect. A `.` stays inside
  a name, as in `Foo.java`.
- Words following each other: the starts of a name's words are what one remembers of it, and few paths have the
  same ones in a row.

## Consequences

- A filter of two words or more shows rows it did not before; it hides none it showed.
- Nothing shows which letters matched, as for a substring.
- Words left out between those typed, such as `CM` for `CapitalCaseMatching`, do not match.
