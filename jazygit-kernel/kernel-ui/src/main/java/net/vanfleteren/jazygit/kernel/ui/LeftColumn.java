package net.vanfleteren.jazygit.kernel.ui;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Alignment;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.screen.FilesLeftPane;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;

/**
 * The left column of the app: the status above the panes of the screen shown, which are the files, the branches, the
 * commits and the stashes of the repository, or those of a screen a feature brings (ADR 168). The features bring the
 * panes of either (ADR 211); the column makes each once, and shows those of the screen shown in the order it lists
 * them.
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
    private final Screens screens;
    private final Map<Screen, List<LeftPane>> screenPanes;
    // The files pane, which steps to another file without the focus and lists what the pane in use follows, if a
    // feature brings it.
    private final Option<FilesLeftPane> filesPanel;

    /**
     * @param styleEngine the active theme, which highlights the selected rows
     * @param openContent moves the focus to the content shown for the pane given
     * @param panes       the panes the features bring to the screens, in the order of the features (ADR 211)
     * @param fills       what the features give for the slots, which the panes ask for theirs (ADR 172)
     * @param clock       turns the spinners of the panes
     * @param screens     the screens the left of the window shows
     */
    public LeftColumn(
            Consumer<Msg> dispatch,
            StyleEngine styleEngine,
            Consumer<PaneId> openContent,
            List<ScreenPane> panes,
            Fills fills,
            Clock clock,
            Screens screens) {
        this.dispatch = dispatch;
        this.styleEngine = styleEngine;
        // How the active theme highlights the selected row of a pane, in use or not.
        Theme.Highlights highlight = active -> Theme.highlights(styleEngine).of(active);
        ScreenPane.Context context = new ScreenPane.Context(dispatch, openContent, highlight, fills, clock);
        this.screens = screens;
        this.screenPanes = panes.groupBy(ScreenPane::screen)
                .mapValues(brought -> brought.map(pane -> pane.factory().of(context)));
        this.filesPanel = screenPanes
                .values()
                .flatMap(made -> made)
                .flatMap(LeftColumn::files)
                .headOption();
    }

    /**
     * The column for the screen {@code model} shows, with the panes under the status, whose bottom borders the user
     * drags: {@code rows} tall each if the rows of the column are known (ADR 92), else their share of it. A collapsed
     * column shows none of them: it is an empty panel with a mark at its top right (ADR 104).
     *
     * @param focus what has the focus, if anything
     * @param inUse the pane in use, whose highlighted row shows
     */
    public Rendered render(Model model, Option<Focus> focus, Option<PaneId> inUse, Option<List<Integer>> rows) {
        if (model.preferences().leftCollapsed(model.screen())) {
            return new Rendered(column(collapsed()), List.empty());
        }
        List<Panel> shown = panesOf(model.screen()).map(pane -> pane.render(model, focus, inUse));
        if (shown.isEmpty()) {
            // A screen whose feature made none of its panes shows the status alone.
            return new Rendered(column(status(model)), List.empty());
        }
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
     * Moves the highlight of the files pane to its file {@code step} of the one highlighted, keeping the focus where it
     * is, and says whether there was one (ADR 117).
     */
    public boolean stepFile(Model model, FileStepMsg.Step step) {
        return filesPanel.exists(files -> files.step(model, step));
    }

    /**
     * What the panes of the screen shown highlight that the model, which they were last rendered from, does not know:
     * the item the content panel shows the preview of, and whose files the files pane lists (ADR 141). A pane the
     * content panel mirrors on its own, as those of the repository, always says; of the others, only the pane in use,
     * {@code inUse}, whose highlight the content panel shows (ADR 168).
     */
    public List<Msg> selectionChanges(Model model, Option<PaneId> inUse) {
        List<LeftPane> shown = panesOf(model.screen());
        Function<PaneId, Option<Followed>> followed =
                id -> shown.find(pane -> pane.pane().equals(id)).flatMap(pane -> pane.followed(model));
        return shown.filter(pane -> Mirrored.of(pane.pane()).isDefined() || inUse.contains(pane.pane()))
                .flatMap(pane -> pane.selectionChange(model))
                .appendAll(
                        shown.flatMap(LeftColumn::files).flatMap(files -> files.followChange(model, inUse, followed)));
    }

    /**
     * The panes of {@code screen}, top to bottom: those the features made for it, in the order the screen lists them,
     * matched by their ids.
     */
    private List<LeftPane> panesOf(Screen screen) {
        List<LeftPane> made = screenPanes.get(screen).getOrElse(List.empty());
        return screen.panes().flatMap(id -> made.find(pane -> pane.pane().equals(id)));
    }

    /**
     * {@code pane}, if it is the files pane.
     */
    private static Option<FilesLeftPane> files(LeftPane pane) {
        return pane instanceof FilesLeftPane files ? Option.of(files) : Option.none();
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
                model, Theme.spanStyles(styleEngine), screens, screen -> dispatch.accept(new ScreenMsg.Shown(screen)));
    }

    /**
     * The left column, and the panes in it under the status.
     */
    public record Rendered(Column column, List<Element> panes) {}
}
