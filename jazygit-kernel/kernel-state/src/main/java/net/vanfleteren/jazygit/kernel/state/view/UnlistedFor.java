package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * Why the row highlighted in the pane {@code from} has no files to list, which the files pane says in their place;
 * {@code Esc} there goes back to {@code from} (ADR 141).
 */
public record UnlistedFor(Unlisted why, PaneId from) {}
