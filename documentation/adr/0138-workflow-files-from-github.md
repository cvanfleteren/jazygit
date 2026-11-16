# 138. The file of a workflow GitHub has, when git does not

Date: 2026-11-16

## Context

The builds of a branch draw the jobs of a build hanging off the jobs they wait for (ADR 131). GitHub does not say
which job needs which; the file of the workflow does. ADR 131 reads it with `git show <sha>:<path>` at the commit of
the build, and a build whose commit is not fetched shows its jobs without lines.

jazygit's own `Build` workflow runs only on pull requests: pushes to main are built by `publish.yml`, which calls it.
Every one of its runs is on the head commit of a pull request, and a pull request that is squash-merged and has its
branch deleted leaves that commit out of every clone that fetched only main, or pruned the branch since. On every
branch (ADR 133), the last builds of `Build` are all such commits: none of them showed which job waited for which.

GitHub keeps the head commit of a pull request after its branch is deleted (`refs/pull/N/head`), and gives a file
of any commit it has with `GET repos/{owner}/{repo}/contents/{path}?ref={sha}`; with
`Accept: application/vnd.github.raw`, as it is rather than base64 in JSON.

## Decision

- A file of a workflow that git does not have at the commit of a build is read from GitHub at that commit, as are the
  workflows of this repository its jobs call. git first: it needs no request and works offline.
- Not: `git fetch origin <sha>`, which works as well but adds objects to the user's repository behind their back. Not:
  the file as it is now on the default branch, which draws a build with jobs it did not have, or without some it had.
- What GitHub answers is read up to 1 MB or 20,000 lines; more is no workflow to draw. A file GitHub does not have
  either, or a request that fails, shows the build without lines, as before, until the builds are shown again: like
  the builds and the runs, a file is read once a view, and a failed read is not tried again. Not: reading it again
  on every update while GitHub cannot be reached, nor a state of its own for a read that failed, for a request that
  follows one GitHub just answered.
- Like the git read, it runs in the background, once a view for each file, and is not in the command log: it changes
  nothing.

## Consequences

- The builds of pull requests merged since show which job waits for which, on every branch too.
- A build whose commit is not fetched costs a request to GitHub per workflow file, on top of those that read the
  builds.
- Still not drawn: the jobs of a pull request's build that its head commit's file does not have. A `pull_request` run
  runs the workflow of the merge of the pull request into its base, which GitHub does not name; its head commit is
  what the run says it built. A pull request that branched off before its base changed the workflow, as one from
  before `package` became `jar` and `checks`, shows the jobs it has no match for without lines, as ADR 131 does for
  any job that does not match. Not yet: reading the file of the base as it was when the run started.
