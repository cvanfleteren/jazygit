# 88. A browser per account

Date: 2026-11-07

## Context

A repository can be configured to use one of the GitHub accounts gh is logged in to, as `jazygit.githubUser` in its
git configuration (ADR 69); gh and git then run as that account, with its token in their environment. A user with a
work and a personal account mostly has a browser, or a profile of one, logged in to each. But every link opens in the
one browser gh would start (ADR 85): a pull request of the work account opens in the browser that is logged in as the
personal one.

What is logged in to which account is a matter of the account, not of the repository: a repository that is switched
to another account should open its links where that account is logged in.

## Decision

### The browser is set per account, in `settings.toml`

- An account's browser is kept in `settings.toml`, by the host and the login of the account:

  ```toml
  [github.browsers."github.com"]
  cvf-work = "firefox -P work"
  cvanfleteren = "firefox -P personal"

  [github.browsers."ghe.acme.com"]
  cvf = "chromium --profile-directory=Acme"
  ```

- The host is the one the remotes give, as `GhToken` resolves it: an ssh alias such as `github-work` counts as the host
  ssh names for it, so one setting holds for every alias of a host. A blank value is not set.
- The value is a command line as gh's `browser` setting is: split into words as a shell would (`Browser.words`), with
  the link added as its last argument. There is no placeholder for the link.
- The account is the one the repository is configured to use; when it is configured to use none, gh's active account on
  the host, read when a link opens with `gh config get user -h {host}`, which reads gh's own files and not GitHub.

### It comes first

- The browser that opens a link is the first of: the account's, then gh's order of ADR 85 (`$GH_BROWSER`, gh's
  `browser` setting, `$BROWSER`, on Linux `xdg-open`). A browser set for one account is more particular than one set
  for all, also `$GH_BROWSER` exported in a shell's profile.
- An account's browser is started on every system, not only on Linux.
- It holds for every opening, not only the ones jazygit starts a browser for itself: `gh pr view --web` and
  `gh browse` get it as `GH_BROWSER` in their environment, as they get the token. Without an account's browser, their
  environment is left as it is.
- When the account cannot be known (gh's active account cannot be read, or there is none) or has no browser set, gh's
  order chooses, as before.
- An account's browser that does not start, or exits with another code than 0, fails the opening, as ADR 85 says. The
  next browser of the order is not tried: it would open the link logged in as another account, which is what this is
  against.
- The setting is read when a link opens, not when the repository does: a browser set in the settings, an account
  switched to, or another repository opened holds for the next opening.

### Choosing it

- The settings panel has a line under GitHub, after the account, "Browser for {login} on {host}" (`b`), for the account
  the repository uses now. `b` opens a text input with what is set; saving writes `settings.toml`, and saving it empty
  takes the account's browser away.
- Without a browser set for the account, the line says what opens instead and why, as the theme says where it is
  inherited from: e.g. "none, firefox from $BROWSER".
- As the account's line, it can be chosen only once gh's accounts are read, and says "reading…" until then; without an
  account on the host it cannot be chosen.
- Only the account the repository uses is set here. Another account's browser is set by switching the repository to
  it, or in `settings.toml`.
- Not: the browser per repository, as `jazygit.browser` in the git configuration as the theme is (ADR 70). Switching a
  repository to another account would leave its browser behind.
- Not: a list of every account gh is logged in to, with its browser. That is more to show for a setting made once per
  account.

## Consequences

- Links of a repository open where its account is logged in, and follow the account when the repository switches.
- Opening a link of a repository that uses gh's active account runs one more gh command, to read that account.
- `settings.toml` holds logins of the user's accounts.
