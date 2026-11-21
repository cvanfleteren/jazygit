package net.vanfleteren.jazygit.features.changes.panes.commits;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.Tuple;
import io.vavr.Tuple4;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.Memo;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;

/**
 * Left-side panel listing the commit log of HEAD, or what a feature lists in its place, as what a search found. It knows
 * no feature: what they show in it, a line above the list and a listing, are its keys ({@link CommitsPane}), which the
 * features fill (ADR 172). While a line is open, it takes the keys that edit it, Enter and Escape; while a listing is
 * shown, Escape clears it.
 */
public class CommitsPanel implements LeftPane {

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = PaneId.COMMITS;

    public static final String ID = PANE.id();

    private final LoadableList<List<Commit>> list;
    private final Keymap keymap = Keymap.standard();
    private final Fills fills;
    private final FilterLine filterLine = new FilterLine();
    // What the last render showed: the line that was open, and what was listed in place of the log.
    private Option<PaneLine> line = Option.none();
    private Option<CommitsListing> listing = Option.none();
    // Whether the files pane was locked to the commits at the last render (ADR 149).
    private boolean locked;
    // What a paste does: while a line is open, it goes in it.
    private final Consumer<PasteEvent> paste;
    // The rows of the commits listed, worked out again only when what they are drawn from changes: the graph is not
    // laid out on every render.
    private final Memo<Tuple4<List<Commit>, Boolean, Option<Sha>, AheadView>, List<LoadableList.Item>> drawn =
            new Memo<>();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitsPanel(Consumer<Msg> dispatch) {
        this(dispatch, Theme.Highlights.DEFAULT, Fills.NONE);
    }

    /**
     * @param fills what the features give for the keys of the pane ({@link CommitsPane})
     */
    public CommitsPanel(Consumer<Msg> dispatch, Fills fills) {
        this(dispatch, Theme.Highlights.DEFAULT, fills);
    }

    /**
     * @param highlights how the highlighted commit is shown, also when the pane is not in use
     * @param fills      what the features give for the keys of the pane ({@link CommitsPane})
     */
    public CommitsPanel(Consumer<Msg> dispatch, Theme.Highlights highlights, Fills fills) {
        this.fills = fills;
        list = new LoadableList<>(Messages.get("panel.commits.title"), ID, CommitsPanel::reselect, highlights);
        paste = event ->
                line.forEach(open -> dispatch.accept(open.edited().apply(filterLine.paste(open.text(), event))));
        list.onKey(event -> line.map(open -> typed(open, event, dispatch))
                .orElse(() -> listing.isDefined() && event.isCancel()
                        ? PaneKeys.sending(listing.map(CommitsListing::cleared), dispatch)
                        // Escape lets go of the files locked to the commits, before it does anything else (ADR 149).
                        : locked && event.isCancel()
                                ? PaneKeys.sending(Option.of(new SelectionMsg.FilesUnlocked()), dispatch)
                                : PaneKeys.sending(keymap.handle(Scopes.COMMITS, event, selectedCommit()), dispatch)));
    }

    /**
     * What {@code event} does while {@code open} is: the keys that edit it do, Enter submits it, Escape cancels it,
     * and the other keys do nothing, but for those the list moves with, which it took before.
     */
    private Runnable typed(PaneLine open, KeyEvent event, Consumer<Msg> dispatch) {
        Option<Msg> msg = event.isCancel()
                ? Option.of(open.cancelled())
                : event.isConfirm()
                        ? Option.of(open.submitted())
                        : filterLine.edit(open.text(), event).map(open.edited());
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
     * The items of the commits listed in place of the log, without the rails of merges: with commits left out, they no
     * longer join up. A merge is still a {@code ◎}. Each row is worked out once it is drawn.
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
        AheadView ahead = new AheadView(AheadUpdate.head(model), AheadUpdate.headDistance(model));
        line = fills.of(CommitsPane.LINE, model);
        listing = fills.of(CommitsPane.LISTING, model);
        locked = PaneKeys.lockedTo(model, PANE);
        boolean listed = listing.isDefined();
        list.whenEmpty(listing.map(CommitsListing::emptyText));
        return list.renderItems(
                listing.map(CommitsListing::commits)
                        .getOrElse(() -> model.repo().commits()),
                commits -> drawn.get(
                        Tuple.of(commits, listed, copied, ahead),
                        key -> listed
                                ? foundItems(commits, copied, ahead)
                                : CommitRows.graphed(commits, copied, ahead)),
                focus.contains(PANE),
                inUse.contains(PANE),
                Pane.title(ahead.title(listing.map(CommitsListing::title).getOrElse(() -> headTitle(model)))),
                line.map(open -> filterLine.field(open.text())),
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
     * The id of the pane, by which its screen lists it.
     */
    @Override
    public PaneId pane() {
        return PANE;
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
