# Architecture decision records

Why jazygit is as it is: one decision a file, numbered in the order they were made. The code, its Javadoc and the
tests say what jazygit does; an ADR says why, and what was tried or turned down.

## Numbers are never reused

The code, the other ADRs and the commit messages cite an ADR by its number (`ADR 125`), so a number keeps its meaning
for good. An ADR is never renumbered, and a number that is gone is not given to a new one:

- **Folded into another ADR.** A small ADR whose lasting part fits in a bigger one is moved there, under a heading
  `Folded in: ADR N (date)`, and its file is kept as a stub that says where it went. Citations in the code are moved
  to the ADR it was folded into; those in other ADRs are left as written, and the stub resolves them.
- **Removed.** An ADR with nothing left to say is deleted, and this index says why. Its text is in git:
  `git log --all -p -- 'documentation/adr/NNNN-*'`.
- **Amended.** An ADR that a later one changes gets a line at its end, `Amended by ADR N: …`, and its body is left as
  it was.

## What git, GitHub, Forgejo and others do

How the tools and services jazygit stands on behave, learned while making a decision (a git flag that is a no-op, a
GitHub field that is null, a terminal that drops an escape), is in [`../facts`](../facts/README.md), one file per
tool, each fact with the date it was learned and the ADR it came from. An ADR that learns something new about
another tool adds it there too.

## Index

