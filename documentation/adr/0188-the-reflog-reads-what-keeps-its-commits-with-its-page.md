# 188. The reflog reads what keeps its commits with its page

Date: 2026-11-23

## Context

ADR 186 has the reflog find which commits no branch, tag or remote reaches with one more process a page, "run after
the page is drawn, so the list never waits for it". Built that way, each look was a background command of its own,
and they ran on a pool of threads, so a look could end after a newer one. The newer look's marks were then thrown
away (a full look of the first page that ended after the look of the second page). Undoing that took a count of the
reads and which looks were older, kept in the model.

ADR 186 also counts as lost what HEAD still reaches. On a detached HEAD, or in a rebase stopped part way, every
commit made since, but the one HEAD is at, was marked `✗` and said "only the reflog keeps it", which is not so: HEAD
keeps them, as it keeps the one it is at, until it moves away.

On this repository a page of 200 entries takes 16 ms to read, a look at its commits 20 ms. A look at every reflog
at once, `git rev-list --reflog --not …`, needs no page, so it could run beside the read, but it takes 44 ms, grows
with every reflog of the repository, and gives the same answer for each page.

## Decision

- **The read of a page finds what keeps its commits**: `ReadReflog` reads the page, then `History.lost` for its
  commits, and both come back in one `ReflogMsg.Read`. What keeps the commits is taken in under the same rule as the
  entries, the read that runs, so a look cannot end after a newer one. From the top it replaces what was known; a later
  page's is added for its own commits. When it cannot be found, the entries show unmarked. This amends ADR 186: the
  rows wait for the look, 20 ms on this repository.
- **What keeps a commit is a `Kept`** of two sets: `lost`, what nothing but the reflog keeps, marked `✗`; and `byHead`,
  what only HEAD reaches, unmarked in the row, whose heading says it is lost once HEAD moves away. The commit HEAD is
  at keeps its `◆`.
- **`History.lost` runs its two processes at the same time**: `git rev-list --ignore-missing <commits> --not
  --branches --tags --remotes`, and the same with `HEAD` among what it leaves out; what the first lists and the
  second does not, HEAD reaches. git resolves `HEAD` as it runs, so it is where HEAD is then, not where the status
  read before an operation said; `--ignore-missing` makes a HEAD without a commit reach nothing, where `HEAD` alone
  would fail.
- **`Parallel.both` in `core-process`** runs the two, each on a virtual thread, and waits for both before it returns,
  in a `try` with resources: nothing it starts outlives the call. `StructuredTaskScope` is a preview in Java 25, and the
  app uses no preview; when it is final, only `Parallel` changes. It throws nothing: it gives an `Either`, failed with an
  `InterruptedException` when the wait is interrupted (the thread stays interrupted), or with what one of the two
  threw (not a `Try`, which throws an `InterruptedException` again rather than hold it); `History.lost` makes those
  `GitError.Interrupted` and `GitError.Broke`.

## Consequences

- A page shows once what keeps its commits is known: 36 ms on this repository in place of 16 ms.
- No count of reads, and no look that can undo a newer one, is kept in the model.
- On a detached HEAD, only what nothing reaches is `✗`.
- `core-git` runs processes at the same time for the first time, always through `Parallel`.
