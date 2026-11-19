package net.vanfleteren.jazygit.changes.panes.commits;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.Tuple;
import io.vavr.Tuple4;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.changes.commit.search.CommitSearch;
import net.vanfleteren.jazygit.changes.commit.search.CommitSearchMsg;
import net.vanfleteren.jazygit.changes.commit.search.CommitSearchView;
import net.vanfleteren.jazygit.git.history.History;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.ui.LeftPane;
import net.vanfleteren.jazygit.ui.commits.AheadView;
import net.vanfleteren.jazygit.ui.commits.CommitRows;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.ui.widgets.Memo;
import net.vanfleteren.jazygit.ui.widgets.Pane;
import net.vanfleteren.jazygit.ui.widgets.PaneKeys;

/**
 * Left-side panel listing the commit log, or what the search of it found. {@code /} opens the line to type the
 * search in above the list, which then takes the keys that edit it; Enter runs it and Escape closes it. While the
 * panel lists the commits found, Escape clears the search.
 */
public class CommitsPanel implements LeftPane {

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = PaneId.COMMITS;

    public static final String ID = PANE.id();

    private final LoadableList<List<Commit>> list;
    private final Keymap keymap = Keymap.standard();
    private final FilterLine searchLine = new FilterLine();
    // What the last render showed: the text of the open search line, and whether a search was listed.
    private Option<String> typing = Option.none();
    private boolean searching;
    // Whether the files pane was locked to the commits at the last render (ADR 149).
    private boolean locked;
    // What a paste does: while the search line is open, it goes in it.
    private final Consumer<PasteEvent> paste;
    // The rows of the commits listed, worked out again only when what they are drawn from changes: the graph is not
    // laid out on every render.
    private final Memo<Tuple4<List<Commit>, Boolean, Option<Sha>, AheadView>, List<LoadableList.Item>> drawn =
            new Memo<>();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitsPanel(Consumer<Msg> dispatch) {
        this(dispatch, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted commit is shown, also when the pane is not in use
     */
    public CommitsPanel(Consumer<Msg> dispatch, Theme.Highlights highlights) {
        list = new LoadableList<>(Messages.get("panel.commits.title"), ID, CommitsPanel::reselect, highlights);
        paste = event ->
                typing.forEach(text -> dispatch.accept(new CommitSearchMsg.Edited(searchLine.paste(text, event))));
        list.onKey(event -> typing.map(text -> typed(text, event, dispatch))
                .orElse(() -> searching && event.isCancel()
                        ? PaneKeys.sending(Option.of(new CommitSearchMsg.Cleared()), dispatch)
                        // Escape lets go of the files locked to the commits, before it does anything else (ADR 149).
                        : locked && event.isCancel()
                                ? PaneKeys.sending(Option.of(new SelectionMsg.FilesUnlocked()), dispatch)
                                : PaneKeys.sending(keymap.handle(Scopes.COMMITS, event, selectedCommit()), dispatch)));
    }

    /**
     * What {@code event} does while the search line is open with {@code text}: the keys that edit it do, Enter runs
     * it, Escape closes it, and the other keys do nothing, but for those the list moves with, which it took before.
     */
    private Runnable typed(String text, KeyEvent event, Consumer<Msg> dispatch) {
        Option<Msg> msg = event.isCancel()
                ? Option.of(new CommitSearchMsg.Cancelled())
                : event.isConfirm()
                        ? Option.of(new CommitSearchMsg.Submitted())
                        : searchLine.edit(text, event).map(CommitSearchMsg.Edited::new);
        return () -> msg.forEach(dispatch);
    }

    /**
     * The selection follows its commit when commits come on top, except on the newest one: it stays there, so a
     * new commit gets highlighted.
     */
    private static int reselect(List<Commit> previous, List<Commit> next, int selected) {
        return selected == 0 ? 0 : LoadableList.Reselect.following(Commit::sha).apply(previous, next, selected);
    }

    /**
     * The items of the commits a search found, without the rails of merges: with commits left out, they no longer
     * join up. A merge is still a {@code ◎}. Each row is worked out once it is drawn.
     */
    static List<LoadableList.Item> foundItems(List<Commit> commits, Option<Sha> copied, AheadView ahead) {
        return commits.map(commit ->
                LoadableList.Item.later(1, () -> CommitRows.marked(foundRow(commit, copied), commit.sha(), ahead)));
    }

    private static LoadableList.Row foundRow(Commit commit, Option<Sha> copied) {
        return CommitRows.row(commit, commit.isMerge() ? "◎ " : "○ ", copied.contains(commit.sha()));
    }

    /**
     * The pane while {@code pane} has the focus, and is in use.
     */
    public Panel render(Model model, PaneId pane) {
        return render(model, Option.of(pane), Option.of(pane));
    }

    /**
     * @param focus what has the focus, if anything
     * @param inUse the left pane in use, whose highlighted row shows
     */
    @Override
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        Option<Sha> copied = model.repoView().commits().cherryPick().map(Commit::sha);
        CommitSearchView view = model.part(CommitSearchView.PART);
        Option<CommitSearch> search = view.search();
        AheadView ahead = new AheadView(AheadUpdate.head(model), AheadUpdate.headDistance(model));
        typing = view.line();
        searching = search.isDefined();
        locked = PaneKeys.lockedTo(model, PANE);
        list.whenEmpty(search.map(found -> Messages.get("changes.panes.commits.noMatches")));
        Loadable<List<Commit>> source =
                search.map(CommitSearch::results).getOrElse(() -> model.repo().commits());
        return list.renderItems(
                source,
                commits -> drawn.get(
                        Tuple.of(commits, searching, copied, ahead),
                        key -> searching
                                ? foundItems(commits, copied, ahead)
                                : CommitRows.graphed(commits, copied, ahead)),
                focus.contains(PANE),
                inUse.contains(PANE),
                Pane.title(ahead.title(search.map(CommitsPanel::title).getOrElse(() -> headTitle(model)))),
                typing.map(searchLine::field),
                paste);
    }

    /**
     * The title of the commits listed, naming what is checked out, whose log they are: a branch, or a detached HEAD;
     * plain until that is read.
     */
    static String headTitle(Model model) {
        return model.repo()
                .status()
                .toOption()
                .map(status -> switch (status.head()) {
                    case Head.OnBranch(BranchName branch, var tip) ->
                        Messages.get("changes.panes.commits.titleOf", branch.value());
                    case Head.Detached(Sha at) -> Messages.get("changes.panes.commits.titleDetached", at.shortSha());
                })
                .getOrElse(() -> Messages.get("panel.commits.title"));
    }

    /**
     * The rows the last render showed.
     */
    List<LoadableList.Row> shownRows() {
        return list.shownRows();
    }

    /**
     * The title of the panel while it lists what {@code search} found, which says when it found more than it
     * lists.
     */
    static String title(CommitSearch search) {
        return search.results().toOption().exists(found -> found.size() >= History.MAX_COMMITS)
                ? Messages.get(
                        "changes.panes.commits.titleSearchCapped", search.text(), String.valueOf(History.MAX_COMMITS))
                : Messages.get("changes.panes.commits.titleSearch", search.text());
    }

    /**
     * The index of the currently highlighted commit, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The highlighted commit, once the commits are shown.
     */
    public Option<Commit> selectedCommit() {
        return list.selected(commits -> commits);
    }

    /**
     * The message to send when the highlighted commit is not the one whose changes {@code model} holds.
     */
    @Override
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(
                selectedCommit().map(Commit::sha),
                model.repoView().preview().commitDetail(),
                SelectionMsg.CommitSelected::new);
    }
}
