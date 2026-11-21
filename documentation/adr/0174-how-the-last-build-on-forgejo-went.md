# 174. How the last build on Forgejo went

Date: 2026-11-21

## Context

ADR 171 lets a forge be a feature of its own: it decides whether it applies to a repository, keeps a part of the
model, and shows badges on the branches. GitHub is the only one. Repositories are also kept on Forgejo, as on
Codeberg or one's own instance, with Forgejo Actions or another CI posting how a commit's builds went.

Forgejo's API, as Gitea's, gives a commit's combined status, `GET /api/v1/repos/{owner}/{repo}/commits/{ref}/status`:
one `state` (`success`, `pending`, `failure`, `error`, `warning`, or empty for no status) and the statuses it was made
of. Forgejo Actions posts one per job, named `<workflow> / <job> (<event>)`; other CIs post theirs the same way.
`GET /api/forgejo/v1/version` answers only on Forgejo, not on Gitea or another host.

fj, Forgejo's CLI (forgejo-cli), keeps a token per host in `keys.json`, with aliases from an ssh host and port to the
host of the web interface (`code.example.net:10022` to `code.example.net`), as `fj auth login` makes them. Its
commands do not give a commit's status: `fj actions tasks` prints the repository's last 20 Actions jobs, as text, with
a short sha and no branch, and leaves out the statuses of other CIs.

## Decision

- **A feature of its own, `forgejo`, in `features-forgejo` on `core-forgejo`**, deletable as GitHub is (ADR 171, 173).
- **It talks to Forgejo's HTTP API with the JDK's client, not through fj**: fj cannot give the status of a commit.
  It uses fj's token for the host, read from fj's `keys.json` (`$XDG_DATA_HOME/forgejo-cli`, or
  `~/.local/share/forgejo-cli`), so that logging in with fj is all it takes, as with gh; without one it asks without a
  token, which is enough for a public repository.
- **A remote is on Forgejo when its host answers `/api/forgejo/v1/version`** over https, once per host for the app's
  run. The host of an ssh remote is fj's alias for it, if fj has one, else the same host on the https port: an
  `ssh://git@code.example.net:10022/owner/repo.git` remote is `https://code.example.net`, repository `owner/repo`.
  Hosts known to be another forge (github.com, gitlab.com, bitbucket.org) are not asked. The feature is on with one
  remote on Forgejo.
- **A branch shows how the builds of the tip of the branch it follows on Forgejo went**, a remote branch those of its
  own tip, in the branches pane, after GitHub's badges, with the symbols of the checks GitHub's shows (✓ ✗ ●, ADR 48),
  and the help's legend says so. A local branch's own commits that are not pushed have no builds: it is the tip of its
  upstream that shows. Nothing shows for a commit without a status.
- **A status is read once per commit, and again while it is pending**: every 10 s, the commits that were not read and
  those pending are read, those of the local branches' upstreams first, at most 20 at a time; after each fetch, every
  commit shown is read again, so that a build run again shows. Not: reading all of them on a timer, as a commit's
  status rarely changes once its builds are done.
- **Only the tips of remotes on Forgejo are read**: a remote found not to be on Forgejo, as a mirror on GitHub, is
  left out of what is read for as long as the repository is open, so its branches do not crowd out those on Forgejo
  from the 20 read at a time. A commit on both is read on Forgejo.
- **A read that fails is tried again only after the next fetch**, and the log says why; the badge shows nothing for
  it meanwhile. Not: trying it again every 10 s, as a private repository without a token fails every time.
- **Another repository on Forgejo starts afresh**, as its remotes may have the same names as those of the one before.
- **Not yet**: the statuses of a commit one by one (`C`), the builds of a branch (`B`), pull requests, a setting for
  the token or the hosts.
- **`jazygit-app`'s tests run without Forgejo too** (`jazygit.without=github,forgejo`), so that none of them asks a
  host on the network; `jazygit-github-app-tests` runs without Forgejo (`jazygit.without=forgejo`), as GitHub's
  tests know nothing of it.
- **ADR 173's test checks each forge**: `GitHubDeletableTest` becomes `ForgesDeletableTest`, which checks GitHub and
  Forgejo alike, and so also that neither uses the other.

## Consequences

- A repository on Forgejo shows how its branches build, with no setup beyond `fj auth login` for a private one.
- Opening a repository asks each host of its remotes once whether it is Forgejo; a host that does not answer takes
  the time of a connect timeout, in the background.
- The symbols of the checks are drawn the same in both forges' badges.
- A native image needs the JDK's HTTP client and TLS; the Nix build checks it.
