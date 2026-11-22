# 178. Creating a pull request on Forgejo

Date: 2026-11-22

## Context

ADR 176 shows the pull request of a branch on Forgejo with `o`, and tells a branch without one so in the command
log. On GitHub, the same key leads to creating one (ADR 18, 25): the branch is pushed first when it needs to be, then
one dialog asks what it merges into, its title, its body and whether it is a draft. ADR 175 gives each forge its own
dialogs, alike by convention, and moves a widget to `kernel-ui` once a second dialog uses it.

Forgejo's API creates a pull request with `POST /repos/{owner}/{repo}/pulls`: `head`, `base` and `title` are
required, `body` is not. A head in another repository is written `owner:branch`, and must be that owner's fork of the
repository, one hop away; else the answer is 404, as it is without the right to read either repository. There is no
draft field: a pull request is a draft when its title starts with one of the server's `WORK_IN_PROGRESS_PREFIXES`,
`WIP:` and `[WIP]` by default, which no endpoint gives. It answers 201 with the pull request; 401 without a token;
403 when the token has no `write:repository` scope; 409 when an open pull request has the same head and base, naming
its number only in the text of the message; and 422 when head and base are the same string. A head with no commit
that the base lacks is not refused: the pull request is created, empty. Gitea writes the head the same way.

## Decision

- **`o` on a branch without an open pull request creates one**, on Forgejo as on GitHub, instead of telling so in the
  log. No other key.
- **First, the head is checked**: the branch the branch is pushed to (`Branch.pushTarget`) must be in a remote on the
  host of the home (ADR 177), as Forgejo opens a pull request only from a repository on its own host. Another host, or
  a remote that is not known, is refused, saying why, before anything is pushed or asked. Not: a check that the
  repository is the owner's fork of the home's, which takes another call to tell what the 404 of creating it says.
- **The branch is pushed first, as on GitHub (ADR 25)**: a confirmation when it does not track a remote branch or has
  commits its upstream does not, an error when it diverged from it. Forgejo's popup and texts are its own; the push is
  `features-branches`' `PushCmd`.
- **Forgejo's own dialog, laid out as GitHub's (ADR 18)**: the bases, the title, the body, which starts empty, and the
  draft box; the same keys, header and height, and no box to stack it (ADR 175). The list of bases is `BasesField`,
  which moves to `kernel-ui`.
- **The bases are the branches of the home's remote** (`upstream/*` in a fork), whether or not one has them locally:
  in a fork, the local `main` usually tracks one's own fork, so the local branches would offer nothing to merge into
  the project. The branch's recorded parent comes first, then the one of the name of the default branch, then the
  rest. The title starts as the subject of the oldest commit of the branch not on the base, read again when another
  base is picked while the title is the one read (ADR 18). Not: GitHub's reading of the closest base when no parent is
  recorded.
- **The head is sent as `branch` when it is in the home's repository, and `owner:branch` when it is in another
  remote's.** A 404 then says that the repository of that remote must be a fork of the home's that one may read.
- **The draft box puts `WIP: ` before the title as it is sent**, once: not when the title starts with `WIP:` or
  `[WIP]` already. The title field keeps what was typed. A server whose prefixes are others does not see it as a
  draft.
- **A branch with no commit that the base lacks is refused before anything is sent**, saying so: an empty pull
  request is a mistake, as of a wrong base.
- **Errors say what to do**: 401, to run `fj auth login` for the host; 403, that the token may not create pull
  requests in the repository; 409, that the branch already has pull request #N, when its number can be read from the
  message, as the list of the pull requests may be older than a pull request opened in the browser, which is then read
  again; others, with Forgejo's message.
- **Once created, it is the branch's pull request at once**, in Forgejo's part, and the open pull requests are read
  again, as after any operation that changes the forge. The dialog of the pull request does not open by itself: `o`
  shows it.
- **Assignees, labels, a milestone, reviewers and the repository's pull request template are not in the dialog**, as
  GitHub's has none of them.

## Consequences

- A branch on Forgejo gets its pull request created in jazygit, from one's fork too, with fj's token; without a token,
  the error says how to get one.
- The two forges' create dialogs look and work alike; what they share is `BasesField`. What a dialog does with what
  the user picks and types is each forge's own, and so are the reads of the parent and the title.
- Creating needs a token with the `write:repository` scope: one that only reads, as for a private repository's
  builds, gets the 403's error.
- No acceptance test covers it, for the reason of ADR 176. The feature's tests run its update, commands and dialog on
  a Forgejo of the test's, and core-forgejo's run the request against a server of the test's.
