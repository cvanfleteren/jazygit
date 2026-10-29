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

### The cost of ambiguous width

`●`, `≡` and `◆` are of ambiguous width (ADR 45): one column in Western terminals, two in those set up to draw
ambiguous characters wide, while TamboUI counts them as one. In the branch lists that moves the rest of line 2 a
column to the right. In the checks dialog and the details of a pull request it costs more: they line up how each
check is doing on the right from the width TamboUI counts, so in such a terminal a running check, `● verify …
running 30s · ? left`, takes one column too many and pushes on the border. The emoji had no such cost, as every
terminal draws them two columns wide.

The three are kept for their shape, `●` above all, which reads as running better than the others. Should the wide
case matter, `◔`, `≣` and `⬥`, of neutral width, take their place, as ADR 45 has it.

The emoji were easier to make out where there is room, but one look per state reads better than two, and the
reasons of ADR 45 for the narrow symbols, their shape apart from their colour and their width, hold in the dialogs
too.
