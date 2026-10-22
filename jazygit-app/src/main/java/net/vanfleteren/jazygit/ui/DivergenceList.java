package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * The divergence the branches pane lists in place of the local branches while one is open: the commits only on the
 * branch it is compared with, then the ones only on the local branch, each under the name of their branch. The
 * highlight skips the names. Enter moves the focus to the highlighted commit.
 */
class DivergenceList {

    private final LoadableList<Divergence> list;
    private final Keymap keymap = Keymap.standard();
    // The highlighted row at the last render, which tells which way the highlight moved.
    private int selected;
    // Which divergence was listed at the last render, by the load that reads it.
    private Option<Cmd.LoadDivergence> shown = Option.none();

    /**
     * @param id         the id of the pane the divergence is listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param openCommit called when Enter is pressed on a commit, to move focus to its content
     * @param highlights how the highlighted commit is shown, also when the pane is not in use
     */
    DivergenceList(String id, Consumer<Msg> dispatch, Runnable openCommit, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get("panel.divergence.title"),
                id,
                (previous, next, index) -> LoadableList.Reselect.following(DivergenceList::identity)
                        .apply(entries(previous), entries(next), index),
                highlights);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKey(event -> event.code() == KeyCode.ENTER
                ? selectedCommit().map(commit -> openCommit)
                : PaneKeys.sending(keymap.handle(Scopes.DIVERGENCE, event, selectedCommit()), dispatch));
    }

    /**
     * The rows of a divergence: the commits only on the branch it is compared with, then the ones only on the
     * local branch, each under a header for their branch, with a line when there are none or more than are listed.
     */
    static List<Entry> entries(Divergence divergence) {
        return side(false, divergence.behind(), divergence.behindCount())
                .appendAll(side(true, divergence.ahead(), divergence.aheadCount()));
    }

    private static List<Entry> side(boolean ahead, List<Commit> commits, int count) {
        List<CommitGraph.Node> nodes = CommitGraph.layout(commits);
        return List.<Entry>of(new Entry.Header(ahead))
                .appendAll(nodes.zipWith(
                        nodes.map(CommitGraph.Node::closesBranch).prepend(false),
                        (node, joins) -> new Entry.Shown(node, joins, ahead)))
                .appendAll(Option.<Entry>when(commits.isEmpty(), Entry.None::new))
                .appendAll(Option.<Entry>when(count > commits.size(), () -> new Entry.More(count - commits.size())));
    }

    /**
     * What a row of a divergence is, which the highlight follows: a commit by its sha.
     */
    private static Object identity(Entry entry) {
        return entry instanceof Entry.Shown(var node, var joins, var ahead)
                ? node.commit().sha()
                : entry;
    }

    /**
     * @param view the divergence the row is of, which names its branches
     */
    static LoadableList.Row row(DivergenceView view, Entry entry) {
        return switch (entry) {
            case Entry.Header(boolean ahead) ->
                LoadableList.Row.of(LoadableList.Seg.styled(
                        "── " + (ahead ? view.branch().value() : view.other().name()), Theme.Classes.BOLD));
            case Entry.Shown(CommitGraph.Node node, boolean joins, boolean ahead) ->
                new LoadableList.Row(List.of(
                                ahead
                                        ? LoadableList.Seg.styled("↑ ", Theme.Classes.AHEAD)
                                        : LoadableList.Seg.styled("↓ ", Theme.Classes.BEHIND))
                        .appendAll(CommitsPanel.row(node, joins).segs()));
            case Entry.None() ->
                LoadableList.Row.of(LoadableList.Seg.styled("  " + Messages.get("divergence.none"), Theme.Classes.DIM));
            case Entry.More(int more) ->
                LoadableList.Row.of(
                        LoadableList.Seg.styled("  " + Messages.get("divergence.more", more), Theme.Classes.DIM));
        };
    }

    /**
     * The pane listing {@code divergence}, if one is open. Called on every render, also when none is, so that a
     * divergence opened again starts at its first commit.
     */
    Option<Panel> render(Option<DivergenceView> divergence, boolean focused, boolean active) {
        follow(divergence);
        return divergence.map(view -> panel(view, focused, active));
    }

    /**
     * Remembers which divergence is shown; one just opened starts at its first commit.
     */
    private void follow(Option<DivergenceView> divergence) {
        Option<Cmd.LoadDivergence> now = divergence.map(DivergenceView::load);
        if (now.isDefined() && !now.equals(shown)) {
            list.select(0);
            selected = 0;
        }
        shown = now;
    }

    private Panel panel(DivergenceView view, boolean focused, boolean active) {
        Panel panel = list.render(
                view.commits(),
                commits -> entries(commits).map(entry -> row(view, entry)),
                focused,
                active,
                Pane.title(Messages.get(
                        "panel.divergence.titleFor",
                        view.branch().value(),
                        view.other().name())));
        skipToCommit(view);
        return panel;
    }

    /**
     * Moves the highlight off a row that is not a commit, to the nearest commit the way it moved, else the other
     * way; when there are no commits, it stays.
     */
    private void skipToCommit(DivergenceView view) {
        List<Boolean> commits = view.commits()
                .toOption()
                .map(divergence -> entries(divergence).map(Entry.Shown.class::isInstance))
                .getOrElse(List.empty());
        int at = list.selectedIndex();
        int way = at >= selected ? 1 : -1;
        Option<Integer> target = commits.asPartialFunction().lift().apply(at).contains(false)
                ? nearest(commits, at, way).orElse(() -> nearest(commits, at, -way))
                : Option.none();
        target.forEach(list::select);
        selected = target.getOrElse(at);
    }

    private static Option<Integer> nearest(List<Boolean> commits, int from, int way) {
        return Stream.iterate(from + way, i -> i + way)
                .takeWhile(i -> i >= 0 && i < commits.size())
                .find(commits::get);
    }

    /**
     * The index of the highlighted row, never negative.
     */
    int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The highlighted commit, once a divergence is shown.
     */
    Option<Commit> selectedCommit() {
        return list.selected(DivergenceList::entries)
                .filter(Entry.Shown.class::isInstance)
                .map(Entry.Shown.class::cast)
                .map(entry -> entry.node().commit());
    }

    /**
     * The message to send when the highlighted commit is not the one whose content {@code view} holds.
     */
    Option<Msg> selectionChange(DivergenceView view) {
        return PaneKeys.selectionChange(
                selectedCommit().map(Commit::sha), view.detail(), SelectionMsg.DivergenceCommitSelected::new);
    }

    /**
     * A row of a divergence; only its commits can be highlighted.
     */
    sealed interface Entry {

        /**
         * The header of the branch the commits below are only on.
         *
         * @param ahead whether it is the local branch, rather than the one it is compared with
         */
        record Header(boolean ahead) implements Entry {}

        /**
         * A commit only on one of the branches.
         *
         * @param joins whether the branch before this commit joins the main line at it
         * @param ahead whether it is on the local branch, rather than on the one it is compared with
         */
        record Shown(CommitGraph.Node node, boolean joins, boolean ahead) implements Entry {}

        /**
         * That the branch above has no commits the other has not.
         */
        record None() implements Entry {}

        /**
         * That the branch above has {@code more} commits than are listed.
         */
        record More(int more) implements Entry {}
    }
}
