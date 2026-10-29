# 49. Fewer git processes per read

Date: 2026-10-29

## Context

Everything jazygit shows is read through the git command line, a process per question. A tick costs one
`git status`, and `CheckMetadata` keeps the other reads from running until their files change. A commit, which moves
HEAD and a branch at once, costs far more: 14 to 18 processes, and one more per base for stacked branches.

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

1. **The distances to the bases (ADR 46) take two processes.** `LoadBaseDistance` is given the tips of the branches
   just read, instead of reading them with a `for-each-ref` of its own. It reads the parents with
   `git config --get-regexp`, as before, and counts every base in one `for-each-ref`, with an
   `%(ahead-behind:<commit>)` atom per base; each branch takes the column of its own base. Git then counts every
   branch against every base, which costs less than a process per base for any number of branches a repository has.
   One base git cannot find fails the whole `for-each-ref`, without a count: a base rewritten and pruned since the
   branches were read. Then each base is counted in a `for-each-ref` of its own, as before, so that only the
   branches of that base show none (ADR 46).
2. **HEAD is resolved once, by the status.** The log of HEAD and the commits ahead of its base start from the commit
   the status read last says HEAD is at, instead of a `rev-parse HEAD` each. Without commits, the status says so,
   and neither is read.
3. **The bases come from the branches read.** The bases of ADR 20 are local branches and those on `origin`, whose
   tips the model holds since the branches were read: the first of `BranchRef.bases` among them counts, at the tip
   the model has, without a `rev-parse` each. The commits ahead are then counted against the tip whose move
   `AheadUpdate.branchesLoaded` reads them again for, as the distances of decision 1 are. Before the branches are
   first read, no base is known, and the commits ahead are read once they are, as now when the main branch becomes
   known.
4. **The parents are read once.** The parents `LoadBaseDistance` reads are kept in the `Repo`, and the commits ahead
   of the base go by them, instead of a `git config --get` each. Once they are read, the commits ahead whose base is
   another one by them are read again. A parent jazygit records itself comes with a new branch, so the read of the
   branches that follows reads the parents again too.

The tips of the two branches of a divergence and of a first subject, which the user asks for, are read in one
`for-each-ref --format=%(refname)%00%(objectname)` over their full refs instead of a `rev-parse` each. A
`for-each-ref` pattern also matches the refs below it, `refs/heads/a` also `refs/heads/a/b`, so only the refs named
exactly count. `%(objectname)` is the object the ref points to, not its commit as `rev-parse <ref>^{commit}` says:
the two agree as branches point to commits.

### What a commit costs

| Load | today | then |
|---|---|---|
| `LoadStatus` | 1 | 1 |
| `LoadCommits` | 2: `rev-parse HEAD`, `log` | 1: `log` |
| its `LoadAhead` | 6–10: `config`, `rev-parse HEAD`, 1–4 `rev-parse`, `log`, `rev-list --count` when full, `merge-base`, `log --no-walk` | 3–4: `log`, `rev-list --count` when full, `merge-base`, `log --no-walk` |
| `LoadBranches` | 2 | 2 |
| its `LoadBaseDistance` | 2 + 1 per base | 2: `config --get-regexp`, `for-each-ref` |
| **total, one base** | **14–18** | **9–10** |

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

- `GitInfoProvider.commits` takes the commit HEAD is at, and `ahead` the tip of the branch and its base with the tip
  of that, instead of finding them out. `baseDistance` takes the tips of the branches.
- `Cmd.LoadAhead` holds the commit HEAD is at, and the base with its tip, so two loads of it are equal only when they
  read from the same HEAD, against the same base where it is.
- What is read from the model can be older than what git would say now. The status and the branches are read side
  by side, and either can arrive first: after a `git pull --rebase`, which moves HEAD and `origin/main` together, the
  branches can ask for the commits ahead of the HEAD of the status before. So the commits ahead of HEAD that arrive
  on their own, not with a log, are kept as those that arrive with one are: only when their load is the one
  `AheadUpdate.ofHead` makes of the model now; otherwise that one is read instead. The same holds for a base that moved. Without it, the commits ahead read
  for the old HEAD would be hidden, as they are not of the log shown, and nothing would read them again until HEAD
  or the base moved once more.
