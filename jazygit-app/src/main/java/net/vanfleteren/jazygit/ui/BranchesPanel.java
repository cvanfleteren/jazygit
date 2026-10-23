package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;

/**
 * Left-side pane with three lists, one at a time: the {@link LocalBranchesList local branches}, the
 * {@link RemoteBranchesList remote-tracking ones} and the {@link WorktreesList worktrees}. {@code [} and {@code ]}
 * go through them, in the same pane, so the focus moves through it as one. Clicking the name of a list in the title
 * shows that list.
 * <p>
 * While a divergence is open, it is listed in place of the local branches, by a {@link DivergenceList}; Escape goes
 * back to the branches. So it does from the commits of a branch, which Enter on a branch lists in place of the
 * branches, by a {@link BranchCommitsList}.
 */
public class BranchesPanel {

    public static final String ID = Scopes.BRANCHES.id();

    private static final String TITLE_SEPARATOR = " │ ";

    private final LocalBranchesList localList;
    private final RemoteBranchesList remoteList;
    private final WorktreesList worktreeList;
    private final DivergenceList divergenceList;
    private final BranchCommitsList branchCommitsList;
    private final Consumer<BranchesTab> tabClicked;

    /**
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param tabClicked called when the name of a list in the title is clicked, with that list
     */
    public BranchesPanel(Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked) {
        this(dispatch, tabClicked, Clock.systemUTC());
    }

    /**
     * @param clock animates the spinners of the busy branches; how long ago they were committed to is told by the
     *              time of the model
     */
    public BranchesPanel(Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked, Clock clock) {
        this(dispatch, tabClicked, clock, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     */
    public BranchesPanel(
            Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked, Clock clock, Theme.Highlights highlights) {
        this(dispatch, tabClicked, () -> {}, clock, highlights);
    }

    /**
     * @param openCommit called when Enter is pressed on a commit of the divergence or of a branch, to move focus to
     *                   its content
     */
    public BranchesPanel(
            Consumer<Msg> dispatch,
            Consumer<BranchesTab> tabClicked,
            Runnable openCommit,
            Clock clock,
            Theme.Highlights highlights) {
        this.tabClicked = tabClicked;
        // Only the list shown is rendered, that of the tab, or the divergence or the commits of a branch in its place,
        // so only it gets keys: TamboUI routes them to the elements of the last frame.
        localList = new LocalBranchesList(ID, dispatch, clock, highlights);
        remoteList = new RemoteBranchesList(ID, dispatch, clock, highlights);
        worktreeList = new WorktreesList(ID, dispatch, highlights);
        divergenceList = new DivergenceList(ID, dispatch, openCommit, highlights);
        branchCommitsList = new BranchCommitsList(ID, dispatch, openCommit, highlights);
    }

    /**
     * The name of {@code tab} in the title.
     */
    private static Span tabName(BranchesTab tab) {
        return Span.raw(Messages.get(
                switch (tab) {
                    case LOCAL -> "panel.branches.title";
                    case REMOTE -> "panel.remoteBranches.title";
                    case WORKTREES -> "panel.worktrees.title";
                }));
    }

    /**
     * The title of the pane: the names of the lists, the one shown in bold and underlined, the others dimmed.
     * The title takes the colour of the border, so they differ in text style only; underlining shows the
     * difference in themes with colours that bold and dim barely change.
     */
    static Line title(BranchesTab tab) {
        List<Span> names = List.of(BranchesTab.values())
                .map(each -> each == tab
                        ? tabName(each).bold().underlined()
                        : tabName(each).dim());
        return Line.from(names.intersperse(Span.raw(TITLE_SEPARATOR)).toJavaList());
    }

    /**
     * The list whose name in the title is {@code column} cells from the start of the title, if any; the
     * separators between them are none.
     */
    static Option<BranchesTab> tabAt(int column) {
        int separator = Span.raw(TITLE_SEPARATOR).width();
        List<BranchesTab> tabs = List.of(BranchesTab.values());
        // Where the name of each tab starts: after the names and separators before it.
        List<Integer> starts =
                tabs.init().scanLeft(0, (start, tab) -> start + tabName(tab).width() + separator);
        return tabs.zip(starts)
                .find(tab ->
                        column >= tab._2 && column < tab._2 + tabName(tab._1).width())
                .map(tab -> tab._1);
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return render(model, focusedId, focusedId);
    }

    /**
     * @param activeId the id of the left pane in use, whose highlighted row shows
     */
    public Panel render(Model model, String focusedId, String activeId) {
        boolean focused = ID.equals(focusedId);
        boolean active = ID.equals(activeId);
        // Both are rendered also when they are not shown, to know when they are opened again.
        Option<Panel> divergence = divergenceList.render(divergence(model), focused, active);
        Option<Panel> branchCommits = branchCommitsList.render(model, focused, active);
        return divergence.orElse(branchCommits).getOrElse(() -> tabPanel(model, focused, active));
    }

    private Panel tabPanel(Model model, boolean focused, boolean active) {
        BranchesTab tab = model.repoView().branchesTab();
        Panel panel = list(tab).render(model, focused, active, title(tab));
        return panel.onMouseEvent(event -> titleClick(panel.renderedArea(), event, tabClicked));
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
     * Shows the list whose name in the title of the pane, drawn in {@code area}, is clicked. The title starts
     * after the corner of the border.
     */
    private static EventResult titleClick(Rect area, MouseEvent event, Consumer<BranchesTab> tabClicked) {
        return Option.when(
                        event.kind() == MouseEventKind.PRESS && event.isLeftButton() && event.y() == area.top(),
                        event.x() - area.left() - 1)
                .flatMap(BranchesPanel::tabAt)
                .map(clicked -> {
                    tabClicked.accept(clicked);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * Whether the pane shows a spinner in {@code model}: on a busy branch in the list it shows, which a divergence
     * or the commits of a branch shown in its place hide.
     */
    public static boolean showsSpinner(Model model) {
        BranchesTab tab = model.repoView().branchesTab();
        return model.screen() == Screen.REPOSITORY
                && divergence(model).isEmpty()
                && model.repoView().branchCommits().isEmpty()
                && model.jobs()
                        .busy()
                        .keySet()
                        .exists(ref -> tab == BranchesTab.LOCAL
                                ? ref instanceof BranchRef.Local
                                : tab == BranchesTab.REMOTE && ref instanceof BranchRef.Remote);
    }

    /**
     * The divergence {@code model} lists in place of the local branches, if any.
     */
    private static Option<DivergenceView> divergence(Model model) {
        return Option.when(model.repoView().branchesTab() == BranchesTab.LOCAL, model.repoView()::divergence)
                .flatMap(open -> open);
    }

    /**
     * The message to send when the highlighted branch of the list shown is not the one whose log {@code model}
     * holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return divergence(model)
                .map(divergenceList::selectionChange)
                .orElse(() -> model.repoView().branchCommits().map(branchCommitsList::selectionChange))
                .getOrElse(() -> tabSelectionChange(model));
    }

    private Option<Msg> tabSelectionChange(Model model) {
        return PaneKeys.selectionChange(
                list(model.repoView().branchesTab()).selectedRef(),
                model.repoView().branchLog(),
                SelectionMsg.BranchSelected::new);
    }
}
