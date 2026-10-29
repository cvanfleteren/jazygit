# 49. Fewer git processes per read

Date: 2026-10-29

## Context

Everything jazygit shows is read through the git command line, a process per question. A tick costs one
`git status`, and `CheckMetadata` keeps the other reads from running until their files change. A commit, which moves
HEAD and a branch at once, costs far more: 14 to 17 processes, and one more per base for stacked branches.

| Load | git processes |
|---|---|
| `LoadStatus` | `status --porcelain=v2 --branch` |
| `LoadCommits` | `rev-parse HEAD`, `log` |
| its `LoadAhead` | `config --get branch.<name>.gh-merge-base`, `rev-parse HEAD`, a `rev-parse` per base until one exists (up to 4), `log`, `rev-list --count` when the log is full, `merge-base`, `log --no-walk` |
| `LoadBranches` | `log --walk-reflogs`, `for-each-ref refs/heads refs/remotes` |
| its `LoadBaseDistance` | `config --get-regexp gh-merge-base`, `for-each-ref refs/heads refs/remotes/origin`, a `for-each-ref %(ahead-behind:<base>)` per base |

Several of these ask what is already known:

- HEAD is resolved three times, while the `# branch.oid` of the status just read says where it is.
- The bases of ADR 20 are full branch refs, resolved one `rev-parse` at a time, while one `for-each-ref` names the
  commit of any number of refs, and leaves out those that do not exist.
- `LoadBaseDistance` runs right after the branches are read, and reads the tips of the local and the `origin` refs
  again.
- `for-each-ref` takes any number of `%(ahead-behind:<base>)` atoms in one format, and counts them all in one
  process.
- The parents of all branches are read for the distances, and that of one branch again for the commits ahead.

## Decision

A read asks git only what it does not know yet, and asks it in as few processes as git allows. What it shows does
not change.

1. **The distances to the bases (ADR 46) take one process, or two.** `LoadBaseDistance` is given the tips of the
   branches just read, instead of reading them with a `for-each-ref` of its own, and counts every base in one
   `for-each-ref`, with an `%(ahead-behind:<commit>)` atom per base; each branch takes the column of its own base.
   Git then counts every branch against every base, which costs less than a process per base for any number of
   branches a repository has.
2. **HEAD is resolved once, by the status.** The log of HEAD and the commits ahead of its base start from the commit
   the status read last says HEAD is at, instead of a `rev-parse HEAD` each. Without commits, the status says so,
   and neither is read.
3. **The bases are resolved in one process.** A `for-each-ref --format=%(refname)%00%(objectname)` over the full
   refs of all the bases of ADR 20 tells which exist and where they are; the first, in the order of
   `BranchRef.bases`, counts. A `for-each-ref` pattern also matches the refs below it, `refs/heads/a` also
   `refs/heads/a/b`, so only the refs named exactly count. The tips of the branches of a divergence, of a log and of
   a first subject are read the same way, one process for both.
4. **The parents are read once.** The parents `LoadBaseDistance` reads with `git config --get-regexp` are kept in
   the `Repo`, and the commits ahead of the base go by them, instead of a `git config --get` each. Until the first
   read of the distances, or when a parent was just recorded by jazygit itself, the commits ahead read it as before.

A commit then costs about 9 processes instead of 15, and the distances to the bases 1 or 2 whatever the number of
bases.

### What stays

- `merge-base` and the `log --no-walk` of the fork point stay two processes: `git log --boundary` names the fork
  point in the same process, but not once `--max-count` cuts the log short.
- The reflogs and the refs of `LoadBranches` stay two processes: they are different commands.
- A divergence keeps a `log` per side: `git log --left-right ours...theirs` gives both in one, but `--max-count`
  limits the two together, not each.
- The configuration that only a fetch or a pull request asks for, the remotes, their URLs, `skipFetchAll` and
  `core.sshCommand`, is still read per question: it is not read on a refresh.
- The status of each other worktree, every 3 seconds, is what that feature costs.

### Consequences

- `GitInfoProvider.commits` and `ahead` take the commit HEAD is at, and `ahead` the parent of the branch, instead of
  finding them out. `baseDistance` takes the tips of the branches.
- What is read from the model can be a moment older than what git would say now. A tip or HEAD that moved since is
  read again by the next tick, as the status and the branches it moved are.
