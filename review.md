# jazygit: whole-project review

2026-10-24, at b7c257b.

Six reviewers each took one area: the core git layer, the GitHub layer, the Elm state core, the app shell and UI, the features and settings, and build/tests/docs. The most serious findings were then checked again in the code; those are marked ✔. The other findings were verified in the code by the reviewers and, where noted, reproduced in scratch repositories.

**Build health:** `./mvnw verify` passes (358 core and 1298 app tests, checkstyle clean, 38 s). The acceptance suite passed 130 of 131. The one failure is the line-wrap-dependent wait in `test_start`, which #117 already fixed on origin/main.

## Fix first: real risk of losing work

1. **✔ Renaming a branch can overwrite another one.** `CliBranch.rename` (`CliBranch.java:84`) passes the names with no `--`, and `BranchName.parse` accepts a leading `-`. Renaming `main` to `--force` while on `feature` overwrites `main` with `feature`. This was reproduced. Fix: add `--`, and make `BranchName.parse` reject a leading `-`.
2. **✔ A new branch made from a remote branch tracks it, and push and delete then act on the shared branch.** `CliCheckout.checkoutNewBranch` runs `checkout -b` without `--no-track`, and so does move commits onto `origin/main`.
    - `CliPush` pushes with an explicit `name:refs/heads/<upstream>` refspec, which skips the guard `push.default=simple` would give. Making `my-feature` from `origin/develop` and pressing `P` pushes onto `develop`.
    - Delete's "remote" option then deletes `develop` on the remote, and the dialog never names it.
3. **✔ A force push is offered when there is nothing to push.** `PushUpdate.java:48` checks `behind > 0` but not `ahead > 0`. Confirming runs `--force-with-lease`, which passes and rewinds the remote, dropping other people's commits.
4. **✔ Deleting a local branch always uses `-D`.** `CliBranch.deleteLocal` passes `--force`, also when a worktree is removed together with its branch. Unmerged or unpushed commits go without a warning. Fix: try `-d` first, then confirm with a count of the commits that would be lost.
5. **Merge and rebase pass bare branch names**, so a tag with the same name wins. The merge then says "Already up to date" and exits 0 without merging. This was reproduced. Fix: use `Refs.head(...)`, as move commits already does.
6. **Global keys get through dialogs.** `q`, `Q` and Ctrl+C reach TamboUI's built-in `quit` binding when a list dialog or markdown dialog leaves them unhandled. `JazygitApp.dispatch` also quits on `Msg.Quit` before the model sees it, so no update can refuse. Ctrl+C in the commit dialog loses the typed message. `M` and Ctrl+P act behind an open popup.
7. **✔ The fat jar picks the JLine backend.** The merged service file lists JLine first, and only `run.sh` and the acceptance tests pin Panama. With `java -jar`, handing the terminal to the editor (`e`, Ctrl+O) loses the first key typed in it, as ARCHITECTURE.md itself warns. Fix: the JLine backend is removed from the fat jar, which leaves only Panama.

## High and medium

### Robustness

- **✔ No guard around updates.** `Program.dispatch` runs `Update.update` unguarded, so a bug in any update shows TamboUI's error screen and ends the app. No `errorHandler` is set, so view exceptions do the same and are never logged.
- **✔ A failed read empties the pane.** `LoadUpdate.failed` replaces the loaded value with `Failed`, against the `Repo` javadoc. One timed-out `for-each-ref` empties both branch tabs, resets the cursors, and drops the "in progress" guard while it lasts.
- **✔ Unbounded preview reads.** Each highlight move queues its own `git show` on the single `git-io` queue, with no slot and no dedupe. Failed previews are retried every tick with no backoff.
- **Processes:**
    - "Cut off" output is still read whole into memory (OOM on huge files).
    - A timeout kills git but not its hooks, ssh or pinentry, and those children keep the TUI's terminal.
    - Remote commands get 2 minutes, hooks included.
    - Forcing `GIT_SSH_COMMAND` overrides a user's `GIT_SSH`.

### Input and UI

- **Keys reach the hidden files tree.** TamboUI also gives unhandled keys to elements without focus, so Space in another pane collapses the files tree and ←/→ fold its folders.
- **Ctrl+C while a GUI editor waits** (`code --wait`) kills the JVM, because handing the terminal over turns signals back on.
- **Tabs in diffs render as one space.**
- **The whole styled diff is rebuilt every frame**, with one CSS resolve per line.

### GitHub

