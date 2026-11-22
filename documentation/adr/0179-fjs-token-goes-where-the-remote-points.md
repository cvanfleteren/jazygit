# 179. fj's token goes where the remote points, over plain HTTP too

Date: 2026-11-22

## Context

ADR 174 sends fj's token only over https: a remote over plain HTTP is asked without it. That is stricter than the
tools the user already runs against the same host: git sends its credentials over an `http://` remote, and fj sends
its token to a host it logged in to over `http://`. It keeps from jazygit what the user set up: a Forgejo on a LAN
or in a homelab, served over plain HTTP, can show its builds and pull requests, but creating a pull request (ADR 178)
gets a 401, and the error tells the user to run `fj auth login`, which cannot help. A Forgejo in Docker for jazygit's
own integration tests, served on `http://localhost:3000`, is the same case.

## Decision

- **fj's token goes where the remote points, over the scheme the remote says**, as git's credentials and fj's token
  do: over plain HTTP to an `http://` remote. How the user reaches their Forgejo is their choice; jazygit does not
  forbid it.
- **It warns in the command log, once as Forgejo turns on**, when fj's token goes to the home of the pull requests
  (ADR 177) unencrypted: "Forgejo at `http://host` is asked over plain HTTP: fj's token is sent unencrypted". A
  notice, not an error: nothing is refused. It comes once the home was found to be Forgejo, so it never takes the place
  of what an operation left in the command log, an error or a notice: then it is not given for that opening.
- **An ssh remote is still asked over https** (or at fj's alias of its host, ADR 174), as it has no scheme of its
  own to follow.
- **A 401 means the host has no token of fj's for jazygit to send**, so ADR 178's advice to run `fj auth login`
  holds for plain HTTP too.
- Not: a variable naming the hosts asked over plain HTTP, for tests only. It would keep the rule and need its own
  matching of hosts and ports, while the tests can use an `http://` remote.

## Consequences

- A Forgejo over plain HTTP works in full, creating pull requests too, with fj's token, and the user is told that
  the token goes unencrypted.
- The one case that gets less safe is a host the user logged in to fj over https while one of their remotes names it
  over `http://`: the token then goes in the clear, as git's credentials for that remote do.
- Integration tests run against a Forgejo in Docker through an `http://` remote, with nothing set for them.
- Only the home is warned of; a remote whose builds are read on another host over plain HTTP gets the token too,
  without a warning of its own.
