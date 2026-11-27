package net.vanfleteren.jazygit.features.branches.panes.branches;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.BranchGroups;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.tabs.TabList;
import net.vanfleteren.jazygit.kernel.ui.tabs.TabTitles;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * Left-side pane with tabs (ADR 185), one shown at a time: its own three lists, the
 * {@link LocalBranchesList local branches}, the {@link RemoteBranchesList remote-tracking ones} and the
 * {@link WorktreesList worktrees}, and after them those the features add ({@link BranchesPane#TABS}). {@code [} and
 * {@code ]} go through them, in the same pane, so the focus moves through it as one; clicking the name of a tab in the
 * title shows that tab. Which it shows is kept in the model ({@link RepoView#tabs}), as for any pane with tabs.
 * <p>
 * While a divergence is open, it is listed in place of the local branches, by a {@link DivergenceList}; Escape goes
 * back to the branches. So it does from the commits of a branch, which Enter on a branch lists in place of the
 * branches, by a {@link BranchCommitsList}. Showing another tab closes either.
 */
public class BranchesPanel implements LeftPane {

    public static final String ID = Scopes.BRANCHES.id();

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = Scopes.BRANCHES.pane();

    private static final List<BranchesTab> OWN = List.of(BranchesTab.values());

    private final LocalBranchesList localList;
    private final RemoteBranchesList remoteList;
    private final WorktreesList worktreeList;
    private final DivergenceList divergenceList;
    private final BranchCommitsList branchCommitsList;
    private final Consumer<Msg> dispatch;
    private final Theme.Highlights highlights;
    private final Fills fills;
    private final Keymap keymap = Keymap.standard();
    // The list of each tab of a feature shown so far, by its id: it keeps its highlighted row while another is shown.
    private Map<TabId, TabList<?>> tabLists = HashMap.empty();
    // What the last render showed: the tabs, and the list of a feature's tab, while one was shown.
    private Option<ShownTabs> tabs = Option.none();
    private Option<TabList<?>> tabShown = Option.none();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public BranchesPanel(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemUTC());
    }

    /**
     * @param clock animates the spinners of the busy branches; how long ago they were committed to is told by the
     *              time of the model
     */
    public BranchesPanel(Consumer<Msg> dispatch, Clock clock) {
        this(dispatch, clock, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     */
    public BranchesPanel(Consumer<Msg> dispatch, Clock clock, Theme.Highlights highlights) {
        this(dispatch, clock, highlights, Fills.NONE);
    }

    /**
     * @param fills what the features give for the keys of the pane, {@link BranchBadges#KEY}, {@link BranchGroups#KEY}
     *              and {@link BranchesPane#TABS} (ADR 172, 185): the app's, or {@link Fills#NONE} without it
     */
    public BranchesPanel(Consumer<Msg> dispatch, Clock clock, Theme.Highlights highlights, Fills fills) {
        this(dispatch, clock, highlights, BranchBadges.filled(fills), BranchGroups.filled(fills), fills);
    }

    /**
     * @param badges what the features show of a branch, as a test gives them (ADR 167)
     */
    public BranchesPanel(Consumer<Msg> dispatch, Clock clock, Theme.Highlights highlights, BranchBadges badges) {
        this(dispatch, clock, highlights, badges, BranchGroups.NONE);
    }

    /**
     * @param groups the groups the features make of the local branches, as a test gives them (ADR 167)
     */
    public BranchesPanel(
            Consumer<Msg> dispatch,
            Clock clock,
            Theme.Highlights highlights,
            BranchBadges badges,
            BranchGroups groups) {
        this(dispatch, clock, highlights, badges, groups, Fills.NONE);
    }

    private BranchesPanel(
            Consumer<Msg> dispatch,
            Clock clock,
            Theme.Highlights highlights,
            BranchBadges badges,
            BranchGroups groups,
            Fills fills) {
        this.dispatch = dispatch;
        this.highlights = highlights;
        this.fills = fills;
        Supplier<Keymap.Hosted<ShownTabs>> hosted = this::tabsHosted;
        // Only the list shown is rendered, that of the tab, or the divergence or the commits of a branch in its place,
        // so only it gets keys: TamboUI routes them to the elements of the last frame.
        localList = new LocalBranchesList(ID, dispatch, clock, highlights, badges, groups, hosted);
        remoteList = new RemoteBranchesList(ID, dispatch, clock, highlights, badges, hosted);
        worktreeList = new WorktreesList(ID, dispatch, highlights, hosted);
        divergenceList = new DivergenceList(ID, dispatch, highlights, hosted);
        branchCommitsList = new BranchCommitsList(ID, dispatch, highlights, hosted);
    }

    /**
     * What the keys that switch tabs act on: the tabs the last render showed.
     */
    private Keymap.Hosted<ShownTabs> tabsHosted() {
        return new Keymap.Hosted<>(Scopes.PANE_TABS, tabs);
    }

    /**
     * The name of {@code tab}, one of the pane's own, in the title.
     */
    static String tabName(BranchesTab tab) {
        return Messages.get(
                switch (tab) {
                    case LOCAL -> "panel.branches.title";
                    case REMOTE -> "panel.remoteBranches.title";
                    case WORKTREES -> "panel.worktrees.title";
                });
    }

    @Override
    public PaneId pane() {
        return PANE;
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
        List<PaneTab<?>> added = fills.all(BranchesPane.TABS, model);
        List<TabId> ids = OWN.map(BranchesTab::id).appendAll(added.map(PaneTab::id));
        TabId shown = model.repoView().tab(PANE).filter(ids::contains).getOrElse(BranchesTab.LOCAL.id());
        ShownTabs showing = new ShownTabs(PANE, ids, shown);
        tabs = Option.of(showing);
        Option<PaneTab<?>> addedShown = added.find(tab -> tab.id().equals(shown));
        tabShown = addedShown.map(tab -> (TabList<?>) listOf(tab));
        List<String> names = OWN.map(BranchesPanel::tabName).appendAll(added.map(PaneTab::title));
        boolean focused = focus.contains(PANE);
        boolean active = inUse.contains(PANE);
        // Both are rendered also when they are not shown, to know when they are opened again.
        Option<Panel> divergence = divergenceList.render(divergence(model), focused, active);
        Option<Panel> branchCommits = branchCommitsList.render(model, focused, active);
        return divergence
                .orElse(branchCommits)
                .getOrElse(() -> tabPanel(model, showing, names, addedShown, focused, active));
    }

    /**
     * The pane showing the tab {@code showing} says, under a title naming every tab, {@code names}; a click on one of
     * them shows that tab.
     */
    private Panel tabPanel(
            Model model,
            ShownTabs showing,
            List<String> names,
            Option<PaneTab<?>> addedShown,
            boolean focused,
            boolean active) {
        Line title = TabTitles.line(names, showing.tabs().indexOf(showing.shown()));
        Panel panel = addedShown
                .map(tab -> addedPanel(tab, focused, active, title))
                .getOrElse(() -> list(BranchesTab.of(showing.shown()).getOrElse(BranchesTab.LOCAL))
                        .render(model, focused, active, title));
        return panel.onMouseEvent(event -> TabTitles.clicked(
                panel.renderedArea(),
                event,
                names,
                index -> dispatch.accept(
                        new ViewMsg.TabChosen(PANE, showing.tabs().get(index)))));
    }

    /**
     * The pane showing {@code tab}, a feature's.
     */
    private <T> Panel addedPanel(PaneTab<T> tab, boolean focused, boolean active, Line title) {
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
            made.onKey(event -> PaneKeys.sending(made.keyed(keymap, event, tabsHosted()), dispatch));
            tabLists = tabLists.put(tab.id(), made);
            return made;
        });
    }

    /**
     * The rows the last render showed of the feature's tab shown, if one was.
     */
    Option<List<LoadableList.Row>> addedRows() {
        return tabShown.map(TabList::shownRows);
    }

    /**
     * What the files pane lists while the list of the tab shown has the focus, rather than a divergence or the commits
     * of a branch (ADR 141): the files of the working tree for a feature's tab; none while that is not known yet.
     */
    @Override
    public Option<Followed> followed(Model model) {
        return model.repoView()
                .branchesTab()
                .map(tab -> list(tab).followed(model))
                .getOrElse(() -> Option.of(new Followed.WorkingTree()));
    }

    /**
     * The list {@code tab} shows.
     */
    private BranchesTabList list(BranchesTab tab) {
        return switch (tab) {
            case LOCAL -> localList;
            case REMOTE -> remoteList;
            case WORKTREES -> worktreeList;
        };
    }

    /**
     * The times the pane shows in {@code model} (ADR 124), without the badges of any feature.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return shownTimes(model, BranchBadges.NONE);
    }

    /**
     * The times the pane shows in {@code model} (ADR 124), with {@code badges}: those of the list it shows, which a
     * divergence or the commits of a branch shown in its place hide.
     */
    public static List<ShownTime> shownTimes(Model model, BranchBadges badges) {
        Option<BranchesTab> tab = model.repoView().branchesTab();
        if (!model.screen().equals(Screen.REPOSITORY)
                || divergence(model).isDefined()
                || model.repoView().branches().branchCommits().isDefined()) {
            return List.empty();
        }
        return tab.contains(BranchesTab.LOCAL)
                ? LocalBranchesList.shownTimes(model, badges)
                : tab.contains(BranchesTab.REMOTE) ? RemoteBranchesList.shownTimes(model, badges) : List.empty();
    }

    /**
     * Whether the pane shows a spinner in {@code model}: on a busy branch in the list it shows, which a divergence
     * or the commits of a branch shown in its place hide.
     */
    public static boolean showsSpinner(Model model) {
        Option<BranchesTab> tab = model.repoView().branchesTab();
        return model.screen().equals(Screen.REPOSITORY)
                && divergence(model).isEmpty()
                && model.repoView().branches().branchCommits().isEmpty()
                && model.jobs()
                        .busy()
                        .keySet()
                        .exists(ref -> tab.contains(BranchesTab.LOCAL)
                                ? ref instanceof BranchRef.Local
                                : tab.contains(BranchesTab.REMOTE) && ref instanceof BranchRef.Remote);
    }

    /**
     * The divergence {@code model} lists in place of the local branches, if any.
     */
    private static Option<DivergenceView> divergence(Model model) {
        return Option.when(
                        model.repoView().branchesTab().contains(BranchesTab.LOCAL),
                        model.repoView().branches()::divergence)
                .flatMap(open -> open);
    }

    /**
     * The message to send when the highlighted branch of the list shown is not the one whose log {@code model}
     * holds, or what a feature's tab shown says for its row. While {@code model} keeps a tab no feature gives any more,
     * which the pane shows the local branches in place of, it is first told to show the local branches, so that it
     * says what the pane shows.
     */
    @Override
    public Option<Msg> selectionChange(Model model) {
        boolean gone = model.repoView()
                .tab(PANE)
                .exists(kept -> tabs.exists(shown -> !shown.tabs().contains(kept)));
        if (gone) {
            return Option.of(new ViewMsg.TabChosen(PANE, BranchesTab.LOCAL.id()));
        }
        return divergence(model)
                .map(divergenceList::selectionChange)
                .orElse(() -> model.repoView().branches().branchCommits().map(branchCommitsList::selectionChange))
                .getOrElse(() -> model.repoView()
                        .branchesTab()
                        .fold(() -> tabShown.flatMap(TabList::selectionChange), tab -> tabSelectionChange(model, tab)));
    }

    // A row without a log says so, rather than leaving the log of the row highlighted before.
    private Option<Msg> tabSelectionChange(Model model, BranchesTab shown) {
        BranchesTabList tab = list(shown);
        return PaneKeys.selectionChange(
                        tab.selectedRef(), model.repoView().preview().branchLog(), SelectionMsg.BranchSelected::new)
                .orElse(() -> tab.noLog()
                        .filter(noLog -> !model.repoView().branches().noLog().contains(noLog))
                        .map(SelectionMsg.NoLogSelected::new));
    }
}
