# 121. What the name of a new branch starts out as

Date: 2026-11-13

## Context

Teams often want every branch name to start the same way, with the key of a ticket: `ART03DEV01-42-fix-login`. The
user then types that start for every new branch, while it hardly ever changes within a repository, or within the
repositories of one project.

This is the first of the ways the user could shape jazygit beyond its settings. It needs no code of the user's: a
fixed text the name starts out as does it. Where to keep it follows ADR 70, which keeps a theme per repository in
the git configuration: git already sets a value for one repository (`--local`), for all (`--global`) or for the
repositories in a directory (`includeIf "gitdir:~/work/art03/"`), and jazygit reads it as git does (`Config.all`, the
last value holding).

## Decision

1. **The key `jazygit.newBranchNameTemplate` of the git configuration holds what the name of a new branch starts out
   as.** It is read from the repository's configuration, the user's and the system's, as git reads it: the last value
   holds. There is no setting for it in `settings.toml`, nor in the settings panel.
2. **It is text, taken as it is.** The name starts out as that text, with the cursor after it. There are no variables
   in it, and braces are only braces: `fix-{old}-` starts the name as `fix-{old}-`.
3. **It is read each time a name is asked for**, so a change in the configuration holds for the next new branch,
   without reading the repository again. Asking for a new branch starts `ReadNameTemplate`, which carries the popup
   that is to ask; its `NameTemplateRead` opens it, with the name as the template, or empty without one.
4. **It holds for every new branch whose name the user makes up:** one made from a local branch, from a commit, or
   from a remote branch. A new branch that is to follow a remote branch, asked for when the name it has on the remote
   is taken (`NewBranchPopup.following`), still starts out as that name. Renaming a branch starts out as its name, as
   before.
5. **The template is not checked.** A template that is no start of a branch name is taken as it is; confirming the
   name checks it, as before.
6. **A template that cannot be read is none.** When git cannot read the configuration, the name starts out empty, and
   only the debug log says why: making a branch is not held up by what only spares typing.

## Consequences

- The popup asking for the name of a new branch opens once a `git config` has run, a few milliseconds after the key.
- Tests that ask for a new branch go through `NameAsked`, which answers the read.
- Variables in the template, like the ticket in the name of the current branch, are for a later ADR. Since braces are
  taken as they are now, that ADR has to say what becomes of a template that has them already.
- Running the user's own code, for a template that has to be worked out, is for a later ADR too.
