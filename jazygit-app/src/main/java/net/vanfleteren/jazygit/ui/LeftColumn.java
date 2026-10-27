package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.column;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;

/**
 * The left column of the app: the status above the panes of the screen shown, which are the files, the branches, the
 * commits and the stashes of the repository, or its pull requests.
 */
public final class LeftColumn {

    private static final int FILES_PERCENT = 25;
    private static final int BRANCHES_PERCENT = 20;
    private static final int COMMITS_PERCENT = 30;
    // A border around a single blank line.
    private static final int MIN_STASH_HEIGHT = 3;

    private final Consumer<Msg> dispatch;
    private final StyleEngine styleEngine;
    private final FilesPanel filesPanel;
    private final BranchesPanel branchesPanel;
    private final CommitsPanel commitsPanel;
    private final StashPanel stashPanel;
    private final List<PullRequestsPanel> pullRequestsPanels;

    /**
     * @param styleEngine     the active theme, which highlights the selected rows
     * @param clock           tells the time the branches are drawn at
     * @param openContent     moves the focus to the content shown for the pane with the id given
     * @param showBranchesTab moves the focus to the branches pane, and shows the list given in it
     */
    public LeftColumn(
            Consumer<Msg> dispatch,
            StyleEngine styleEngine,
            Clock clock,
            Consumer<String> openContent,
            Consumer<BranchesTab> showBranchesTab) {
        this.dispatch = dispatch;
        this.styleEngine = styleEngine;
        // How the active theme highlights the selected row of a pane, in use or not.
        Theme.Highlights highlight = active -> Theme.highlights(styleEngine).of(active);
        this.filesPanel = new FilesPanel(dispatch, () -> openContent.accept(FilesPanel.ID), highlight);
        this.branchesPanel = new BranchesPanel(
                dispatch, showBranchesTab, () -> openContent.accept(BranchesPanel.ID), clock, highlight);
        this.commitsPanel = new CommitsPanel(dispatch, () -> openContent.accept(CommitsPanel.ID), highlight);
        this.stashPanel = new StashPanel(dispatch, () -> openContent.accept(StashPanel.ID), highlight);
        this.pullRequestsPanels = PullRequestsPanel.all(dispatch, openContent, highlight);
    }

    /**
     * The height of the stash pane: just its rows, but at most the share of the left column that is left once the
     * other panes have theirs, and at least an empty pane (a blank line in a border). Empty when the {@code height}
     * of the column is not known.
     */
    static Option<Integer> stashHeight(Model model, Option<Integer> height) {
        int rows = Math.max(1, model.repo().stashes().getOrElse(List.empty()).size());
        return height.map(column -> column
                        - StatusPanel.HEIGHT
                        - column * (FILES_PERCENT + BRANCHES_PERCENT + COMMITS_PERCENT) / 100)
                .map(share -> Math.max(MIN_STASH_HEIGHT, Math.min(rows + 2, share)));
    }

    /**
     * The column for the screen {@code model} shows, {@code height} rows tall if that is known.
     *
     * @param focusedId the element with the focus, if any
     * @param activeId  the pane in use, whose highlighted row shows
     */
    public Column render(Model model, String focusedId, String activeId, Option<Integer> height) {
        return model.screen() == Screen.PULL_REQUESTS
                ? pullRequestPanes(model, focusedId, activeId)
                : repositoryPanes(model, focusedId, activeId, height);
    }

    /**
     * What the panes highlight that the model, which they were last rendered from, does not know: the item the
     * content panel shows the preview of. On the pull requests screen, only the pane in use, {@code activeId}, says.
     */
    public List<Msg> selectionChanges(Model model, String activeId) {
        return model.screen() == Screen.PULL_REQUESTS
                // The content panel shows the pull request highlighted in the pane in use.
                ? pullRequestsPanels
                        .filter(pane -> pane.id().equals(activeId))
                        .flatMap(pane -> pane.selectionChange(model))
                : List.of(
                                branchesPanel.selectionChange(model),
                                commitsPanel.selectionChange(model),
                                filesPanel.selectionChange(model),
                                stashPanel.selectionChange(model))
                        .flatMap(change -> change);
    }

    /**
     * The left column of the repository screen: the status, the files, the branches, the commits and the stashes.
     */
    private Column repositoryPanes(Model model, String focusedId, String activeId, Option<Integer> height) {
        Panel branches = branchesPanel.render(model, focusedId, activeId);
        Panel stash = stashPanel.render(model, focusedId, activeId);
        Option<Integer> stashHeight = stashHeight(model, height);
        return column(
                status(model),
                filesPanel.render(model, focusedId, activeId).percent(FILES_PERCENT),
                branches.percent(BRANCHES_PERCENT),
                // With the stash sized to its content, the commits take what is left, which is
                // their share when the stash is as tall as it may get.
                stashHeight.isDefined()
                        ? commitsPanel.render(model, focusedId, activeId).fill()
                        : commitsPanel.render(model, focusedId, activeId).percent(COMMITS_PERCENT),
                stashHeight.map(stash::length).getOrElse(stash::fill));
    }

    /**
     * The left column of the pull requests screen: the status, the pull requests of the repository, the ones that ask
     * for the user's review, and the ones they opened, sharing the height.
     */
    private Column pullRequestPanes(Model model, String focusedId, String activeId) {
        List<Panel> panes = pullRequestsPanels.map(
                pane -> pane.render(model, focusedId, activeId).fill());
        return column(panes.<Element>map(pane -> pane).prepend(status(model)).toJavaArray(Element[]::new));
    }

    /**
     * The status pane, whose title shows a screen when its name is clicked.
     */
    private Panel status(Model model) {
        return StatusPanel.render(
                model, Theme.spanStyles(styleEngine), screen -> dispatch.accept(new PrOverviewMsg.Chosen(screen)));
    }
}
