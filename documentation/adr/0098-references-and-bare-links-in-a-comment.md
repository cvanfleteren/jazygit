# 98. References and bare links in a comment

Date: 2026-11-10

## Context

ADR 96 opens a link of a comment with a click, but only a Markdown link: commonmark makes no link of a URL that is
only text, nor of `#381`, which GitHub shows as a link to issue or pull request 381 of the repository. Both are
common in the comments of a pull request: `Closes #290`, a URL pasted as it is. A long URL is wrapped over lines in
the narrow pane, where the terminal no longer finds it whole.

Issues and pull requests of a repository share one series of numbers, so `#381` does not say which it is. GitHub
sends `/<owner>/<repo>/issues/<n>` on to `/pull/<n>` when it is a pull request.

## Decision

- In the comment shown in full in the comments dialog, jazygit makes a Markdown link of:
  - `#N`, to `https://<host>/<owner>/<repo>/issues/N` of the repository of the pull request: its base, where the
    comment is, also for one from a fork. Nothing is asked of GitHub to know whether it is an issue or a pull request.
  - `owner/repo#N`, to `https://<host>/owner/repo/issues/N`, on the same host.
  - a bare `http` or `https` URL, to itself.
- Each shows as it is written, as any other link (ADR 96), and a click opens it as ADR 96 has it.
- `N` is 1 to 9 digits, without a leading zero. `owner` is letters, digits and `-`, at most 39; `repo` is letters,
  digits, `.`, `_` and `-`. A reference does not follow a letter, a digit, `/`, `\`, `&` or `#`, nor is its number
  followed by a letter or a digit: `page#12`, `#12abc` and `&#35;` stay text. An `_` between it and a word does the same.
- A bare URL ends as GitHub's autolinks end: at a space, `<`, `>`, `|`, `[`, `]`, `` ` `` or `\`, without the `.`, `,`,
  `:`, `;`, `!`, `?`, `*`, `_`, `~` and quotes it ends with, and without a `)` that no `(` in it opens. It follows the
  start of a line, a space, `*`, `_`, `~`, `(` or a quote, and has a host.
- Only text is linked, as commonmark reads it: not code, a code block, HTML, a link or the text of an image.
- When the repository of the pull request is not known, `#N` and `owner/repo#N` stay text; a bare URL is still linked.
- The Markdown is rewritten before it is drawn, as its images are (ADR 82), by where commonmark read its text.
- Not: the list of comments, which shows the Markdown as it is written; the log, whose subjects often hold `(#380)`;
  the review threads in a diff (`RemarkLines`). Each needs a click of its own.
- Not: `GH-N`, `@user` and the hashes of commits, which GitHub links too.
- Not: whether a reference is an issue or a pull request, or its title or state. That would ask GitHub for each one.

## Consequences

- `#N`, `owner/repo#N` and bare URLs in a comment open with a click, also when a URL is wrapped over lines.
- A reference to a number that is neither opens GitHub's page that says so.
- A reference to a repository on another host, written as a URL, is a bare URL; as `owner/repo#N` it is taken to be
  on the host of the pull request.
