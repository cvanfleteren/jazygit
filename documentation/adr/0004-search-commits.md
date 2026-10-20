# 4. Searching commits by message, author, path or content

Date: 2026-10-20

## Context

The commits pane shows the log of HEAD, at most `CliLog.MAX_COMMITS` (300) commits. Finding a commit further back,
or by who made it or what it touched, meant leaving jazygit for `git log`.

Popups already filter their lists with `/` (`ListFilter`), but that matches substrings of rows already loaded. Here it
would miss everything past the first 300 commits, and could not search by path or content at all, as a loaded
`Commit` holds neither its files nor its diff.

## Decision

`/` in the commits pane opens a line above the list to type a query in. Enter runs it through `git log`, and the pane
then shows only the matching commits, until Escape in the pane clears the search. Escape in the line only cancels the
edit. `/` while filtered opens the line again with the query in it.

This is for the log of HEAD in the commits pane only, not the logs of branches or the divergence views.

### The query

One line. Bare words, and the prefixes `author:`, `path:` and `-S`:

```
fix login author:alice author:"Jane Doe" path:src/ -S:token
```

- All bare words together are one phrase of the message: `--grep="fix login"`.
- `author:` and `path:` may repeat; their values are OR'd, as git does with `--author` and pathspecs.
- `-S:` at most once, as git takes only one: the pickaxe, commits that change how often the text occurs.
- A value with spaces goes in quotes.
- Only those prefixes count: `feat: …` or `fix(ui): …` are text of the message, as conventional commits are full of
  them. A prefix with nothing after it filters nothing.

The message, the authors and `-S` match as substrings (`--fixed-strings`), ignoring case unless one of them has an
uppercase letter (`--regexp-ignore-case`), as `ListFilter` does. git's `-i` makes `-S` ignore case too, so it cannot
keep its case apart from the others. Paths are literal and keep their case. No regex for now; it can come later as
its own prefix, such as `-G:`. No `--follow`, which git only allows for one path.

### The results

The query and its results live in the Model, beside the log and not instead of it, in `RepoView` as the divergence
does: `searchLine: Option<String>` while the line is open, and
`commitSearch: Option<CommitSearch(text, query, Loadable<List<Commit>> results)>`. `repo.commits()` stays the full log,
which the commit dialog's summary history and dropping commits read. The commit operations find the selected commit
through `Model.commit`, in the log or else among the results, which can be older than the log reads. Each time the
log is read again, the search runs again, so the pane stays right after an operation on a commit.

- At most `MAX_COMMITS` results; when there are that many, the title says so: `Commits: /fix author:bob (first 300)`.
- The list is flat: with commits left out, the parents no longer join up, so `CommitGraph` would draw broken rails.
- No matches shows a dimmed "No commits match", with the query in the title; a failing `git log`, e.g. on a bad
  pathspec, shows like any failed load.
- Every commit action stays on. They act on the sha, so squashing goes into the real parent, not the row shown below;
  their dialogs name the commits they touch.
- Clearing the search keeps the highlight on its commit in the full log, as `Reselect.following(sha)` does; when it
  is not among the first 300, the highlight stays on the same row.

### Where it lives

- `CommitQuery` and its parser in `jazygit-core`, pure and tested on its own.
- `CliLog.search(workTree, CommitQuery)` reads the log as `selected()` does, with the extra flags and the paths, but
  leaves out `CommitGraph.arrange`.
- The line is `FilterLine`, drawn out of `ListFilter`: its field and its editing (`LineEdit`), which the popups use
  too; not its matching.
- `CommitSearchMsg` and `CommitSearchUpdate` are in `state`, as `ui` cannot use the features; `feature.commit.search`
  declares the `/` key.

### Why this way

- In git, not in memory: it searches the whole history, and path and pickaxe need git anyway.
- In the pane, not a popup of results: the commit actions and the preview of the highlighted commit keep working.
- One line with prefixes, not a menu or a form: fast to type, combining for free, and each maps onto one git flag.
- On Enter, not as you type: each run is a git process, `-S` over a long history takes seconds, and a half-typed
  `path:` means nothing.
