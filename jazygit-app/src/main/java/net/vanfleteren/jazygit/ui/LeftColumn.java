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
     * How many rows each pane under the status takes of a column {@code height} rows tall, whose panes but the last
     * are {@code heights} percent of the column tall: the last takes what they and the status leave. The borders
     * between the panes are rounded to a row, not the panes, so moving one border leaves the others where they are.
     */
    static List<Integer> paneRows(List<Integer> heights, int height) {
        List<Integer> borders = heights.scanLeft(0, Integer::sum).map(border -> Math.round(height * border / 100f));
        return borders.tail()
                .zipWith(borders, (bottom, top) -> bottom - top)
                .append(Math.max(0, height - StatusPanel.HEIGHT - borders.last()));
    }

    /**
     * The height of the stash pane: just its rows, but at most the share of the left column that is left once the
     * other panes have theirs, and at least an empty pane (a blank line in a border). Empty when the {@code height}
     * of the column is not known.
     */
    static Option<Integer> stashHeight(Model model, Option<Integer> height) {
        int rows = Math.max(1, model.repo().stashes().getOrElse(List.empty()).size());
        return height.map(column -> paneRows(model.preferences().leftHeights(Screen.REPOSITORY), column)
                        .last())
                .map(share -> Math.max(MIN_STASH_HEIGHT, Math.min(rows + 2, share)));
    }

    /**
     * The column for the screen {@code model} shows, {@code height} rows tall if that is known, with the panes under
     * the status, whose bottom borders the user drags.
     *
     * @param focusedId the element with the focus, if any
     * @param activeId  the pane in use, whose highlighted row shows
     */
    public Rendered render(Model model, String focusedId, String activeId, Option<Integer> height) {
        List<Panel> panes = model.screen() == Screen.PULL_REQUESTS
                ? pullRequestPanes(model, focusedId, activeId, height)
                : repositoryPanes(model, focusedId, activeId, height);
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
    private List<Panel> repositoryPanes(Model model, String focusedId, String activeId, Option<Integer> height) {
        List<Integer> heights = model.preferences().leftHeights(Screen.REPOSITORY);
        Panel stash = stashPanel.render(model, focusedId, activeId);
        Panel commits = commitsPanel.render(model, focusedId, activeId);
        Option<Integer> stashHeight = stashHeight(model, height);
        return sized(
                        List.of(
                                filesPanel.render(model, focusedId, activeId),
                                branchesPanel.render(model, focusedId, activeId)),
                        heights.take(2),
                        height)
                // With the stash sized to its content, the commits take what is left, which is
                // their share when the stash is as tall as it may get.
                .append(stashHeight.isDefined() ? commits.fill() : commits.percent(heights.get(2)))
                .append(stashHeight.map(stash::length).getOrElse(stash::fill));
    }

    /**
     * The panes of the pull requests screen under the status: the pull requests of the repository, the ones that ask
     * for the user's review, and the ones they opened.
     */
    private List<Panel> pullRequestPanes(Model model, String focusedId, String activeId, Option<Integer> height) {
        List<Panel> panes = pullRequestsPanels.map(pane -> pane.render(model, focusedId, activeId));
        return sized(panes.init(), model.preferences().leftHeights(Screen.PULL_REQUESTS), height)
                .append(panes.last().fill());
    }

    /**
     * The {@code panes} on top of the column, each its height of {@code heights} tall: in rows of a column
     * {@code height} rows tall, else in percent of the column.
     */
    private static List<Panel> sized(List<Panel> panes, List<Integer> heights, Option<Integer> height) {
        return height.fold(
                () -> panes.zipWith(heights, Panel::percent),
                column -> panes.zipWith(paneRows(heights, column), Panel::length));
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
