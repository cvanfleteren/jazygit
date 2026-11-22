# gh

What gh does that jazygit relies on, learned by testing, reading the docs or measuring. Each fact says when it was
learned, so a fact that has grown old can be checked again; see README.md.

## The repository and a branch's base

- `gh api` fills `{owner}` and `{repo}` in an endpoint path from the working tree's repository, and a fork is not
  special. *Why it matters:* REST calls need no owner/repo resolution of jazygit's own, and check history is keyed by
  the repository the read was made against. — learned 2026-11-22 · [ADR 0024](../adr/0024-stacked-pull-requests.md),
  [ADR 0032](../adr/0032-how-long-checks-take.md)
- `gh` resolves the repository it works on from the working tree's remotes, not from the branch's upstream. *Why it
  matters:* builds are read only for a branch that follows a GitHub remote, because otherwise a branch of the same name
  in another repository would show builds that are not its own. — learned 2026-11-22 · [ADR
  0131](../adr/0131-the-builds-of-a-branch.md)
- gh reads the pull requests of the repository chosen by `gh repo set-default`, which it records on that remote as
  `remote.<name>.gh-resolved = base`. Without that, among the remotes on hosts it knows, it prefers `upstream`, then
  `github`, then `origin`. *Why it matters:* GitHub's claim names the `set-default` remote as its home, and the kernel's
  rule follows the same name order. — learned 2026-11-22 · [ADR
  0177](../adr/0177-the-forge-of-a-repository-decided-or-asked.md)
- gh's convention for a branch's parent is the config key `branch.<name>.gh-merge-base`, holding the base's name on the
  remote (`development`, not `origin/development`); `gh pr create` takes it as the base when it is set, and gh records a
  branch's base there when it creates a branch. *Why it matters:* jazygit reads and writes the same key, so both it and
  gh agree on a branch's parent. — learned 2026-11-22 · [ADR 0003](../adr/0003-pr-base-defaults-to-parent-branch.md),
  [ADR 0020](../adr/0020-commits-not-on-the-base.md), [ADR 0143](../adr/0143-setting-the-base-of-a-branch.md)

## Accounts and tokens

- gh takes its token from `GH_TOKEN`, then `GITHUB_TOKEN` (`GH_ENTERPRISE_TOKEN`, then `GITHUB_ENTERPRISE_TOKEN`, for an
  enterprise host); gh then runs as that token's account, which its files do not name. *Why it matters:* the account,
  and so its browser, cannot be known when jazygit inherits such a token and no account is configured. — learned
  2026-11-22 · [ADR 0088](../adr/0088-a-browser-per-account.md)
- gh runs as a given account when `GH_TOKEN` holds its token, or `GH_ENTERPRISE_TOKEN` plus `GH_HOST` for an Enterprise
  Server, without changing gh's active account. *Why it matters:* jazygit runs gh per repository account this way, and
  tells agents to prefix each gh command the same way. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- An empty `GH_TOKEN` is treated as unset, so gh then runs silently as its active account. *Why it matters:* an agent
  that runs gh with an empty token may act as the wrong account without any sign. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- `gh auth token --user <user> --hostname <host>` prints the token of a logged-in account. It prints nothing when the
  account was logged out or the caller cannot read gh's keyring. *Why it matters:* an agent can fetch the token per
  command, but must stop when the output is empty. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- `gh config get user -h {host}` gives gh's active account on a host from gh's own files, without asking GitHub. *Why it
  matters:* the account a repository runs as can be looked up cheaply when a link opens. — learned 2026-11-22 · [ADR
  0088](../adr/0088-a-browser-per-account.md)
- `gh auth switch` changes the active account for every program that uses gh, not just the caller. *Why it matters:*
  agents are told never to use it. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- gh run as the wrong account against a private repository of another account or organisation reports it as not found.
  *Why it matters:* an agent may wrongly conclude that there are no logs. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- A token passed on a command line shows up in the process list (and in jazygit's command log). *Why it matters:* the
  token is never passed as an argument. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- `gh api <url>` sends gh's token only to `github.com`. *Why it matters:* images of a private repository that GitHub
  does not serve with gh's token stay a placeholder. — learned 2026-11-22 · [ADR
  0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md)

## The browser

- `gh pr view --web` only opens pull requests and drops the `#…` fragment of a link (such as `#issuecomment-…`), and gh
  opens nothing in the browser except pull requests, branches and files. *Why it matters:* gh cannot open a specific
  comment in the browser, so jazygit starts the browser itself to open a single comment. — learned 2026-11-22 · [ADR
  0006](../adr/0006-view-pull-request-comments.md), [ADR 0085](../adr/0085-a-review-remark-opens-in-the-browser.md)
