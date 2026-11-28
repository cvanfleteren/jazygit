package net.vanfleteren.jazygit.features.changes.panes.commits;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple6;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.ShownTabs;
import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.tabs.TabList;
import net.vanfleteren.jazygit.kernel.ui.tabs.TabTitles;
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
 * <p>
 * The log is its first tab; the features add others ({@link CommitsPane#TABS}, ADR 185). While there are, the title
 * names them all, a click on a name shows that tab, and {@code [} and {@code ]} go through them. The line and the
 * listing belong to the log.
 */
public class CommitsPanel implements LeftPane {

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = PaneId.COMMITS;

    public static final String ID = PANE.id();

    private final LoadableList<Listed> list;
    private final Keymap keymap = Keymap.standard();
    private final Fills fills;
    private final Consumer<Msg> dispatch;
    private final Theme.Highlights highlights;
    private final FilterLine filterLine = new FilterLine();
    // What the last render showed: the line that was open, and what was listed in place of the log.
    private Option<PaneLine> line = Option.none();
    private Option<CommitsListing> listing = Option.none();
    // What a feature listed above the log at the last render, as the steps of an operation that stopped (ADR 199).
    private Option<CommitsAbove> above = Option.none();
    // The list of each tab of a feature shown so far, by its id: it keeps its highlighted row while another is shown.
    private Map<TabId, TabList<?>> tabLists = HashMap.empty();
    // What the last render showed: the tabs, while there was more than one, and the list of a feature's tab, while one
    // was shown rather than the log.
    private Option<ShownTabs> tabs = Option.none();
    private Option<TabList<?>> tabShown = Option.none();
    // Whether the files pane was locked to the commits at the last render (ADR 149).
    private boolean locked;
    // What a paste does: while a line is open, it goes in it.
    private final Consumer<PasteEvent> paste;
    // The rows of the commits listed, worked out again only when what they are drawn from changes: the graph is not
    // laid out on every render.
    private final Memo<Tuple6<Listed, Boolean, Option<Sha>, AheadView, Option<Sha>, Set<Sha>>, List<LoadableList.Item>>
            drawn = new Memo<>();
    // What is listed, the same instance while it stays equal: the list moves its highlight only when it changes.
    private final Memo<Tuple2<Loadable<List<Commit>>, List<CommitsAbove.Row>>, Loadable<Listed>> listed = new Memo<>();

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
        this.dispatch = dispatch;
        this.highlights = highlights;
        list = new LoadableList<>(Messages.get("panel.commits.title"), ID, CommitsPanel::reselect, highlights);
        paste = event ->
                line.forEach(open -> dispatch.accept(open.edited().apply(filterLine.paste(open.text(), event))));
        list.onKey(event -> line.map(open -> typed(open, event, dispatch))
                .orElse(() -> listing.isDefined() && event.isCancel()
                        ? PaneKeys.sending(listing.map(CommitsListing::cleared), dispatch)
                        // Escape lets go of the files locked to the commits, before it does anything else (ADR 149).
                        : locked && event.isCancel()
                                ? PaneKeys.sending(Option.of(new SelectionMsg.FilesUnlocked()), dispatch)
                                : PaneKeys.sending(
                                        keymap.handle(Scopes.COMMITS, event, selectedCommit(), tabsHosted()),
                                        dispatch)));
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
     * What the keys that switch tabs act on: the tabs shown, while there is more than one.
     */
    private Keymap.Hosted<ShownTabs> tabsHosted() {
        return new Keymap.Hosted<>(Scopes.PANE_TABS, tabs);
    }

    /**
     * What {@code event} does in the list of a feature's tab: Escape lets go of the files locked to the commits first,
     * as in the log (ADR 149); otherwise the keys of the tab, and those that switch tabs.
     */
    private Option<Runnable> tabKeyed(TabList<?> tabList, KeyEvent event) {
        return locked && event.isCancel()
                ? PaneKeys.sending(Option.of(new SelectionMsg.FilesUnlocked()), dispatch)
                : PaneKeys.sending(tabList.keyed(keymap, event, tabsHosted()), dispatch);
    }

    /**
     * The selection follows its row when rows come on top, except on the newest one: it stays there, so a new commit,
     * or the step a rebase goes on with, gets highlighted.
     */
    private static int reselect(Listed previous, Listed next, int selected) {
        return selected == 0
                ? 0
                : LoadableList.Reselect.<Entry, String>following(Entry::key)
                        .apply(previous.entries(), next.entries(), selected);
    }

    /**
     * {@code items}, those of {@code commits}, with each commit above {@code doneAbove} marked done, a {@code ✓} in
     * place of its {@code ○} (ADR 199); none when {@code doneAbove} is not among them.
     */
    static List<LoadableList.Item> done(List<LoadableList.Item> items, List<Commit> commits, Option<Sha> doneAbove) {
        int done = doneAbove
                .map(base -> commits.indexWhere(commit -> commit.sha().equals(base)))
                .filter(index -> index > 0)
                .getOrElse(0);
        return items.zipWithIndex(
                (item, at) -> at < done ? LoadableList.Item.later(item.height(), () -> checked(item.row())) : item);
    }

    /**
     * {@code row} with the first {@code ○} or {@code ◎} of its graph a {@code ✓}.
     */
    static LoadableList.Row checked(LoadableList.Row row) {
        int at = row.segs()
                .indexWhere(seg -> seg.text().contains("○") || seg.text().contains("◎"));
        return at < 0
                ? row
                : new LoadableList.Row(
                        row.segs()
                                .update(
                                        at,
                                        seg -> new LoadableList.Seg(
                                                seg.text().replaceFirst("[○◎]", "✓"), seg.cssClass(), seg.shrinks())),
                        row.detail(),
                        row.note());
    }

    /**
     * The items of the commits listed in place of the log, without the rails of merges: with commits left out, they no
     * longer join up. A merge is still a {@code ◎}. Each row is worked out once it is drawn.
     */
    static List<LoadableList.Item> foundItems(
            List<Commit> commits, Option<Sha> copied, AheadView ahead, Set<Sha> unpushed) {
        return commits.map(commit -> LoadableList.Item.later(
                1, () -> CommitRows.marked(foundRow(commit, copied, unpushed), commit.sha(), ahead)));
    }

    private static LoadableList.Row foundRow(Commit commit, Option<Sha> copied, Set<Sha> unpushed) {
        return CommitRows.row(
                commit, commit.isMerge() ? "◎ " : "○ ", copied.contains(commit.sha()), unpushed.contains(commit.sha()));
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
        List<PaneTab<?>> added = fills.all(CommitsPane.TABS, model);
        List<TabId> ids = List.of(CommitsPane.LOG).appendAll(added.map(PaneTab::id));
        TabId shown = model.repoView().tab(PANE).filter(ids::contains).getOrElse(CommitsPane.LOG);
        tabs = Option.when(added.nonEmpty(), () -> new ShownTabs(PANE, ids, shown));
        Option<PaneTab<?>> addedShown = added.find(tab -> tab.id().equals(shown));
        // The line and the listing belong to the log: a feature's tab shows neither.
        line = addedShown.isEmpty() ? fills.of(CommitsPane.LINE, model) : Option.none();
        listing = addedShown.isEmpty() ? fills.of(CommitsPane.LISTING, model) : Option.none();
        // What a feature lists above the log goes with the log: not above what is listed in its place.
        above = addedShown.isEmpty() && listing.isEmpty() ? fills.of(CommitsPane.ABOVE, model) : Option.none();
        locked = PaneKeys.lockedTo(model, PANE);
        AheadView ahead = new AheadView(AheadUpdate.head(model), AheadUpdate.headDistance(model));
        // How far what is listed above the log got, after what the log is of (ADR 199).
        String progress = above.flatMap(CommitsAbove::progress)
                .map(done -> Messages.get("changes.panes.commits.progress", done))
                .getOrElse("");
        String logTitle = ahead.title(listing.map(CommitsListing::title).getOrElse(() -> headTitle(model))) + progress;
        // With tabs, the log is named short, so that the names of the others stay in view where the pane is narrow;
        // what it is the log of goes in a line at its bottom instead.
        Option<String> logOf = Option.when(
                        tabs.isDefined(),
                        () -> ahead.title(listing.map(CommitsListing::title).getOrElse(() -> headName(model)))
                                + progress)
                .filter(text -> !text.isBlank());
        List<String> names = List.of(tabs.isDefined() ? Messages.get("panel.commits.title") : logTitle)
                .appendAll(added.map(PaneTab::title));
        Line title = tabs.isDefined() ? TabTitles.line(names, ids.indexOf(shown)) : Pane.title(logTitle);
        boolean focused = focus.contains(PANE);
        boolean active = inUse.contains(PANE);
        tabShown = addedShown.map(tab -> (TabList<?>) listOf(tab));
        Panel panel = addedShown
                .map(tab -> tabPanel(tab, focused, active, title))
                .getOrElse(() -> logPanel(model, ahead, focused, active, title, logOf));
        return tabs.isEmpty()
                ? panel
                : panel.onMouseEvent(event -> TabTitles.clicked(
                        panel.renderedArea(),
                        event,
                        names,
                        index -> dispatch.accept(new ViewMsg.TabChosen(PANE, ids.get(index)))));
    }

    /**
     * The pane showing the log, or what a feature lists in its place, with what it is the log of in a dim line at its
     * bottom, if {@code logOf} says.
     */
    private Panel logPanel(
            Model model, AheadView ahead, boolean focused, boolean active, Line title, Option<String> logOf) {
        Option<Sha> copied = model.repoView().commits().cherryPick().map(Commit::sha);
        boolean found = listing.isDefined();
        Option<Sha> doneAbove = above.flatMap(CommitsAbove::doneAbove);
        Set<Sha> unpushed = fills.all(CommitsPane.UNPUSHED, model).toSet();
        list.whenEmpty(listing.map(CommitsListing::emptyText));
        Loadable<List<Commit>> commits = listing.map(CommitsListing::commits)
                .getOrElse(() -> model.repo().commits());
        return list.renderItems(
                listed.get(
                        Tuple.of(commits, above.map(CommitsAbove::rows).getOrElse(List.empty())),
                        key -> key._1().map(loaded -> new Listed(key._2(), loaded))),
                shown -> drawn.get(
                        Tuple.of(shown, found, copied, ahead, doneAbove, unpushed),
                        key -> shown.above()
                                .map(row -> LoadableList.Item.of(row.row()))
                                .appendAll(
                                        found
                                                ? foundItems(shown.commits(), copied, ahead, unpushed)
                                                : done(
                                                        CommitRows.graphed(shown.commits(), copied, ahead, unpushed),
                                                        shown.commits(),
                                                        doneAbove))),
                focused,
                active,
                title,
                line.map(open -> filterLine.field(open.text())),
                // One row, cut off with an ellipsis where the pane is narrow.
                logOf.map(text -> Toolkit.text(text)
                        .addClass(Theme.Classes.DIM)
                        .ellipsis()
                        .length(1)),
                paste);
    }

    /**
     * The pane showing {@code tab}, a feature's.
     */
    private <T> Panel tabPanel(PaneTab<T> tab, boolean focused, boolean active, Line title) {
        return listOf(tab).render(tab, focused, active, title);
    }

    /**
     * The list of {@code tab}, made the first time it is shown.
     */
    @SuppressWarnings("unchecked")
    private <T> TabList<T> listOf(PaneTab<T> tab) {
        // The id of a tab is its feature's, which always lists the same type in it.
        return (TabList<T>) tabLists.get(tab.id()).getOrElse(() -> {
            TabList<T> made = new TabList<>(ID, highlights);
            made.onKey(event -> tabKeyed(made, event));
            tabLists = tabLists.put(tab.id(), made);
            return made;
        });
    }

    /**
     * The title of the commits listed, naming what is checked out, whose log they are: a branch, or a detached HEAD,
     * or the branch a rebase works on while it detaches HEAD (ADR 197); plain until that is read.
     */
    static String headTitle(Model model) {
        return model.repo()
                .status()
                .toOption()
                .map(status -> status.rebasedBranch()
                        .map(branch -> Messages.get("changes.panes.commits.titleRebasing", branch.value()))
                        .getOrElse(() -> switch (status.head()) {
                            case Head.OnBranch(BranchName branch, var tip) ->
                                Messages.get("changes.panes.commits.titleOf", branch.value());
                            case Head.Detached(Sha at) ->
                                Messages.get("changes.panes.commits.titleDetached", at.shortSha());
                        }))
                .getOrElse(() -> Messages.get("panel.commits.title"));
    }

    /**
     * What is checked out, whose log the commits are, as the line at the bottom of the log names it: a branch, or a
     * detached HEAD, or the branch a rebase works on while it detaches HEAD (ADR 197); nothing until that is read.
     */
    static String headName(Model model) {
        return model.repo()
                .status()
                .toOption()
                .map(status -> status.rebasedBranch()
                        .map(branch -> Messages.get("changes.panes.commits.rebasing", branch.value()))
                        .getOrElse(() -> switch (status.head()) {
                            case Head.OnBranch(BranchName branch, var tip) -> branch.value();
                            case Head.Detached(Sha at) ->
                                Messages.get("changes.panes.commits.detachedAt", at.shortSha());
                        }))
                .getOrElse("");
    }

    /**
     * The rows the last render showed, of the log or of the feature's tab shown in its place.
     */
    List<LoadableList.Row> shownRows() {
        return tabShown.map(TabList::shownRows).getOrElse(list::shownRows);
    }

    /**
     * The index of the currently highlighted commit, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The highlighted commit, once the commits are shown; none while a feature's tab is shown in place of the log.
     */
    public Option<Commit> selectedCommit() {
        return selectedEntry()
                .flatMap(entry -> entry instanceof Entry.Logged logged ? Option.of(logged.logged()) : Option.none());
    }

    /**
     * The highlighted row of the log, a commit of it or a row above it; none while a feature's tab is shown in its
     * place.
     */
    private Option<Entry> selectedEntry() {
        return tabShown.isDefined() ? Option.none() : list.selected(Listed::entries);
    }

    /**
     * The id of the pane, by which its screen lists it.
     */
    @Override
    public PaneId pane() {
        return PANE;
    }

    /**
     * The message to send when the highlighted row is not the one whose preview {@code model} holds: a commit of the
     * log, or what a feature's tab says for its row.
     */
    @Override
    public Option<Msg> selectionChange(Model model) {
        return tabShown.isDefined()
                ? tabShown.flatMap(TabList::selectionChange)
                // A row above the log shows the commit it is of, if any (ADR 199).
                : PaneKeys.selectionChange(
                        selectedEntry().flatMap(Entry::commit),
                        model.repoView().preview().commitDetail(),
                        SelectionMsg.CommitSelected::new);
    }

    /**
     * What the log lists: the rows a feature lists above it, then its commits.
     */
    record Listed(List<CommitsAbove.Row> above, List<Commit> commits) {

        List<Entry> entries() {
            return above.<Entry>map(Entry.Above::new).appendAll(commits.map(Entry.Logged::new));
        }
    }

    /**
     * A row of the log: one a feature lists above it, or a commit of it.
     */
    sealed interface Entry {

        /**
         * What tells it from the other rows.
         */
        String key();

        /**
         * The commit the content panel shows while it is highlighted, if any.
         */
        Option<Sha> commit();

        record Above(CommitsAbove.Row row) implements Entry {

            @Override
            public String key() {
                return "above " + row.key();
            }

            @Override
            public Option<Sha> commit() {
                return row.commit();
            }
        }

        record Logged(Commit logged) implements Entry {

            @Override
            public String key() {
                return logged.sha().value();
            }

            @Override
            public Option<Sha> commit() {
                return Option.of(logged.sha());
            }
        }
    }
}
