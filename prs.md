# PR overview

A second tab of the app, next to the repo, that shows pull requests. It is for triage first ("what needs my
attention now?") and browsing the repo's PRs second.

## Tabs

- `ctrl+p` switches between the **Repo** tab and the **PRs** tab. The status panel shows `Repo · PRs` with the
  current tab highlighted.
- The PRs tab only changes the left column: the status panel stays on top, with three stacked panes below it.
  The content pane, the command log and the fetch line stay.
  - **Review requested**: open PRs that ask for your review, across all repos.
  - **My PRs**: your open PRs, across all repos.
  - **This repo**: all open PRs of the current repo, including PRs from forks.
- Each tab remembers its focused pane and its highlighted rows. The first time the PRs tab is shown, the first list
  that is not empty gets the focus, starting with "Review requested".

## Data

- **This repo**: its own `gh api graphql` call (at most 100), keeping PRs from forks, which also tells the
  repository's owner/name. It is separate from the call that feeds the PR symbols in the branches pane.
- **Review requested** and **My PRs**: one `gh api graphql` search, which also gives the checks and review state.
  At most 50 per list, with a dimmed "showing 50 of N" line when there are more.
- Loaded when the tab is shown, again on the one-minute fetch tick while it is shown, and with a refresh key.

## Rows

Number, title, author, age, checks and review state. Drafts are dimmed. Most recently updated first. A PR in
"This repo" that is also yours shows in "My PRs" too.

## Preview

The content pane shows the highlighted PR like a commit: a header, the description, `---`, then the diff from
`gh pr diff` (`-R owner/repo` for PRs of other repos).

## Keys

- **Enter**: move the focus into the preview.
- **`o`**: the existing PR dialog for a PR of the current repo; the browser for others.
- **`O`**: open in the browser.
- **Space**: check out (`gh pr checkout`) in place, refused with uncommitted changes. Works for PRs from forks. Only
  for PRs of the current repo.

A PR is "of the current repo" when its owner/repo is the repo's GitHub remote, whatever list it is in.

## When GitHub is not available

The tab is always there. Each pane says why it is empty: no `gh`, not logged in, or not a GitHub repo.

## Model

- The root `Model` gains only `Screen screen` (REPO or PRS) and `PrOverview prOverview`.
- `PrOverview` holds the three lists, the focus and highlighted row of its panes, the PR preview and what is being
  loaded.
- A sealed `PrOverviewMsg` and a `PrOverviewUpdate` that works on `PrOverview` alone. The root `Update` hands the
  messages over, and only steps in for switching tabs, the repo's GitHub remote and the PR dialog.
- The existing PR state of the branches pane stays where it is. Moving it into a `RepoGitHub` sub-record is a
  later refactor.

## Steps

Each its own commit, with tests.

1. [x] The tab, `ctrl+p`, the layout, and "This repo" with the PR dialog and the browser.
2. [x] The two cross-repo lists, from the graphql search.
3. [x] The preview: description plus diff.
4. [ ] Checkout.

## Later

- Show that a list changed since you last looked (a marker or a count). This needs the lists to load in the
  background while the tab is hidden, and to remember what was seen; PRs are kept by repo and number for this.
- Check out a PR into a new worktree.
- Review actions (approve, request changes) and the PR dialog's actions for PRs of other repos.
