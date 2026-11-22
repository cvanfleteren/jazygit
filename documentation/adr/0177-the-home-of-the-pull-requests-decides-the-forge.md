# 177. The home of the pull requests decides the forge

Date: 2026-11-22

## Context

ADR 171 lets a forge decide whether it applies to a repository, once, as it opens. GitHub applies with any remote on
a GitHub host and gh found; Forgejo (ADR 174) with any remote on a host that answers as Forgejo does. A repository
with remotes on both, as a project on GitHub with a mirror on Codeberg, has both on, and ADR 175 then lets the first
in `Features.ALL` have a key both bind.

Pull requests and reviews of a repository happen on one forge: a mirror on another only receives pushes. Two remotes
on one forge are common, though: in the fork workflow `origin` is one's fork and `upstream` the project, and the pull
requests are opened against `upstream`, with their head in the fork.

gh reads the pull requests of the repository `gh repo set-default` chose, which it marks on that remote
(`remote.<name>.gh-resolved` is `base`), else of the remote named `upstream`, then `github`, then `origin`.

## Decision

- **A repository's pull requests have a home: the remote they are opened against**, `PullRequestHome` in `core-repo`,
  whichever forge it is on: the remote set for it, else `upstream`, else `origin`, else the only remote. None without
  remotes, or among several with none set and none of those names.
- **Only the forge of the home applies**: each forge's detection asks whether the home is on it, not whether any
  remote is. Every other forge is off for the repository, its badges too. Not: a forge on with any remote on it, and
  the pull requests alone decided by the home, as two forges then apply where only one is worked on.
- **For GitHub, the home set is the remote `gh repo set-default` chose**, read from git's config
  (`Remotes.setting`), as gh reads pull requests from it. Forgejo has no such setting yet; a setting of jazygit's for
  the home, for both, is left for when it is missed.
- **The home is decided once, as the repository opens** (ADR 171): a remote added or renamed counts at the next
  opening; a home on Forgejo that cannot be reached then leaves Forgejo off until then (ADR 174).
- **A branch's pull request is the open one in the home whose head is the branch's push target**
  (`Branch.pushTarget`): the same repository, that of the remote it pushes to, and the same branch. A pull request
  from one's fork into the project is found; one from another fork with a branch of the same name is not. Forgejo
  does so with its pull requests (ADR 176); GitHub, matching by the name of the branch alone now, does so in a change
  of its own.
- **ADR 175's keys that features bind alike stay**, so that two forges' keys do not make the keymap refuse them; with
  one forge on, the first that is on is the only one.

## Consequences

- One forge per repository: the case of two on at once, and which has a shared key, no longer happens.
- A mirror on another forge shows nothing of that forge, not even its builds.
- A repository with several remotes and none named `upstream` or `origin` has no forge, until a setting for the home
  exists.
- The fork workflow finds the pull request of a branch on both forges.
- ADR 174 and 175 get a line "Amended by ADR 177".