- gh opens no arbitrary link of its own: unlike a pull request, where it falls back to the desktop's browser, it has no
  command to open a given URL. *Why it matters:* jazygit falls back to the OS opener itself when no browser is set. —
  learned 2026-11-22 · [ADR 0096](../adr/0096-opening-the-links-of-a-comment.md)
- gh picks a browser in this order: `$GH_BROWSER`, gh's `browser` setting (`gh config get browser --host {host}`),
  `$BROWSER`, then `xdg-open` on Linux (`open` on macOS, `rundll32 url.dll,FileProtocolHandler` on Windows). The setting
  is split into words as a shell would and the link appended as the last argument. *Why it matters:* jazygit copies gh's
  order so links open in the browser gh would use. — learned 2026-11-22 · [ADR
  0085](../adr/0085-a-review-remark-opens-in-the-browser.md)
- `gh pr view --web` and `gh browse` honour `GH_BROWSER` in their environment. *Why it matters:* an account's own
  browser can be handed to them through the environment. — learned 2026-11-22 · [ADR
  0088](../adr/0088-a-browser-per-account.md)

## Reading pull requests

- `gh pr view --json body,author,createdAt,comments,reviews` gives a pull request's conversation; the author and date
  are those of the description. *Why it matters:* the comments popup is read with one call. — learned 2026-11-22 · [ADR
  0006](../adr/0006-view-pull-request-comments.md)
- `gh pr view` gives a bot's login without the `[bot]` suffix. *Why it matters:* bots cannot be told apart from people
  without asking GitHub more. — learned 2026-11-22 · [ADR 0006](../adr/0006-view-pull-request-comments.md)
- `gh pr view --json` has no field for review threads: `comments` are those of the conversation and `reviews` only give
  each review's text. *Why it matters:* review threads must be read via `gh api graphql`. — learned 2026-11-22 · [ADR
  0007](../adr/0007-review-thread-counts.md), [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- `gh pr view --json comments` gives `viewerDidAuthor` for each comment but neither `viewerCanUpdate` nor
  `viewerCanDelete`, and it does read `reactionGroups`. *Why it matters:* in the conversation popup, edit and delete
  rights fall back to authorship, so an admin cannot delete others' comments from there. — learned 2026-11-22 · [ADR
  0064](../adr/0064-delete-a-comment.md), [ADR 0086](../adr/0086-reactions-on-review-comments.md)
- `gh pr list --json` has no fields for a pull request's stack (`stack`, `stackEntry`). *Why it matters:* the pull
  requests of the branches are read with GraphQL instead. — learned 2026-11-22 · [ADR
  0024](../adr/0024-stacked-pull-requests.md)
- `gh pr diff` is subject to GitHub's diff limits (about 300 files or 20 000 lines), cannot be read file by file, and
  shows all whitespace. *Why it matters:* a pull request's diff with remarks is read with git instead. — learned
  2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- `gh pr diff` has no flag for the amount of context: it always gives GitHub's three lines. *Why it matters:* the pull
  request diff on the pull requests screen keeps three lines of context. — learned 2026-11-22 · [ADR
  0076](../adr/0076-the-amount-of-context-in-a-diff.md)

## Changing pull requests and branches

- `gh pr comment <number> --body <text>` adds a plain conversation comment to a pull request. *Why it matters:*
  commenting is one gh call. — learned 2026-11-22 · [ADR 0005](../adr/0005-comment-on-pull-request.md)
- `gh pr edit` changes the title, body and base of a pull request but not whether it is a draft; that takes `gh pr ready
  <n>` or `gh pr ready <n> --undo`. *Why it matters:* saving an edit runs `gh pr edit` and then, only if the draft box
  changed, `gh pr ready`. — learned 2026-11-22 · [ADR 0059](../adr/0059-edit-a-pull-request.md)
- `gh pr review <n> --approve | --request-changes | --comment --body <text>` submits a review through GraphQL
  `addPullRequestReview`, one call with an event and a body. *Why it matters:* jazygit calls the mutation directly, so
  it can later switch to `submitPullRequestReview` once pending reviews exist. — learned 2026-11-22 · [ADR
  0100](../adr/0100-approving-or-asking-for-changes.md)
- `gh pr merge` calls GraphQL's `mergePullRequest`, which refuses stacked pull requests. *Why it matters:* jazygit
  merges every pull request through the REST `merge-async` endpoint instead. — learned 2026-11-22 · [ADR
  0024](../adr/0024-stacked-pull-requests.md)
