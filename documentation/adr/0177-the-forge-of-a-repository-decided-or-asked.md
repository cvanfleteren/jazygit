# 177. The forge of a repository, decided or asked

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
(`remote.<name>.gh-resolved` is `base`), else, among the remotes on hosts it knows, of the one named `upstream`, then
`github`, then `origin`.

Each forge working the home out by itself, by such a rule, makes them disagree: GitHub honours `set-default`, Forgejo
has no such setting, and a rule over all the remotes, without the hosts, picks a remote on no forge, as an `upstream`
on a plain git server. In a setup that mixes forges any rule guesses; the user knows.

## Decision

- **A forge tells which remotes of a repository are its own**: its detection (ADR 171) answers
  `FeatureMsg.Claimed`, a `ForgeClaim`: the remotes on it with their URLs, those it could not tell, as one on a host
  that could not be reached, and the remote its own setting names the home, if any (GitHub: the one
  `gh repo set-default` chose). Not: whether it applies, yes or no.
- **The kernel decides once every forge told its claims, and what the user chose before was read**, naming no forge
  (`ForgeDecision`, `ForgeUpdate` in `kernel-state`):
  - a remembered remote that is claimed is the home; a remembered "no forge" stands;
  - else one remote claimed is the home;
  - else several claimed by one forge are picked by the rule (`PullRequestHome` in `core-repo`): the one the forge
    prefers, else `upstream`, `github`, `origin`;
  - else the user is asked: when remotes of more than one forge are claimed, or the rule picks none.
- **A remote a forge could not tell never makes the kernel ask, nor pick another forge**: it decides from what is
  certain, and what it would ask waits for a later opening, with no forge meanwhile. A remembered remote that a
  forge could not tell gives no forge this session, without asking.
- **It asks with a choice as the repository opens** (`ForgeChoicePopup`): each remote claimed, numbered, by its
  forge, its name and its URL, and "No forge", the rule's pick highlighted. Until the user answers, no forge is on.
  Escape puts it off: no forge this session, and the question comes back at the next opening.
- **The answer is kept in the repository's git configuration**, `jazygit.forgeHome`: the remote, or empty for no
  forge. Not: the per-repository store of `kernel-settings`, which keeps a repository by its `owner/name`, a name only
  a forge knows. The settings panel has a row, "Forge of this repository…", that asks again from the claims of the
  opening; it is disabled when no forge claimed a remote. A remembered remote that is gone, or no longer claimed, is
  asked again, the rule's pick highlighted.
- **Only the forge of the home is on**: every other forge is off for the repository, its badges too. Choosing another
  switches the one before off and the chosen on. The kernel keeps the home in the model (`ForgeHome.of(model)`), from
  which the forge on reads its pull requests.
- **A branch's pull request is the open one in the home whose head is the branch's push target**
  (`Branch.pushTarget`): the same repository, that of the remote it pushes to, and the same branch. A pull request
  from one's fork into the project is found; one from another fork with a branch of the same name is not. Forgejo
  does so with its pull requests (ADR 176); GitHub, matching by the name of the branch alone now, does so in a change
  of its own.
- **The home is decided once, as the repository opens** (ADR 171): a remote added or renamed counts at the next
  opening.
- **ADR 175's keys that features bind alike stay**, so that two forges' keys do not make the keymap refuse them; with
  one forge on, the first that is on is the only one.

## Consequences

- One forge per repository: the case of two on at once, and which has a shared key, no longer happens.
- The usual setups, one forge, or a fork and its project on one forge, are never asked about; a mix of forges is asked
  once.
- A mirror on another forge shows nothing of that forge, not even its builds.
- Detection waits for every forge: a Forgejo host that cannot be reached delays the decision by its connect timeout,
  in the background, while the repository is usable without a forge.
- The fork workflow finds the pull request of a branch on both forges.
- A test of a forge's detection checks what it claims; whether it applies is the kernel's, tested once
  (`ForgeDecisionTest`, `ForgeUpdateTest`).
- ADR 171, 174 and 175 get a line "Amended by ADR 177".
