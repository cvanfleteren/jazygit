package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Alignment;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.view.BranchesTab;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.github.PullRequestsPanel;
import net.vanfleteren.jazygit.ui.stash.StashPanel;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * The left column of the app: the status above the panes of the screen shown, which are the files, the branches, the
 * commits and the stashes of the repository, or its pull requests.
 */
public final class LeftColumn {

    /**
     * How many columns a collapsed left column takes: a border on either side of its mark (ADR 104).
     */
    public static final int COLLAPSED_COLUMNS = 3;

    /**
     * The mark at the top right of a collapsed left column, pointing the way it opens.
     */
    static final String COLLAPSED_MARK = "»";

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
     * The column for the screen {@code model} shows, with the panes under the status, whose bottom borders the user
     * drags: {@code rows} tall each if the rows of the column are known (ADR 92), else their share of it. A collapsed
     * column shows none of them: it is an empty panel with a mark at its top right (ADR 104).
     *
     * @param focusedId the element with the focus, if any
     * @param activeId  the pane in use, whose highlighted row shows
     */
    public Rendered render(Model model, String focusedId, String activeId, Option<List<Integer>> rows) {
        if (model.preferences().leftCollapsed(model.screen())) {
            return new Rendered(column(collapsed()), List.empty());
        }
        List<Panel> shown = model.screen() == Screen.PULL_REQUESTS
                ? pullRequestsPanels.map(pane -> pane.render(model, focusedId, activeId))
                : repositoryPanes(model, focusedId, activeId);
        List<Integer> heights = model.preferences().leftHeights(model.screen());
        List<Panel> panes = rows.fold(
                () -> shown.init()
                        .zipWith(heights, Panel::percent)
                        .append(shown.last().fill()),
                tall -> shown.zipWith(tall, Panel::length));
        return new Rendered(
                column(panes.<Element>map(pane -> pane).prepend(status(model)).toJavaArray(Element[]::new)),
                panes.map(pane -> pane));
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
     * The panes of the repository screen under the status: the files, the branches, the commits and the stashes.
     */
    private List<Panel> repositoryPanes(Model model, String focusedId, String activeId) {
        return List.of(
                filesPanel.render(model, focusedId, activeId),
                branchesPanel.render(model, focusedId, activeId),
                commitsPanel.render(model, focusedId, activeId),
                stashPanel.render(model, focusedId, activeId));
    }

    /**
     * The left column collapsed: an empty panel, marked at its top right.
     */
    static Panel collapsed() {
        return panel().title(Pane.title(COLLAPSED_MARK))
                .titleAlignment(Alignment.RIGHT)
                .fill();
    }

    /**
     * The status pane, whose title shows a screen when its name is clicked.
     */
    private Panel status(Model model) {
        return StatusPanel.render(
                model, Theme.spanStyles(styleEngine), screen -> dispatch.accept(new PrOverviewMsg.Chosen(screen)));
    }

    /**
     * The left column, and the panes in it under the status.
     */
    public record Rendered(Column column, List<Element> panes) {}
}
