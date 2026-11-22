# 175. Each forge has its own pull-request dialogs

Date: 2026-11-22

## Context

The dialogs of a pull request (`show.PullRequestDialog`, `create.NewPullRequestDialog`, `edit.EditPullRequestDialog`)
are GitHub's, in `features-github`, on `core-gh`'s model: `PullRequestDetails`, `PullRequestStack`, `MergeMethod`, the
checks, the review, auto-merge and the review threads. ADR 174 brought a second forge, Forgejo (Gitea's API), which
shows how a branch's builds went, and leaves its pull requests for later.

The two forges differ in more than which rows they have: GitHub stacks pull requests (ADR 73, 114) and Forgejo has no
stacks; a draft is a flag on GitHub and a `WIP:` prefix of the title on Forgejo; GitHub has check runs and workflows,
Forgejo a commit's statuses; their merge styles, auto-merge and merge queue, and reviews do not match one to one.

GitHub binds its keys on the kernel's scopes itself (`o` on `Scopes.BRANCHES` shows the pull request of a branch;
8 keys on the branches, others on the remote branches, a diff with remarks, the commit's files, the repository and
everywhere). The keymap refuses a key bound twice in a scope (`Keymap.requireOnce`), whether the features that bind
it are on or off: a second forge cannot bind `o` on the branches as GitHub does.

## Decision

- **This decides how a second forge's pull-request dialogs relate to GitHub's**; how it reads them, which of them
  come first, and what else it shows are decisions of their own.
- **Each forge has its own dialogs of a pull request, showing, creating and editing it**, in its own feature module,
  on its own model. Not: one dialog on a model of every forge, with a seam for each thing one has and another lacks
  (stacks, drafts, auto-merge, the merge queue, verdicts): that model would be in a module both forges use, which
  deleting GitHub then leaves behind (ADR 171), and the dialog would grow a flag for each difference.
- **Gitea and Forgejo are one forge**, `forgejo` (ADR 174); where they differ, it tells them apart itself.
- **The forges look alike by convention**: where a thing is the same on both, their dialogs show it the same, with
  the same rows in the same order and with the same labels (state, author, branch, review, checks…), the same keys
  in the dialog and the same fixed height. Each forge's tests check its own dialog.
- **What a forge does not have is not in its dialog; what does not apply now is disabled**: Forgejo's has no stack
  and no merge queue, while merging a pull request that conflicts is shown disabled, with why, as everywhere.
- **Widgets are shared once a second dialog uses them**: when Forgejo's dialog is made, what it takes of GitHub's (the
  labelled rows, `CheckRows`, the actions in two columns, `PullRequestSymbols` if its badges use them) moves to
  `kernel-ui`'s `widgets`, on types of the kernel's own, as a check's name, state, progress and times; GitHub maps its
  own to them. Nothing moves before.
- **Forgejo's first dialog has no stacks**; pull requests whose bases follow one another could be shown as one later,
  as Forgejo's own.
- **The forges bind the same keys, each with its own texts** (`forgejo.help.*`), so that what is learnt on one works
  on the other.
- **Features that are on or off for a repository may bind one key in one scope, once each**, the keys of the scopes
  it hosts counted with its own (ADR 161), and one global key alike: `requireOnce` refuses a key bound twice only
  when a binding of it is the kernel's or of a feature that is always on, or when one feature binds it twice. The
  keys a feature adds are its own as they are added (`Keymap.owned`), so the check sees whose they are.
- **Such a key sends what the first of them that is on sends**: `Keymap.handle` gives a `FeatureMsg.FirstOn` of what
  each of their bindings sends, in the order of `Features.ALL`, and the update runs the first whose feature is on, as
  ADR 172's fills take what the first feature gives. A binding that sends nothing, as without a selection, is left
  out. A key of a scope gathers its own bindings and those of the scopes it hosts, and `handleGlobal` the global
  keys, in the same way. The help lists the key once, as the first of them that is on has it.

## Consequences

- A forge stays one module and its core, deletable as GitHub is (ADR 171, 173); the kernel gains no model of a pull
  request.
- What the dialogs of both forges show alike is kept alike by hand and by their tests, not by one class.
- A feature binding a key the kernel or a feature that is always on binds is still refused as the keymap is made; a
  feature that is on or off can no longer clash with itself. Two of them that are on at once share a key without
  being told: ADR 174 turns Forgejo on with one remote on it, so a repository with remotes on both GitHub and Forgejo
  has both on, and GitHub, first in `Features.ALL`, has the key. Choosing the forge of the selected branch's remote
  is left for a decision of its own, once Forgejo binds keys GitHub does.
- Not: a test of the app that each forge, on alone, has no clashing keys: the keymap refuses those as it is made.
- Not: keys of the panes that no forge names ("the pull request of this branch"), sending messages of the kernel
  that the forge on answers: every action of a forge that wants a key would need a message of the kernel.
