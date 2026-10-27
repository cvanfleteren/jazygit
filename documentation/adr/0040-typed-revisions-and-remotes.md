# 40. Typed revisions and remotes

Date: 2026-10-28

## Context

The whole-project review of 2026-10-24 found several bugs with one root: what jazygit hands git as a revision or a
remote is a plain `String`, put together at each call site.

- Renaming a branch to `--force` overwrote another branch (#123): a name read as an option.
- Merge and rebase passed bare branch names, so a tag of the same name won (#126).
- A pull request was created with the local name of its base instead of its name on the remote (#138).
- A new branch made from `origin/develop` follows it, and `P` then pushed it onto `develop`.

Each was fixed where it happened. What is left as a `String`: the start point of a new branch or worktree, the
target of a reset, the revision `resolve` reads, the remote of `RemoteBranch` and of a fetch, the `Refs` helpers, and
the head and base names and the head sha handed to gh. A full ref is spelled in four places (`Refs`,
`BranchRef.ref()`, `RemoteBranch.trackingRef()`, `ResetTarget.revision()`), and nothing stops a fifth from using
`name.value()`.

The last bug is not one of typing. A new branch made from a remote-tracking branch follows it because of git's
`branch.autoSetupMerge`, whose default is `true`. Git then keeps it from pushing there: with the default
`push.default=simple`, `git push` refuses when the upstream has another name than the branch. jazygit does not ask
git where to push: `CliPush` writes `<name>:refs/heads/<upstream>` itself, as `push.default=upstream` would, for
every user. Deleting such a branch "on the remote" deletes `develop`, and the dialog never names it.

What `git push` does without a refspec (checked with git 2.56):

| `push.default`       | What it pushes                                                             |
|----------------------|----------------------------------------------------------------------------|
| `simple` (default)   | the checked out branch, to the same name; refuses when its upstream has another name |
| `current`            | the checked out branch, to the same name                                   |
| `upstream`/`tracking`| the checked out branch, to its upstream                                    |
| `matching`           | every branch that has a branch of the same name on the remote              |
| `nothing`            | nothing: it refuses                                                        |

## Decision

### Tracking is the user's git configuration

- Creating a branch passes neither `--track` nor `--no-track`, so `branch.autoSetupMerge` decides: a branch made from
  a remote-tracking one follows it by default, and not with `false`; with `always` or `inherit` it follows what git
  says. Not: `--no-track` always, which overrides a setting the user may have chosen.
- Only checking out a remote branch, and a new worktree for one, passes `--track`: following it is what was asked.
- Which of the two is a type, `NewBranchStart`: `From(Revision start, Option<BranchName> parent)` or
  `Following(RemoteBranch upstream)`. It replaces the `String start` and `boolean track` of the new worktree branch.

### Push goes where `push.default` says

- Pushing a branch reads `push.default` (git's effective value, `simple` when unset) when it runs, and pushes the
  branch to its upstream's remote:
  - `simple`: to the branch of the same name; when the upstream has another name, it refuses, as git does.
  - `current` and `matching`: to the branch of the same name.
  - `upstream` and `tracking`: to the upstream.
  - `nothing`: it refuses.
- It still names the branch in the refspec, as `<name>:refs/heads/<target>`. Not: a plain `git push`, which only
  pushes the checked out branch, while jazygit pushes any branch; and which with `matching` would push every
  matching branch where one was asked for.
- A refusal is `GitError.PushRefused(String pushDefault, RemoteBranch upstream)`. The push then asks where to push
  the branch, in the popup that asks for the upstream of a branch without one: it starts out as the branch of the same
  name, and says why it is asked. Typing the upstream pushes there, as asked for. Not: an error, after which the user
  can do nothing in jazygit.
- A force push goes where a push goes; its lease is then that of the target.
- Pushing to an upstream typed by the user goes there, whatever `push.default` says: it was asked for.
- What stays out: `remote.pushDefault` and `branch.<name>.pushRemote` (the push goes to the upstream's remote, as
  before), and `remote.<name>.push` refspecs.

### Deleting on the remote says which branch

The choices of the delete dialog that act on the remote name the remote branch, e.g.
`delete remote branch origin/develop`. When that branch has another name than the local one, they say so.

### A revision is a type, with one way to write it

- `Revision`, in `git.model`, is sealed. `BranchRef` (`Local`, `Remote`) extends it, with `Commit(Sha)` and `Head()`.
- `toArg()` is what git is given: always a full ref (`refs/heads/main`, `refs/remotes/origin/main`), a full sha, or
  `HEAD`; never a bare name. A tag of the same name cannot win, and no revision can start with `-`. `label()` is what
  the user sees: `main`, `origin/main`, `abc1234`.
- `BranchRef.ref()` becomes `toArg()`, `ResetTarget.revision()` a `Revision`, and the `Refs` helpers go into it.
- `Sha.parse` only takes 4 to 64 hex digits.
- What the user types as a start is not a revision until git says what it is: `resolve` runs
  `git rev-parse --verify --end-of-options <text>^{commit}` in the command, and the worktree starts at that
  `Commit`. Not: a `Revision` that holds typed text, which would be the `String` again.

### A remote is a type

`RemoteName`, in `git.model`: `parse` refuses a blank name, whitespace, and a leading `-`. It is the remote of
`RemoteBranch`, of a fetch and of its messages, and `DEFAULT_REMOTE`.

### Git commands are built with their arguments typed

- `GitArgs` builds the arguments of a git command: `option(String)` for what jazygit spells itself, and
  `revision(Revision)`, `branch(BranchName)`, `remote(RemoteName)` and `paths(...)` for the rest. For a command that
  takes paths, it puts `--` in front of them, or at the end when there are none, so a revision is never taken for a
  path.
- Commands that take no paths (`merge`, `rebase`, `push`, `fetch`, `worktree add`) get no `--`; what keeps their
  arguments from being read as options is that none of the types above can start with `-`.
- `ArchitectureTest` fails when a class of `git` other than `GitArgs` and the types themselves calls
  `BranchName.value()`, `Sha.value()`, `RemoteName.value()` or `Revision.toArg()` to build a command.

### gh is given the same types

- `createPullRequest(RemoteBranch head, RemoteBranch base, ...)`: a name on the remote, which a local name cannot be
  passed for. `browse(RemoteBranch)`.
- `mergePullRequest(int, Option<Sha> head, ...)`: the "unless empty" becomes an `Option`.
- `PullRequestDetails` has the head as a `BranchName` and its sha as a `Sha`; `PullRequestPreview` its base as a
  `BranchName`. The parser decides, not the code that reads them.

### Steps

1. Push follows `push.default`, and deleting on the remote names the branch.
2. `Revision`, `RemoteName`, and a stricter `Sha`, with `BranchRef` a `Revision` and `Refs` folded in.
3. Start points are typed: `NewBranchStart`, and a typed start resolved to a commit.
4. `GitArgs`, every `Cli` class built with it, and the `ArchitectureTest` rule.
5. `RemoteName` from fetching to pushing, through the app.
6. gh's head, base and sha typed.

## Consequences

- A branch made from `origin/develop` still follows it, unless the user's configuration says otherwise; with the
  default configuration, `P` asks where to push it instead of pushing onto `develop`. A user with
  `push.default=upstream` pushes onto `develop`, as plain git would.
- The branches pane shows the upstream git set, as before.
- A user with `push.default=nothing` is asked where to push every branch.
- The tests that compare command lines change with steps 1, 2 and 4: a branch is passed as `refs/heads/<name>`.
- A new `Cli` method can only hand git a revision, branch or remote through `GitArgs`.
- Stash refs (`Stash.ref`) are still a `String`; they are left for later.
- gh's `owner:branch` head for a pull request from a fork stays unsupported.
