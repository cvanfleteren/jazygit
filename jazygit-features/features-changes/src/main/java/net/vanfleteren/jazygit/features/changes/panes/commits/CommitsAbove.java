package net.vanfleteren.jazygit.features.changes.panes.commits;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * What a feature lists above the log of HEAD in the commits pane (ADR 199): rows that are no commit of the log, as the
 * steps of an operation that stopped, still to come; and the commit of the log above which its commits are marked done.
 *
 * @param rows      the rows, top first
 * @param doneAbove the commit of the log whose commits above it are marked done, with a {@code ✓} in place of their
 *                  {@code ○}; none, or one that is not in the log, marks none
 * @param progress  what the title says after what the log is of, as {@code 2/3}, if anything
 */
public record CommitsAbove(List<Row> rows, Option<Sha> doneAbove, Option<String> progress) {

    /**
     * A row above the log. It is no commit of the log: the keys that act on one do nothing on it.
     *
     * @param row    how it is drawn
     * @param commit the commit the content panel shows while it is highlighted, if any
     * @param key    what tells it from the other rows, so that the highlight follows it as rows come and go
     */
    public record Row(LoadableList.Row row, Option<Sha> commit, String key) {}
}
