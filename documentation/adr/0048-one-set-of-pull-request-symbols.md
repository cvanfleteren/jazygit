# 48. One set of pull request symbols

Date: 2026-10-29

## Context

ADR 45 gave the branch lists symbols one column wide for the checks, the review and the merge of a pull request,
and left the emoji to the dialogs, the comments and the pull requests screen. A pull request now shows `✗` and `+`
in the branches pane, and `❌` and `👍` once its details are opened: the same state in two looks, and the legend of
the help shows only the one of the branch lists.

## Decision

The symbols one column wide of ADR 45 show wherever the state of a pull request shows: the branch lists, the pull
requests screen, the details of a pull request, the checks dialog and the comments. The emoji are gone.

- Where a pull request sits in its stack shows as `2/4`, without `📚`, also in the pull requests screen.
- A review that only commented, which the branch lists do not show, takes `~` instead of `💬`, in the class `dim`.
  A dismissed review keeps `⊘`.
- Each state keeps its style class, so the themes colour them as before.
- The comments dialog keeps two columns for the symbol and the space after it, instead of three.

The emoji were easier to make out where there is room, but one look per state reads better than two, and the
reasons of ADR 45 for the narrow symbols, their shape apart from their colour and their width, hold in the dialogs
too.
