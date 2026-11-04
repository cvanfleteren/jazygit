# 72. One git error type

Date: 2026-11-04

## Context

Decision 5 of ADR 69 left the error types of the git operations as they were: a mix of `ProcessError`, `GitError`,
`GitFailure<GitError>` and `GitFailure<ProcessError>`, as issue #294 (section 1a) found them.

`ProcessError` is a sealed subtype of `GitError` holding the five failures that running any git command can have:
`GitUnavailable`, `TimedOut`, `Interrupted`, `CommandFailed` and `UnexpectedOutput`. `GitFailure` takes the error as a
type parameter, so that an operation can say it fails only by running git (`GitFailure<ProcessError>`) or also for a
reason of its own, such as `LocalChanges` or `StoppedOnConflicts` (`GitFailure<GitError>`).

The parameter only ever takes those two values: 186 times `GitFailure<GitError>`, 151 times `GitFailure<ProcessError>`,
and 43 times `GitFailure<?>` where the app does not care which. Nothing uses the narrower type:

- No code switches over the five process errors alone. `ErrorText` matches on `GitError` as a whole.
- What an operation can fail with is already documented on the operation, as the Javadoc of `GitError` asks.

It costs:

- Two `GitFailure.widen` overloads, and about 40 calls to them, only to pass a `GitFailure<ProcessError>` where a
  `GitFailure<GitError>` is expected; type witnesses such as `GitFailure.<GitError, String>widen(…)` and
  `.<GitFailure<GitError>>mapLeft(…)`; and `Either<? extends GitFailure<?>, …>` on every `GitOperationCmd`.
- A dropped error. `cleanupFailed`, `StashNotRestored.cause` and `GitFailure.followedBy` take only a `ProcessError`.
  When a pull request cannot be checked out in a new worktree, `AddWorktreeCmd` removes that worktree again; when that
  removal fails, its `GitError` is kept only if it is a `ProcessError`, and otherwise silently dropped, though the
  worktree is then left behind.

`GhFailure`, on the gh side, is already a plain record of a `GhError` and the commands.

## Decision

1. **`GitFailure` is not generic**: `GitFailure(GitError error, List<ExecutedCommand> commands, Option<GitError>
   cleanupFailed)`. `widen` goes; `nothing`, `sequence`, `after`, `followedBy` and `map` take and give a plain
   `GitFailure`. An operation that can fail returns `Either<GitFailure, …>`, and `GitOperationCmd.perform` returns
   `Either<GitFailure, List<ExecutedCommand>>`.
2. **`ProcessError` is folded into `GitError`.** Its five records implement `GitError` directly, and a read that
   returned `Either<ProcessError, T>` returns `Either<GitError, T>`. A parser that can only fail with
   `UnexpectedOutput` keeps saying so in its type, as it does now.
3. **A failed cleanup keeps its reason, whatever it is.** `StashNotRestored` holds a `GitError`, and so does
   `cleanupFailed` in `GitFailure`, `Failure.Git` and `Failure.GitHub`. `AddWorktreeCmd` keeps every reason the
   removal of the worktree failed.
4. **Which failures an operation can have stays documented on the operation**, as before; the type no longer says it.
5. **`baseDistance`, which swallows its errors, stays as it is.** The rest of decision 5 of ADR 69 is left for a later
   change.

## Consequences

- One change rewrites about 110 files, nearly all mechanically: the type arguments of `GitFailure` go, the calls to
  `widen` are unwrapped, and `ProcessError` becomes `GitError`.
- The compiler no longer tells an operation that fails only by running git from one that can refuse for a reason of
  its own; its Javadoc does.
- A switch over the error of a failed cleanup, or of a plain read, covers every `GitError`, not only the five process
  errors. `ErrorText` already does.
- The section on errors in `ARCHITECTURE.md`, with its class diagram of `ProcessError`, is rewritten with the change.

Amends decision 5 of ADR 69 (`0069-git-operations-in-roles.md`).