- On a 409 from `merge-async` (a merge of it already pending), gh only reports why, not the id of the pending merge.
  *Why it matters:* an already-pending merge cannot be waited for, so jazygit never asks twice. — learned 2026-11-22 ·
  [ADR 0024](../adr/0024-stacked-pull-requests.md)
- gh has no command to rename a branch, and `gh pr edit` cannot change the head branch of a pull request. *Why it
  matters:* a remote rename can only be a push plus a delete, which closes any PR whose head is deleted. — learned
  2026-11-22 · [ADR 0090](../adr/0090-renaming-a-branch-on-the-remote.md)

## Checking out

- `gh pr checkout <number>` sets up the pull request's branch exactly as checking out in place does, also for a fork,
  where gh may name the branch differently from the head ref; it fetches, writing remote-tracking refs. *Why it
  matters:* a PR worktree is created detached and `gh pr checkout` run inside it, under the remote-refs guard. — learned
  2026-11-22 · [ADR 0010](../adr/0010-worktrees-from-anywhere.md), [ADR 0027](../adr/0027-guard-the-remote-refs.md)
- `gh pr checkout` moves HEAD and changes the working tree on every call. *Why it matters:* it is unusable for bringing
  in all branches of a stack at once. — learned 2026-11-22 · [ADR 0099](../adr/0099-checking-out-a-stack.md)

## Stacks and the gh-stack extension

- `gh pr create --base <branch below>` sets the base but does not link the pull request into a stack on GitHub. `gh
  stack link` (the gh-stack extension) or the Stacks API does that. *Why it matters:* branches an agent opens on each
  other are no stack until someone links them. — learned 2026-11-22 · [ADR
  0072](../adr/0072-stacks-in-the-branches-list.md), [ADR 0073](../adr/0073-making-and-unmaking-a-stack.md)
- The `gh-stack` extension, in preview, keeps its own list of a stack's branches in `.git/gh-stack`, a private JSON
  format. A stack made through the API shows up in that list only after `gh stack checkout` reads it from GitHub. *Why
  it matters:* jazygit neither reads nor writes `.git/gh-stack` and goes by GitHub. — learned 2026-11-22 · [ADR
  0072](../adr/0072-stacks-in-the-branches-list.md), [ADR 0073](../adr/0073-making-and-unmaking-a-stack.md)
- The `gh-stack` extension provides `gh stack checkout`, which checks out a stack. *Why it matters:* jazygit does the
  same with plain git and does not depend on the extension. — learned 2026-11-22 · [ADR
  0099](../adr/0099-checking-out-a-stack.md)
- `gh stack rebase` rebases one branch at a time from the bottom up, each `--onto` the new tip of the one below, and
  runs `git rebase` without `--rebase-merges`, so merge commits are dropped. GitHub's documentation calls its
  server-side stack rebase the same as `gh stack rebase`. *Why it matters:* dropping merges in jazygit's stack rebase
  matches what GitHub does. — learned 2026-11-22 · [ADR 0074](../adr/0074-rebasing-and-pushing-a-stack.md)

## gh api

- gh runs `--jq` filters with gojq, which differs from jq in nothing jazygit's filters used. *Why it matters:* the
  filters could be tested with jq, before ADR 42 moved decoding to Java. — learned 2026-11-22 · [ADR
  0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- `gh api graphql --paginate` follows the pages of one list only. *Why it matters:* the checks of the branches stay a
  query of their own. — learned 2026-11-22 · [ADR 0058](../adr/0058-less-reading-in-the-background.md)
- `gh api graphql --paginate --slurp` combines all pages of a paginated query into one array. *Why it matters:* review
  threads are read 100 per page in one call. — learned 2026-11-22 · [ADR
  0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)

## Run logs

- Since gh 2.104, gh refuses to print output containing escape sequences (such as the ANSI colours of a job log) unless
  given `--allow-escape-sequences`. Older gh rejects that flag as unknown and prints the escapes anyway. *Why it
  matters:* jazygit passes the flag and retries without it when gh says it does not know it. — learned 2026-11-22 · [ADR
  0160](../adr/0160-the-log-of-a-check.md) · gh 2.104
- `gh run view <run> --log-failed --repo <owner/name>` prints only the failed steps of a run's jobs. *Why it matters:*
  it is the command an agent is told to use to read logs, but jazygit's own log view reads the whole job log, since the
  failed steps alone drop what led up to the failure. — learned 2026-11-22 · [ADR
  0160](../adr/0160-the-log-of-a-check.md), [ADR 0161](../adr/0161-a-failed-build-handed-to-an-agent.md)
