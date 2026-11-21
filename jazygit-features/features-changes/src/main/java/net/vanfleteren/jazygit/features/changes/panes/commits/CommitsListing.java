package net.vanfleteren.jazygit.features.changes.panes.commits;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * What a feature lists in the commits pane in place of the log of HEAD (ADR 172), as the commits a search found. They
 * are some of the commits only, so the pane draws them flat, without the rails of the graph, which would not join up.
 * The commit operations find them through the model, as a part knows its commits (ADR 158).
 *
 * @param title     the title of the pane while it lists them
 * @param commits   the commits, newest first
 * @param emptyText what the pane says when there are none
 * @param cleared   what to send when the user pressed Escape, to see the log again
 */
public record CommitsListing(String title, Loadable<List<Commit>> commits, String emptyText, Msg cleared) {}
