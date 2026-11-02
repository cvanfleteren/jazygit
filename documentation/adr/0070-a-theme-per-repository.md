# 70. A theme per repository

Date: 2026-11-02

## Context

The theme is one setting, in `settings.toml`, for every repository; `--theme` replaces it for one session. A user
with work and personal repositories open side by side wants to tell them apart at a glance, so wants a theme per
repository, or per kind of repository: the ones in a directory, or the ones of a GitHub account.

The account a repository uses is kept in its own git configuration already, as `jazygit.githubUser` (ADR 69 keeps
that behind `Config`). And git already groups repositories by directory: an `includeIf "gitdir:~/work/"` in
`~/.gitconfig` is how many set their work email for every repository under `~/work`.

## Decision

### The theme is a key of the git configuration

- A repository's theme is `jazygit.theme` in the git configuration, with the id of a theme as `--theme` takes it. The
  theme shown is the first of: the one asked for with `--theme`, the one the git configuration gives the repository,
  and the one in `settings.toml`, which is the theme for all repositories.
- The key is read as git reads it, from the repository's configuration, the user's, the system's and the files they
  include. So a theme per directory is an `includeIf` the user writes, and jazygit has no rules of its own:

  ```ini
  # ~/.gitconfig
  [includeIf "gitdir:~/work/"]
      path = ~/.gitconfig-work

  # ~/.gitconfig-work
  [jazygit]
      theme = light
  ```

- One `git config --show-scope --show-origin --get-all jazygit.theme` gives every value with its file. The theme of
  the repository's own is read with `--local`, as the account is; the inherited one is the last value outside the
  repository's configuration. A value that names no theme counts as not set.
- It is read when a repository opens: at startup before the first frame, and on a switch on the thread of the command
  that switches, not on the render thread. A change to the git configuration made outside jazygit shows the next time
  the repository opens.
- Not: a theme per GitHub account. Repositories of one account mostly sit in one directory, which the `includeIf`
  covers, and the theme would wait for the account to be known. Should it be wanted, the account can come from the
  same `includeIf`, once `jazygit.githubUser` is read as git reads it rather than with `--local`.
- Not: the theme in `repos/<owner>/<name>/` next to the state (ADR 63). That has only repositories with a GitHub
  remote, is shared by every clone, and leaves a theme per directory to rules of jazygit's own.
- Not: rules in `settings.toml`, by directory or by remote. That is a matcher to write and explain, which `includeIf`
  already is.

### Choosing it

- The theme dialog chooses for all repositories or for this one. The settings panel opens it either way, from two
  lines under Appearance: "Theme for all repositories" (`t`) and "Theme for this repository" (`r`), which shows the
  repository's own theme, else the inherited one and its file, with `~` for the home directory, else none.
- Choosing for all repositories while the repository has a theme saves it, but what shows is still the repository's:
  the line of the repository says why.
- A theme chosen for this repository is written with `git config --local`, in the command log, and replaces a theme
  asked for at startup, as choosing for all repositories does. A write that fails leaves what was chosen showing until
  jazygit stops, and says so.
- When the repository has a theme of its own, the dialog's first line takes it away, `git config --local --unset-all
  jazygit.theme`, and says what shows instead: the inherited theme and its file, or the theme for all repositories.
  Highlighting it previews that one.
- jazygit only writes the repository's own configuration. A theme from `~/.gitconfig` or a file it includes is shown,
  not changed: that file is the user's and holds for other repositories too.
- Outside a repository (ADR 28) there is no git configuration of one, and the theme for all repositories shows.
- The repository's configuration is shared by its worktrees, so they show the same theme.
