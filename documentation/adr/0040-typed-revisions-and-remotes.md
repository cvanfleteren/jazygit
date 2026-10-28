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

- Pushing a branch reads `push.default` (git's effective value, `simple` when unset) when it runs, as a
  `PushDefault`, and pushes the branch to its upstream's remote:
  - `simple`: to the branch of the same name; when the upstream has another name, it refuses, as git does.
  - `current` and `matching`: to the branch of the same name.
  - `upstream` and `tracking`: to the upstream.
  - `nothing`: it refuses.
  - A value git does not know: it refuses, as `git push` fails on one. Not: taking it as `simple`, which would push
    where the user's configuration does not say.
- Whether to push, force push or say the branch is behind is decided against the upstream only when it has the
  branch's name. With another name, where the push goes is up to `push.default` when it runs, so the push starts
  without those checks: git refuses what is not a fast-forward, and a refusal of `push.default` asks as below. Not:
  reading `push.default` into the model to compare against the target, which is more to keep up to date for a rare
  case; what it costs is that such a branch is not offered a force push.
- It still names the branch in the refspec, in full: `refs/heads/<name>:refs/heads/<target>`. Not: a plain
  `git push`, which only pushes the checked out branch, while jazygit pushes any branch; and which with `matching`
  would push every matching branch where one was asked for.
- A refusal is `GitError.PushRefused(String pushDefault, RemoteBranch upstream)`, with the value as configured. The
  push then asks where to push the branch, in the popup that asks for the upstream of a branch without one: it starts
  out as the branch of the same name, and says why it is asked. Typing the upstream pushes there, as asked for. Not:
  an error, after which the user can do nothing in jazygit.
- A force push goes where a push goes; its lease is then that of the target.
- Pushing to an upstream typed by the user goes there, whatever `push.default` says: it was asked for.
- What stays out: `remote.pushDefault` and `branch.<name>.pushRemote` (the push goes to the upstream's remote, as
  before), and `remote.<name>.push` refspecs.

### Deleting on the remote says which branch

The choices of the delete dialog that act on the remote name the remote branch, e.g.
`delete remote branch origin/develop`. When that branch has another name than the local one, they say so. The push
that deletes it names it in full, `git push <remote> --delete refs/heads/<branch>`, so a tag of the same name on the
remote neither makes git refuse nor is deleted instead.

### A revision is a type, with one way to write it

- `Revision`, in `git.model`, is sealed. `BranchRef` (`Local`, `Remote`) extends it, with `Commit(Sha)` and `Head()`.
- `toArg()` is what git is given where it takes a revision: always a full ref (`refs/heads/main`,
  `refs/remotes/origin/main`), a full sha, or `HEAD`; never a bare name. A tag of the same name cannot win, and no
  revision can start with `-`. `label()` is what the user sees: `main`, `origin/main`, `abc1234`.
- Where git takes the name of a branch rather than a commit, it is given the name, as a full ref would mean something
  else there: `git checkout refs/heads/x` checks out no branch, and `git branch -d refs/heads/x` looks for a branch of
  that name.

  | git takes a branch name                     | git takes a revision, as a full ref or sha                        |
  |---------------------------------------------|-------------------------------------------------------------------|
  | `checkout <branch>`                         | the start of `checkout -b`, `worktree add`, `branch --force`      |
  | `-b <name>` of `checkout` and `worktree add` | `merge`, `rebase`, `reset`, `cherry-pick`, `revert`, `show`       |
  | `branch --delete`, `--move`, `--force <name>` | `log`, `rev-list`, `rev-parse`, `merge-base`                     |
  | `worktree add <path> <branch>`              | both sides of a refspec: `push`, and `fetch` to fast-forward       |
  | `--exclude=<name>` with `--branches`        | the branch `push --delete` deletes                                |
  | `branch.<name>.*` configuration keys        |                                                                   |

- `BranchRef.ref()` becomes `toArg()`, `ResetTarget.revision()` a `Revision`, and the `Refs` helpers go into it.
- `Sha.parse` only takes a full object id: 40 or 64 hex digits. An abbreviated one, which a ref of the same name would
  win over, is never a `Commit`; git prints every sha jazygit reads in full, and only the display shortens one.
- What the user types as a start is not a revision until git says what it is: `resolve` runs
  `git rev-parse --verify --end-of-options <text>^{commit}` in the command, and the worktree starts at that
  `Commit`. Not: a `Revision` that holds typed text, which would be the `String` again.

### A remote is a type

`RemoteName`, in `git.model`: `parse` refuses a blank name, whitespace, and a leading `-`. It is the remote of
`RemoteBranch`, of a fetch and of its messages, and `DEFAULT_REMOTE`.

### Git commands are built with their arguments typed

- `GitArgs` is the one place in the `git` package that turns a revision, a branch name or a remote into text for git.
  Its functions are used inside the command lines the `Cli` classes build, one per kind of argument:
  - `revision(Revision)`, and the compound forms `not(Revision)` (`^<rev>`), `symmetric(Revision, Revision)`
    (`<a>...<b>`) and `refspec(Revision, BranchRef)` (`<src>:<dst>`);
  - `name(BranchName)`, where git takes a branch name, also inside `--exclude=` and configuration keys, and in the
    text jazygit hands git besides arguments, like the input of `fmt-merge-msg`;
  - `remote(RemoteName)`.

  Not: a builder that replaces the command lines, which the 81 of them would all have to move to for the same
  guarantee.
- `GitArgs` adds no `--`: the commands that take paths pass it as they did. What keeps an argument from being read as
  an option is that none of the types can start with `-`.
- `CoreArchitectureTest` fails when a class of the `git` package other than `GitArgs` calls `Revision.toArg()`, of any
  kind of revision, `BranchName.value()` or `RemoteName.value()`. ArchUnit sees the calls, not what they are for, so
  the rule is on the calls: whatever needs the text goes through `GitArgs`, even when it is no argument.
- `Sha.value()` is left out of the rule: a `Sha` is a full hex object id, which cannot be taken for a name or an
  option, and the rebase todo and its script match on it as text. A parent is written `<sha>^` from it.

### gh is given types of its own

The gh layer knows nothing of git's (`CoreArchitectureTest.ghKnowsNothingOfGit`), so it gets its own:

- `GhBranch`, a branch as GitHub names it in the repository: `createPullRequest(GhBranch head, GhBranch base, ...)`
  and `browse(GhBranch)`.
- `GhCommit`, a commit as GitHub reported it: `mergePullRequest(int, Option<GhCommit> head, ...)`, where "unless
  empty" becomes an `Option`, and `PullRequestDetails.head` is an `Option<GhCommit>`.
- In the app, the head and the base of a new pull request are `RemoteBranch`es up to the command, and
  `OnGitHub.branch(RemoteBranch)` is the one place a `GhBranch` is made, which `ArchitectureTest` keeps so: a local
  name cannot be handed to gh for the remote one.
- The branch names that are only shown, like `headBranch` and `baseBranch` of a pull request, stay `String`s.

### Steps

1. Push follows `push.default`, and deleting on the remote names the branch.
2. `Revision`, and a stricter `Sha`, with `BranchRef` a `Revision` and `Refs` folded in.
3. Start points are typed: `NewBranchStart`, and a typed start resolved to a commit.
4. `GitArgs`, every `Cli` class through it, and the `CoreArchitectureTest` rule.
5. `RemoteName`, from fetching to pushing, through the app.
6. gh's head, base and commit typed.

## Consequences

- A branch made from `origin/develop` still follows it, unless the user's configuration says otherwise; with the
  default configuration, `P` asks where to push it instead of pushing onto `develop`. A user with
  `push.default=upstream` pushes onto `develop`, as plain git would.
- The branches pane shows the upstream git set, as before.
- A user with `push.default=nothing` is asked where to push every branch.
- A branch whose upstream has another name is not offered a force push; a push git rejects says so.
- The tests that compare command lines change with steps 1, 2 and 4: where git takes a revision, a branch is passed
  as `refs/heads/<name>`, and a push names both sides in full.
- A new `Cli` method can only hand git a revision, branch or remote through `GitArgs`. The rule cannot see one turned
  into text by concatenation, which records allow: `"x" + remote` writes `RemoteName[value=origin]`. The tests that
  compare command lines are what catch that.
- Stash refs (`Stash.ref`) are still a `String`; they are left for later.
- gh's `owner:branch` head for a pull request from a fork stays unsupported.