| ADR | Title | Status |
|---|---|---|
| 1 | [Move commits to branch](0001-move-commits-to-branch.md) |  |
| 2 | [What lives in the Model, and what may stay in the widgets](0002-model-and-view-state.md) |  |
| 3 | [A new pull request merges into the branch it came from](0003-pr-base-defaults-to-parent-branch.md) |  |
| 4 | [Searching commits by message, author, path or content](0004-search-commits.md) |  |
| 5 | [Commenting on a pull request from its dialog](0005-comment-on-pull-request.md) |  |
| 6 | [Viewing the comments of a pull request](0006-view-pull-request-comments.md) |  |
| 7 | [Counting the review threads of a pull request](0007-review-thread-counts.md) |  |
| 8 | [Resizing the left column](0008-resize-left-column.md) |  |
| 9 | [Working with worktrees](0009-worktree-ux.md) |  |
| 10 | [Worktrees from anywhere, and removed with their branch](0010-worktrees-from-anywhere.md) |  |
| 11 | [More detail in the worktrees list](0011-worktree-detail.md) |  |
| 12 | [A pool for the background commands](0012-background-pool.md) |  |
| 13 | [Reverting a commit](0013-revert-commit.md) |  |
| 14 | [Draw a frame only when something changed](0014-redraw-on-change.md) |  |
| 15 | [Check the git directory before reading the branches, the stashes and the worktrees](0015-check-metadata-before-loading.md) |  |
| 16 | [Show the diff of the highlighted stash](0016-stash-diff.md) |  |
| 17 | [Fetch from the remotes at startup](0017-fetch-at-startup.md) |  |
| 18 | [One dialog to create a pull request](0018-one-dialog-to-create-a-pull-request.md) |  |
| 19 | [Refresh the pull request dialog](0019-refresh-the-pull-request-dialog.md) |  |
| 20 | [Telling the commits of a branch from those on its base](0020-commits-not-on-the-base.md) |  |
| 21 | [Ages in minutes, told by a clock apart from the refresh](0021-a-clock-apart-from-the-refresh.md) | folded into ADR 33 |
| 22 | [How long ago the remotes were fetched, in steps of 10s](0022-fetched-ago-in-steps-of-10s.md) | folded into ADR 33 |
| 23 | [View the commits of a branch](0023-view-a-branchs-commits.md) |  |
| 24 | [Stacked pull requests](0024-stacked-pull-requests.md) |  |
| 25 | [Push before creating a pull request](0025-push-before-creating-a-pull-request.md) |  |
| 26 | — | Commits of a pull request: removed on 2026-11-22, as it only added `commits.totalCount` to a line. |
| 27 | [Guard the remote refs](0027-guard-the-remote-refs.md) |  |
| 28 | [Start outside a repository](0028-start-outside-a-repository.md) |  |
| 29 | [Unseen pull requests](0029-unseen-pull-requests.md) |  |
| 30 | [Move commits back onto the branch's own line](0030-move-commits-back-onto-the-branchs-own-line.md) |  |
| 31 | [Every preview takes a slot, and failed reads back off](0031-preview-slots-and-retry-backoff.md) |  |
| 32 | [How long checks take, and when they are done](0032-how-long-checks-take.md) |  |
| 33 | [Show that work is being done](0033-show-that-work-is-being-done.md) |  |
| 34 | [Check times to the second in the dialogs](0034-check-times-to-the-second-in-the-dialogs.md) |  |
| 35 | [A workflow that waits to start is queued](0035-a-workflow-that-waits-to-start-is-queued.md) |  |
| 36 | [Wait for the jobs GitHub has not made yet](0036-wait-for-jobs-not-made-yet.md) |  |
| 37 | [When the checks started and ended](0037-when-the-checks-started-and-ended.md) | folded into ADR 32 |
| 38 | [A popup closes by itself](0038-a-popup-closes-by-itself.md) |  |
| 39 | [What runs is told from `Jobs.running`](0039-what-runs-is-told-from-running.md) |  |
| 40 | [Typed revisions and remotes](0040-typed-revisions-and-remotes.md) |  |
| 41 | [A branch is as old as its last activity](0041-a-branch-is-as-old-as-its-last-activity.md) |  |
| 42 | — | Never written. The code still cites it for gh's JSON decoded with the `Decoder`s of `lib-json`, one query class per read, and ADR 58 for the second query of how far a pull request is behind its base. |
| 43 | [Nuke a branch](0043-nuke-a-branch.md) |  |
| 44 | [A nicer commit diff, with a tree of its files](0044-a-nicer-commit-diff-with-a-file-tree.md) |  |
| 45 | [Two lines per branch](0045-two-lines-per-branch.md) |  |
| 46 | [How far a branch is behind its base](0046-how-far-a-branch-is-behind-its-base.md) |  |
| 47 | [The rich view for the working tree](0047-the-rich-view-for-the-working-tree.md) |  |
| 48 | [One set of pull request symbols](0048-one-set-of-pull-request-symbols.md) | folded into ADR 45 |
| 49 | [Fewer git processes per read](0049-fewer-git-processes-per-read.md) |  |
| 50 | [A diff is parsed where it is read](0050-a-diff-is-parsed-where-it-is-read.md) |  |
| 51 | [A rewritten line spends none of the word budget](0051-a-rewritten-line-spends-none-of-the-word-budget.md) |  |
| 52 | [The status is read every second](0052-the-status-is-read-every-second.md) | folded into ADR 125 |
| 53 | [Syntax in the rich view](0053-syntax-in-the-rich-view.md) |  |
| 54 | [Whitespace between changed words is highlighted](0054-whitespace-between-changed-words-is-highlighted.md) |  |
| 55 | [Review remarks in a pull request's diff](0055-review-remarks-in-a-pull-requests-diff.md) |  |
| 56 | [Fewer git processes after a commit](0056-fewer-git-processes-after-a-commit.md) | A second ADR 56, *A parsed diff is one tree*, was removed on 2026-11-22: a refactoring the code shows. |
| 57 | [Only the files with remarks](0057-only-the-files-with-remarks.md) |  |
| 58 | [Less reading in the background](0058-less-reading-in-the-background.md) |  |
| 59 | [Edit a pull request](0059-edit-a-pull-request.md) |  |
| 60 | [A cursor in the rich view, to comment on lines](0060-a-cursor-in-the-rich-view-to-comment-on-lines.md) |  |
| 61 | [The files of a branch](0061-the-files-of-a-branch.md) |  |
| 62 | [A thread or a comment collapsed or open](0062-a-thread-or-a-comment-collapsed-or-open.md) |  |
| 63 | [State per repository, and threads stay collapsed or open](0063-state-per-repository.md) |  |
| 64 | [Delete a comment](0064-delete-a-comment.md) |  |
| 65 | [Edit a comment](0065-edit-a-comment.md) |  |
| 66 | — | Actions in two columns: removed on 2026-11-22, a layout option of `ChoiceList` with no decision in it. |
| 67 | [Reading less, rather than nothing, in the background](0067-reading-less-rather-than-nothing-in-the-background.md) |  |
| 68 | [Asking where to check out, in a linked worktree](0068-asking-where-to-check-out-in-a-linked-worktree.md) |  |
| 69 | [Git operations in roles](0069-git-operations-in-roles.md) |  |
| 70 | [A theme per repository](0070-a-theme-per-repository.md) |  |
| 71 | [A load keeps what it reads in a target](0071-a-load-keeps-what-it-reads-in-a-target.md) | A second ADR 71, *The git roles as built*, was folded into ADR 69 on 2026-11-22. |
| 72 | [Stacks in the branches list](0072-stacks-in-the-branches-list.md) |  |
| 73 | [Making and unmaking a stack](0073-making-and-unmaking-a-stack.md) |  |
| 74 | [Rebasing and pushing a stack](0074-rebasing-and-pushing-a-stack.md) |  |
| 75 | [One git error type](0075-one-git-error-type.md) |  |
| 76 | [The amount of context in a diff](0076-the-amount-of-context-in-a-diff.md) |  |
| 77 | [Showing more of one hunk](0077-showing-more-of-one-hunk.md) |  |
| 78 | [An area is given only the lines that show](0078-an-area-is-given-only-the-lines-that-show.md) |  |
| 79 | [A diff is read only up to 50 000 lines or 4 MiB](0079-a-diff-is-read-only-up-to-50000-lines-or-4-mib.md) |  |
| 80 | [A changed line goes on to the edge of the area](0080-a-changed-line-goes-on-to-the-edge-of-the-area.md) |  |
| 81 | [The left column slides to its width](0081-the-left-column-slides-to-its-width.md) |  |
| 82 | [An image in a comment is a placeholder](0082-an-image-in-a-comment-is-a-placeholder.md) |  |
| 83 | [Changed lines are shown in pairs](0083-changed-lines-are-shown-in-pairs.md) |  |
| 84 | [An image in a review remark is drawn in half blocks](0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md) |  |
| 85 | [A review remark opens in the browser](0085-a-review-remark-opens-in-the-browser.md) |  |
| 86 | [Reactions on review comments](0086-reactions-on-review-comments.md) |  |
| 87 | [The stacks shown or not](0087-the-stacks-shown-or-not.md) | folded into ADR 72 |
| 88 | [A browser per account](0088-a-browser-per-account.md) |  |
| 89 | [How far the checks are](0089-how-far-the-checks-are.md) |  |
| 90 | [Renaming a branch on the remote](0090-renaming-a-branch-on-the-remote.md) |  |
| 91 | [Rebasing on the base branch](0091-rebasing-on-the-base-branch.md) |  |
| 92 | [Resizing and maximizing panes with keys](0092-resizing-and-maximizing-panes-with-keys.md) |  |
| 93 | [The index written anew now and then](0093-the-index-written-anew-now-and-then.md) | folded into ADR 97 |
| 94 | [Copying lines of a diff](0094-copying-lines-of-a-diff.md) |  |
| 95 | [A taller pane grows as far as a maximized one](0095-a-taller-pane-grows-as-far-as-maximized.md) | folded into ADR 92 |
| 96 | [Opening the links of a comment](0096-opening-the-links-of-a-comment.md) |  |
| 97 | [The index written anew once it changed](0097-the-index-written-anew-once-it-changed.md) |  |
| 98 | [References and bare links in a comment](0098-references-and-bare-links-in-a-comment.md) |  |
| 99 | [Checking out a stack](0099-checking-out-a-stack.md) |  |
| 100 | [Approving a pull request, or asking for changes](0100-approving-or-asking-for-changes.md) |  |
| 101 | — | A cancelled load reports nothing: removed on 2026-11-22, it only recorded the removal of dead code. |
| 102 | [The original or the new version of a file](0102-the-original-or-the-new-version-of-a-file.md) |  |
| 103 | [A version of Markdown rendered](0103-a-version-of-markdown-rendered.md) |  |
| 104 | [The left column collapses](0104-the-left-column-collapses.md) |  |
| 105 | [Filtering the files of a tree](0105-filtering-the-files-of-a-tree.md) |  |
| 106 | [A filter matches the first letters of words](0106-a-filter-matches-the-first-letters-of-words.md) |  |
| 107 | [The parts of state depend one way](0107-the-parts-of-state-depend-one-way.md) |  |
| 108 | [All files of the working tree](0108-all-files-of-the-working-tree.md) |  |
| 109 | [A remote branch checked out in a new worktree](0109-a-remote-branch-checked-out-in-a-new-worktree.md) |  |
| 110 | [A worktree opened in an editor](0110-a-worktree-opened-in-an-editor.md) |  |
| 111 | [The settings file opened from the settings](0111-the-settings-file-opened-from-the-settings.md) |  |
| 112 | [Merging a pull request when it is ready](0112-merging-a-pull-request-when-it-is-ready.md) |  |
| 113 | [Annotations in the rich view](0113-annotations-in-the-rich-view.md) |  |
| 114 | [GitHub operations in roles](0114-github-operations-in-roles.md) |  |
| 115 | [A popup that composes text](0115-a-popup-that-composes-text.md) |  |
| 116 | [A force push only over what the branch had](0116-a-force-push-only-over-what-the-branch-had.md) |  |
| 117 | [The next file from the diff](0117-the-next-file-from-the-diff.md) |  |
| 118 | [Left held down stops at the start of the lines](0118-left-held-down-stops-at-the-start-of-the-lines.md) |  |
| 119 | [A failed merge gives way once the pull request changed](0119-a-failed-merge-gives-way-once-the-pull-request-changed.md) |  |
| 120 | [git and gh in packages](0120-git-and-gh-in-packages.md) |  |
| 121 | [What the name of a new branch starts out as](0121-what-the-name-of-a-new-branch-starts-out-as.md) |  |
| 122 | [A version of JSON rendered](0122-a-version-of-json-rendered.md) |  |
| 123 | [A version of YAML rendered](0123-a-version-of-yaml-rendered.md) |  |
| 124 | [A frame when a time shown changes](0124-a-frame-when-a-time-shown-changes.md) |  |
| 125 | [Reading the status every 3 s, and turning the timers' reads off](0125-reading-the-status-every-3-s-and-turning-the-timers-off.md) |  |
| 126 | [Searching the content panel](0126-searching-the-content-panel.md) |  |
| 127 | [A feature is wired in one place](0127-a-feature-is-wired-in-one-place.md) |  |
| 128 | [All files of a commit](0128-all-files-of-a-commit.md) |  |
| 129 | [The command log takes the focus](0129-the-command-log-takes-the-focus.md) |  |
| 130 | [The log reads 3000 commits](0130-the-log-reads-3000-commits.md) |  |
| 131 | [The builds of a branch](0131-the-builds-of-a-branch.md) |  |
| 132 | [Older builds on demand](0132-older-builds-on-demand.md) | folded into ADR 131 |
| 133 | [The timings of one build](0133-the-timings-of-one-build.md) |  |
| 134 | [Where a new worktree starts out](0134-where-a-new-worktree-starts-out.md) |  |
| 135 | [Running failed checks again](0135-rerun-failed-checks.md) |  |
| 136 | [The application in modules](0136-the-application-in-modules.md) |  |
| 137 | [Checking out a commit](0137-checking-out-a-commit.md) |  |
| 138 | [The file of a workflow GitHub has, when git does not](0138-workflow-files-from-github.md) |  |
| 139 | [The old and the new file side by side](0139-the-old-and-the-new-file-side-by-side.md) |  |
| 140 | [Going to a line](0140-going-to-a-line.md) |  |
| 141 | [The files follow the highlight](0141-the-files-follow-the-highlight.md) |  |
| 142 | [No listing of only the commits not on the base](0142-no-listing-of-only-the-commits-ahead.md) | folded into ADR 143 |
| 143 | [Setting the base of a branch](0143-setting-the-base-of-a-branch.md) |  |
| 144 | [Filters that hide hunks of a diff](0144-filters-that-hide-hunks-of-a-diff.md) |  |
| 145 | [A file opened in an editor, at a line](0145-a-file-opened-in-an-editor-at-a-line.md) |  |
| 146 | [An editor that fails is in the command log, and one that exits as it starts hints at a terminal](0146-a-failed-editor-in-the-command-log.md) |  |
| 147 | [Blame on a version of a file](0147-blame-on-a-version-of-a-file.md) |  |
| 148 | [Adding a branch without a pull request to a stack](0148-adding-a-branch-without-a-pull-request-to-a-stack.md) |  |
| 149 | [Enter locks the files to a list](0149-enter-locks-the-files-to-a-list.md) |  |
| 150 | [ssh asks jazygit for a key's passphrase](0150-ssh-asks-jazygit-for-a-passphrase.md) |  |
| 151 | [A version of XML rendered](0151-a-version-of-xml-rendered.md) |  |
| 152 | [Each kind of content draws itself](0152-each-kind-of-content-draws-itself.md) |  |
| 153 | [The askpass socket opens when ssh may ask](0153-the-askpass-socket-opens-when-ssh-may-ask.md) | folded into ADR 150 |
| 154 | [Features contribute what the content panel shows](0154-features-contribute-what-the-content-panel-shows.md) |  |
| 155 | [What an area shows has its own keys, scroll value and focus](0155-what-an-area-shows-keys-and-focuses-itself.md) |  |
| 156 | [A feature keeps what it shows in the content panel in a content package](0156-a-feature-keeps-its-content-in-a-content-package.md) | folded into ADR 157 |
| 157 | [A feature keeps its part of the model](0157-a-feature-keeps-its-part-of-the-model.md) |  |
| 158 | [A feature's code lives in its feature's module](0158-a-features-code-lives-in-its-features-module.md) |  |
| 159 | [The modules in groups](0159-the-modules-in-groups.md) |  |
| 160 | [The log of a check](0160-the-log-of-a-check.md) |  |
| 161 | [A failed build handed to an agent](0161-a-failed-build-handed-to-an-agent.md) |  |
| 162 | [The agent is told the account of the repository](0162-the-agent-is-told-the-account-of-the-repository.md) |  |
| 163 | [A feature owns its settings](0163-a-feature-owns-its-settings.md) |  |
| 164 | [The panel edits a list of the settings](0164-the-panel-edits-a-list-of-the-settings.md) |  |
| 165 | [What a repository is made of is `core-repo`](0165-what-a-repository-is-made-of-is-core-repo.md) |  |
| 166 | [The features add timers and answer the kernel's events](0166-the-features-add-timers-and-answer-the-kernels-events.md) | folded into ADR 158 |
| 167 | [A feature draws in the pane of another](0167-a-feature-draws-in-the-pane-of-another.md) |  |
| 168 | [A feature brings a screen](0168-a-feature-brings-a-screen.md) |  |
| 169 | [GitHub's state leaves the kernel](0169-githubs-state-leaves-the-kernel.md) |  |
| 170 | [Properties in the rich view](0170-properties-in-the-rich-view.md) |  |
| 171 | [GitHub can be deleted](0171-github-can-be-deleted.md) |  |
| 172 | [A pane declares keys the features fill](0172-a-pane-declares-keys-the-features-fill.md) |  |
| 173 | [A test proves GitHub can be deleted](0173-a-test-proves-github-can-be-deleted.md) |  |
| 174 | [How the last build on Forgejo went](0174-how-the-last-build-on-forgejo-went.md) |  |
| 175 | [Each forge has its own pull-request dialogs](0175-each-forge-has-its-own-pull-request-dialogs.md) |  |
| 176 | [The pull request of a branch on Forgejo](0176-the-pull-request-of-a-branch-on-forgejo.md) |  |
| 177 | [The forge of a repository, decided or asked](0177-the-forge-of-a-repository-decided-or-asked.md) |  |
| 178 | [Creating a pull request on Forgejo](0178-creating-a-pull-request-on-forgejo.md) |  |
| 179 | [fj's token goes where the remote points, over plain HTTP too](0179-fjs-token-goes-where-the-remote-points.md) | folded into ADR 174 |
| 180 | [Merging a pull request on Forgejo](0180-merging-a-pull-request-on-forgejo.md) |  |
| 181 | [Merging on Forgejo when the checks succeed](0181-merging-on-forgejo-when-the-checks-succeed.md) |  |
| 182 | [Another worktree of the repository opens with what the worktrees share](0182-another-worktree-opens-with-what-the-repository-shares.md) |  |
| 183 | [The programs the user set are started through a launcher, not through git](0183-programs-are-started-through-a-launcher.md) |  |
| 184 | [The diff filters are the diffs feature's](0184-the-diff-filters-are-the-diffs-features.md) |  |
| 185 | [A pane has tabs the features add](0185-a-pane-has-tabs-the-features-add.md) |  |
| 186 | [The reflog of HEAD, read only](0186-the-reflog-of-head-read-only.md) |  |
| 187 | [A source heads what another shows](0187-a-source-heads-what-another-shows.md) |  |
| 188 | [The reflog reads what keeps its commits with its page](0188-the-reflog-reads-what-keeps-its-commits-with-its-page.md) |  |
| 189 | [The branches pane has the tabs of any pane](0189-the-branches-pane-has-the-tabs-of-any-pane.md) |  |
| 190 | [An image in a comment of the conversation is drawn too](0190-an-image-in-a-comment-of-the-conversation-is-drawn.md) |  |
| 191 | [The terminal draws a picture sharp where it can: Kitty, else Sixel, else half blocks](0191-the-terminal-draws-a-picture-sharp-where-it-can.md) |  |
| 192 | [A file of a pull request is edited while its branch is checked out](0192-a-file-of-a-pull-request-is-edited-while-its-branch-is-checked-out.md) |  |
| 193 | [A long line of a text box wraps, and the commit description marks column 72](0193-a-long-line-of-a-text-box-wraps.md) |  |
| 194 | [A branch, a reset and a checkout at an entry of the reflog](0194-a-branch-a-reset-and-a-checkout-at-a-reflog-entry.md) |  |
| 195 | [The checks of a branch on Forgejo, one by one](0195-the-checks-of-a-branch-on-forgejo-one-by-one.md) |  |
| 196 | [A program left running outlives the terminal jazygit runs in](0196-a-program-left-running-outlives-the-terminal.md) |  |
| 197 | [A rebase that stopped shows where it stopped, in every worktree](0197-a-rebase-that-stopped-shows-where-it-stopped.md) |  |
| 198 | [A conflict is resolved in the app, a file at a time](0198-a-conflict-is-resolved-in-the-app-a-file-at-a-time.md) |  |
| 199 | [The plan of an operation that stopped shows in the commits pane](0199-the-plan-of-an-operation-that-stopped-shows-in-the-commits-pane.md) |  |
| 200 | [A dialog can tell what every choice does in it, and Enter resolves a file in conflict](0200-a-dialog-can-tell-what-every-choice-does-in-it.md) |  |
| 201 | [A file in conflict is handed to a merge tool or to mergiraf, and opens at its first conflict](0201-a-file-in-conflict-is-handed-to-a-merge-tool-or-mergiraf.md) |  |
| 202 | [A feature fills slots, which the kernel and the panes declare](0202-a-feature-fills-slots-the-kernel-and-the-panes-declare.md) |  |
| 203 | [A slot is its name](0203-a-slot-is-its-name.md) |  |
| 204 | [The forges split their packages by flow](0204-the-forges-split-their-packages-by-flow.md) |  |
| 205 | [A feature says its notices itself](0205-a-feature-says-its-notices-itself.md) |  |
| 206 | [A feature asks the kernel to open a file, and the feature that opens files answers](0206-a-feature-asks-the-kernel-to-open-a-file.md) |  |
| 207 | [An update asks the app to move the focus, and to step the files](0207-an-update-asks-the-app-to-move-the-focus.md) |  |
| 208 | [The files pane leaves the kernel, and the resolving marks its files through slots](0208-the-files-pane-leaves-the-kernel.md) |  |
| 209 | [The flows every forge has are written once, in features-forge, and each forge gives a port](0209-the-flows-every-forge-has-are-written-once.md) |  |