- **✔ One character can break a whole PR list.** Every parser splits on `\R`, so a U+2028 or `\f` in one title or comment fails the whole list or conversation. Fix: split on `\n`.
- **✔ The account picker doesn't work with ssh host aliases.** `GitHubSetup.onHost` compares the alias it never resolved (`github-work` ≠ `github.com`), so the picker is empty for exactly the case the setting exists for.
- **Wrong base name.** `--base` gets the local branch name instead of its name on the remote, and the `gh-merge-base` parent (ADR 3) is then ignored.
- **Checks and load:**
    - The check state comes from the first 100 contexts only, so a failing check beyond that shows as passing.
    - Every open PR is read, with its contexts, after every fetch (thousands of PRs on big repos), which times out or burns rate limit.
- **The pull requests screen can show a merged or closed PR as still open for up to a minute.** Its reload has no `again`, unlike `GitHubReads`.
- **A transient `TokenUnavailable` is cached for good.** One failed `gh auth token` (say a locked keyring) means every gh command fails until the repository is reopened.

### Features and settings

- **Init sits under the cursor on the start screen**, so a reflex Enter runs `git init ~`.
- **Saving settings replaces a symlinked `config.toml`** (dotfiles managers) and narrows it to 0600.
- **✔ Move commits accepts a side-branch commit** reached through a merge, then resets `from` somewhere surprising.
- **Reset from the commits pane never says how many commits it leaves behind.**

## Guardrails that claim more than they check

- **No layering rule.** ARCHITECTURE.md says `ArchitectureTest` fails on any dependency not drawn, but it only checks cycles and a few targeted rules. `keys` already imports `state`, `git.model` and `gh.model`, and `ui` uses `GitInfoProvider.MAX_COMMITS`, all without failing.
- **The purity rule can be got around.** A probe update calling `LocalDateTime.now()`, `getenv`, `Random` and a helper doing `Files.readString` passed all 13 rules. There is no actual impurity in the code today; this is a missing guard.
- **The JUnit git tests inherit `~/.gitconfig`** (hooksPath, signing, pull.rebase). The acceptance tests are isolated; these are not.
- **CI:**
    - `main` is unprotected, and #116 merged with a red check.
    - The native image is published without ever being run.
    - Actions are pinned by major tag only.
    - `ai-issue.yml` feeds the issue text into a prompt with `Bash(git:*)`/`Bash(mvn:*)`, so labelling an issue amounts to running what its author wrote.

## Docs

- **ARCHITECTURE.md has drifted a lot:**
    - About 12 `Model.x` fields now live in `Jobs`, `Repo`, `RepoView`, `GitHubState` and others, and the class diagram is stale.
    - Classes and methods that don't exist are named: `CliBranches`, `CliRebase.rewordAny`, `CliIndex.reword`.
    - The "Writes" list puts operations in the wrong classes.
    - Several things described are no longer what the code does: the `gh` commands (`pr list --json`, `--fill`, `pr merge`), the "`git-fetch` thread" (now a pool of 4), and the 30 s read timeout (status gets 10 s).
- **The project's rules aren't in agents.md**, which is two lines. They live only in a private assistant memory, so the three CI agents can't see them: run verify, not test; ADRs are immutable; no `toString` on records. `ai-issue.yml` points to a CLAUDE.md that doesn't exist.
- **Stale and stray files:**
    - wanted.md and TODO.md list work that is done (revert, search, word editing, the fetch race).
    - `alfa.txt` and `jazygit-parent.iml` are committed.
    - The ADRs have no Status lines, and ADR 3 and ADR 12 don't point forward to ADR 18 and ADR 27, which change them. That forward reference is the one edit the immutability rule allows.

## Overall

- **The architecture is real, not aspirational.** Updates are genuinely pure, errors stay data all the way down, and `Update.confirm` checks a popup's subject again at confirm time. `Unfinished.runOrUndo` never aborts what the user left unfinished. The integration tests against real git are extensive.
- **The weak spots follow three patterns:**
    - **Git's safety nets switched off:** auto-tracking, explicit push refspecs, `-D`, a lease checked against the local tracking ref.
    - **Validation in the UI rather than in the types:** `BranchName` and `Sha` accept almost anything, and start points, remotes and stash refs are plain Strings.
    - **TamboUI's event routing leaking through:** global handlers, the built-in quit binding, and unfocused elements getting keys.

  One "input policy" choke point and stricter value types would close most of the high findings.
- **Design debt:**
    - `GitInfoProvider` has 68 methods behind a pass-through implementation.
    - `CliGitHub` holds about 10 query/jq/parser triples tied together only by positional indices, with no jq program ever run in a test.
    - `JazygitApp` (767 lines) mixes start-up, runtime, layout and the terminal hand-over.
    - The `Cancelled` handler is copied 39 times.
