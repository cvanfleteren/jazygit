# 45. Two lines per branch

Date: 2026-10-29

## Context

A local branch is one row in the branches pane: its age, its name, `(worktree)`, `↑2 ↓5` or `gone`, then its pull
request `#1234` with `📚2/4`, the symbol of its checks and when they are expected to be done, the symbol of its
review, and the spinner of a busy branch. On a busy branch that is more than the left column, 30% of the terminal by
default, has room for, and the pieces start wherever the name before them ends, so the list reads as noise.

The symbols are emoji. Every one of them is wide in Unicode (East Asian Width `W`: ✅ ❌ ⏳ 💤 👍 👎 🔀 🚦 💥 🟣 📚),
so each takes two columns.

Other ways were considered:

- One line, with the name on the left cut off with `…` and the rest in columns aligned on the right, leaving out
  what matters least when the column is narrow, and everything in a header above the log in the content panel. It
  keeps the most branches in view, but leaves things out of the list.
- Two lines only for a branch whose row does not fit, or only for the highlighted branch. The rows below then move
  when the left column is resized, or when the highlight moves.

## Decision

### Two lines, always

- Every branch, local and remote, takes two lines, also one with little to say: the rows keep one rhythm, and the
  highlight moves by the same amount. Both lines are one item of the list, so the highlight covers both and a
  click on either highlights the branch.
- Line 1 is the name, with the whole width, cut off with an ellipsis at its end when it is still wider; then the
  spinner of a busy branch, which stays in view. The current branch keeps its style of its own.
- Line 2 is indented two columns and dimmed, but for the coloured symbols. In this order, with two spaces between
  the groups:
  1. the age;
  2. `↑2 ↓5`, or `gone`;
  3. the checks and when they are expected to be done or how long they waited (ADR 32, 35), or how the merge
     of the pull request goes (ADR 24); then the pull request `#1234`; then its review; then where it sits in its
     stack, as `2/4`. A branch without a pull request shows the checks on its tip, in the same place: the checks
     come before the number, so they line up between branches with and without a pull request;
  4. `wt`, for a branch checked out in another worktree.

  ```
   feat/payment-retry-backoff  Pushing ⠋
     3d  ↑2 ↓5  ● ~4m #1234  −  2/4  wt
   main
     9d  ↓3  ✓
  ```
- A remote branch has no ahead or behind and no worktree: its line 2 is its age and how it is doing on GitHub.
  One that a local branch follows stays dimmed, on both lines.
- Half as many branches fit in the pane. There is no setting for one line per branch; one can come when that
  turns out to hurt.

### One column per symbol

The branch lists use symbols one column wide, which tell the states apart by their shape as well as their colour.
Each state keeps the style class it had, so every theme colours them as before; the colour-blind theme
(`dark-colorblind`) already draws good in blue and bad in vermillion, and the shapes stay apart without colour,
also in `mono` and `none`.

| state | emoji | branch lists | class |
|---|---|---|---|
| checks passed | ✅ | `✓` | `checks-passed` |
| checks failed | ❌ | `✗` | `checks-failed` |
| checks running | ⏳ | `●` | `checks-running` |
| checks waiting to start | 💤 | `◌` | `dim` |
| approved | 👍 | `+` | `review-approved` |
| changes requested | 👎 | `−` | `review-changes` |
| to review | `?` | `?` | `dim` |
| merging | 🔀 | `»` | `dim` |
| in the merge queue | 🚦 | `≡` | `dim` |
| merge failed | 💥 | `!` | `checks-failed` |
| merged | 🟣 | `◆` | `pull-request` |
| place in the stack | `📚2/4` | `2/4` | `dim` |

`●`, `≡` and `◆`, like the `↑` and `↓` the list showed before, are of ambiguous width in Unicode (East Asian
Width `A`): one column in Western terminals, two in those set up to draw ambiguous characters wide, where the rest of
line 2 then moves a column to the right per symbol. TamboUI counts them as one. The others, `✓ ✗ ◌ + − ? » !`, are
one column everywhere (`N` or `Na`). The three are kept for their shape; should the wide case matter, symbols of
neutral width can take their place, such as `◔`, `≣` and `⬥`.

The dialogs, the comments and the pull requests screen keep the emoji: they have the room, and the emoji are
easier to make out.

Later: ADR 48 shows the symbols one column wide in the dialogs, the comments and the pull requests screen too; the
emoji are gone.
