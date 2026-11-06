# 85. A review remark opens in the browser

Date: 2026-11-05

## Context

The diff of a pull request's files shows its review remarks, with a cursor that stops on each thread and on each
reply of an open one (ADR 55, ADR 60). Much is still done on GitHub's site: reading a long discussion, reacting,
suggesting a change. Getting there meant leaving the diff for the pull request dialog, whose `o` opens the pull request
in the browser, and finding the thread again on GitHub.

GitHub gives every review comment an anchor in the conversation of its pull request:
`https://{host}/{owner}/{repo}/pull/{number}#discussion_r{id}`, with the comment's REST id, which jazygit reads already
(`fullDatabaseId`, `ReviewThread.Comment.restId`) to reply to it. The files tab has one too, `/files#r{id}`, but it
shows no outdated thread, and a large diff loads its files later than the page, so the anchor can miss.

ADR 6 left opening one comment out: `gh pr view --web` drops the `#…` of a link, gh opens nothing else but pull
requests, branches and files, and jazygit started no browser of its own. AWT's `Desktop.browse` is not an option:
the native image would need eight more files beside it.

## Decision

### The key

In the diff of a pull request's files, `o` opens the pull request in the browser, on the comment of the cursor:

- on a reply, that reply;
- on a thread, its first comment, which its stop is (ADR 62);
- anywhere else, on a file, a line or a gap, the pull request, without an anchor.

The link goes to the conversation, `…/pull/{number}#discussion_r{id}`, which shows outdated and resolved threads as
well. It is made from what the files of the pull request already hold (its host, repository and number) and the
comment's REST id: nothing more is read from GitHub.

Opening only reads, so it is never refused: not for a merged or closed pull request, nor for threads read at other
commits than the files, nor for a comment being deleted or a thread something is sent for.

The comments popup of ADR 6 keeps its `o`, which opens the pull request.

### The browser

jazygit now starts a browser of its own, the one gh would start, in gh's order:

1. `$GH_BROWSER`;
2. gh's `browser` setting, `gh config get browser --host {host}`;
3. `$BROWSER`;
4. on Linux, `xdg-open`.

The setting is split into words as a shell would, with quotes and backslashes, and the link added as its last
argument, as gh does. The browser is started with its input closed and its output thrown away, and not waited for:
once it is still running a moment later, or exited with 0, it is taken to have opened. A browser that exits with
another code, or does not start, fails the opening.

On another system than Linux, without a browser set, `gh pr view {url} --web` opens the pull request: without the
anchor, but on a system jazygit is not tested on, with what is known to work. `open` on macOS and
`rundll32 url.dll,FileProtocolHandler` on Windows, which gh uses, can take its place when asked for.

A browser on the terminal, such as `w3m` in `$BROWSER`, is not handed the terminal, as an editor is: it has no input,
so it does not take jazygit's.

### The log

Opening is an operation, as opening a branch is (`BrowseCmd`): the command log shows the browser's command line, with
the link, so the link can be copied from there, and a failure is an error that says what failed.

## Consequences

- From a thread in the diff, the discussion on GitHub is one key away, with the thread scrolled to.
- jazygit starts a process it does not wait for, which outlives it if it is the browser itself rather than a command
  that hands the link to one.
- The comments popup can open the highlighted comment the same way, through `#issuecomment-{id}`, as a decision of its
  own.
