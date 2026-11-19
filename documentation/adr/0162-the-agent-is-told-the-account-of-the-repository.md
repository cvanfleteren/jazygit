# 162. The agent is told the account of the repository

Date: 2026-11-20

## Context

A repository can be configured to use a GitHub account of its own (`jazygit.githubUser`). jazygit then runs gh and
git as that account, with its token in `GH_TOKEN`, or `GH_ENTERPRISE_TOKEN` and `GH_HOST`, for each command it runs
(`GhToken`); gh's active account is left as it is, so other repositories and programs keep using it.

The agent a failed build is handed to (ADR 161) is started without those variables. When it runs the command that
reads the logs, `gh run view … --log-failed`, gh runs as its active account. On a private repository of another
account or organisation, that reads as not found, and the agent may take it that there are no logs; what it writes,
a comment, or a push over HTTPS through gh's credential helper, would be as the wrong account.

Handing the agent the token in its environment would make that work by itself, but:

- ADR 161 accepted that what the forge gives can steer the agent, as the agent holds no secret. With the token in its
  environment, a crafted job name or log line can have it read out, and it is the user's whole gh token.
- Every program the agent starts inherits it: tests, scripts, tools.
- An agent left running in a terminal of its own, as `tmux new-window`, gets the environment of the tmux server, not
  the one jazygit gives: the token would not arrive, and nothing would tell. Passing it on the command line puts it
  in the list of processes and the command log.

## Decision

- The agent is told the account, not given its token. jazygit's own prompt says, before the forge's data and outside
  it, which account the repository uses on which host, and how to run the forge's program as it: for gh, each command
  with `GH_TOKEN="$(gh auth token --user <user> --hostname <host>)"` before it, or `GH_ENTERPRISE_TOKEN` and `GH_HOST`
  for an Enterprise Server, as jazygit runs gh itself; and never `gh auth switch`, which changes the account of every
  program.
- Only an account configured for the repository is told. Without one, jazygit runs gh as its active account, and so
  does the agent.
- A prompt of the user's own has it as `{account}`: that sentence, empty without an account.
- What the agent is told names no forge: a `ForgeAccount`, in `kernel-state` beside `FailedBuild`, holds the user, the
  host, and how to run the forge's program as it, which the forge side writes (`GitHubAccount.forge`). The `Env` gives
  it (`forgeAccount`), from the account configured (`Account.configured`), as the command that starts the agent runs:
  the account is read from the repository's git configuration, which the model does not hold. `features-agent` still
  knows no forge.
- Not: the token in the agent's environment, for the reasons above. An agent that is to have it can be set up to fetch
  it itself; passing it as an option of an agent is for a later ADR, if it is wanted.

## Consequences

- The agent can still get the token: it is told how. It gets it on purpose, for one command at a time, and it is not
  in the environment of everything it starts.
- An agent that does not follow what it is told runs gh as the active account, as before; with a wrong account it
  most likely fails, rather than acting as it.
- A push over SSH goes as the SSH key's account, whatever the agent is told; only HTTPS goes through gh's token.
- The command log shows the prompt with `$(gh auth token …)` in it, not the token.
