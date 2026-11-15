# 134. Where a new worktree starts out

Date: 2026-11-15

## Context

A new worktree starts out next to the main worktree, named after it and the branch: `/repos/app-feature-login` for
`feature/login` next to `/repos/app` (ADR 9). In a bare layout, with no main worktree, it starts out next to the
worktree shown, named after the branch only. A pull request is named after its branch, or `pr-<number>` when it
comes from a fork. The user can change the location before it is added, but has to do so for every worktree when
they keep them elsewhere: all worktrees under `~/worktrees/<repository>/`, or the pull requests they review under
`~/reviews/`, apart from their own branches.

ADR 121 made the name of a new branch start out as a text from the git configuration. A location cannot be a fixed
text: every worktree would start out at the same place, and get a number appended. It needs parts that are worked
out for each worktree. ADR 121 left variables to a later ADR, and said what becomes of braces in its template is for
that ADR; this one leaves its template as it is, as the variables below are only for the new keys.

## Decision

1. **Two keys of the git configuration hold where a new worktree starts out.** `jazygit.newWorktreeTemplate` holds it
   for every new worktree; `jazygit.pullRequestWorktreeTemplate` holds it for one that checks out a pull request, and
   when it is not set, `jazygit.newWorktreeTemplate` holds for those too. They are read as ADR 121 reads its key: from
   the repository's configuration, the user's and the system's, the last value holding, so that they can be set for
   one repository, for all, or with `includeIf "gitdir:…"` for the repositories in a directory. There is no setting
   for them in `settings.toml`, nor in the settings panel.
2. **A template has variables in braces**, each replaced once, so that what it is replaced with is not looked at
   again:
   - `{repo}`: the name of the directory of the main worktree; in a bare layout, of the directory the worktrees are
     in.
   - `{branch}`: what the directory is named after without a template, with `/` as `-`: the branch, the revision as
     typed, or, for a pull request from a fork, `pr-<number>`.
   - `{owner}` and `{name}`: the repository on GitHub. For a pull request, the one it is of; for any other worktree,
     the one the pull requests were read for, once they were.
   - `{number}`: the number of the pull request.

   A variable that has no value for the worktree, like `{number}` for a branch, or that is not one of these, is left
   as it is written. So is a brace without a variable.
3. **A leading `~` is the home directory, and a relative location is next to the main worktree**: resolved against the
   directory the main worktree is in, or in a bare layout the one the worktree shown is in. An absolute location is
   taken as it is. `git worktree add` makes the directories that are missing.
4. **Without a template it starts out as before**, as if the template were `{repo}-{branch}`, or `{branch}` in a bare
   layout. A template that is blank is none, so that a repository can turn off one set for all.
5. **A location a worktree has already gets a number appended**, as before (ADR 9).
6. **The templates are read each time a location is asked for**, as ADR 121 reads its key: asking for a new worktree
   clears the error shown and starts `ReadWorktreeTemplate`, whose `LocationTemplateRead` opens the popup, only when
   no popup is open. The location is worked out once it is read, from the worktrees known then.
7. **The template is not checked, and one that cannot be read is none.** The popup shows where it leads, which the
   user can change; confirming adds the worktree there, or says why git would not. When git cannot read the
   configuration, the worktree starts out where it would without a template, and only the debug log says why.

## Consequences

- The popup asking where a new worktree goes opens once a `git config` has run, as the one asking for the name of a
  new branch does.
- Tests that ask where a new worktree goes go through `LocationAsked`, which answers the read.
- A variable in the name of a new branch is still for a later ADR; it can take the braces this ADR uses.
- Asking where a new worktree goes cannot be skipped: a template only fills in the location.
