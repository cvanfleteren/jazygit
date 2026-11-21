package net.vanfleteren.jazygit.features.changes.commit.search.content;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearch;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearchMsg;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearchView;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsListing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;

/**
 * What the search of the log of HEAD shows in the commits pane, through its keys (ADR 172): the line it is typed in,
 * while it is open, and the commits the search that ran found, in place of the log, until it is cleared.
 */
public final class CommitSearchShown {

    private CommitSearchShown() {}

    /**
     * The line the search is typed in, while it is open.
     */
    public static Option<PaneLine> line(Model model) {
        return model.part(CommitSearchView.PART)
                .line()
                .map(text -> new PaneLine(
                        text,
                        CommitSearchMsg.Edited::new,
                        new CommitSearchMsg.Submitted(),
                        new CommitSearchMsg.Cancelled()));
    }

    /**
     * What the search that ran found, until it is cleared.
     */
    public static Option<CommitsListing> listing(Model model) {
        return model.part(CommitSearchView.PART)
                .search()
                .map(search -> new CommitsListing(
                        title(search),
                        search.results(),
                        Messages.get("changes.commit.search.noMatches"),
                        new CommitSearchMsg.Cleared()));
    }

    /**
     * The title of the pane while it lists what {@code search} found, which says when it found more than it lists.
     */
    static String title(CommitSearch search) {
        return search.results().toOption().exists(found -> found.size() >= History.MAX_COMMITS)
                ? Messages.get("changes.commit.search.titleCapped", search.text(), String.valueOf(History.MAX_COMMITS))
                : Messages.get("changes.commit.search.title", search.text());
    }
}
