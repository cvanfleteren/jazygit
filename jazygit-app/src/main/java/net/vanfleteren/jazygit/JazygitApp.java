package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.settings.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.*;
import net.vanfleteren.jazygit.ui.widgets.*;

/**
 * Entry point for jazygit, a lazygit-style terminal UI for Git built with TamboUI.
 *
 * <p>Rendering never touches git: it only draws the current {@link Model}. Git is read on a
 * background thread by the {@link Program}, which publishes new models on the render thread.
 */
public class JazygitApp extends ToolkitApp {

    // Rows of the command log panel, borders included.
    private static final int COMMAND_LOG_HEIGHT = CommandLogPanel.VISIBLE_LINES + 2;
    private static final int FILES_PERCENT = 25;
    private static final int BRANCHES_PERCENT = 20;
    private static final int COMMITS_PERCENT = 30;
    // A border around a single blank line.
    private static final int MIN_STASH_HEIGHT = 3;

    private final SettingsStore.Loaded settings;
    private final Option<ThemeName> themeOverride;
    private final StyleEngine styleEngine;
    // Tells the time a frame is drawn at, which the spinners turn by.
    private final Clock clock = Clock.systemUTC();
    // The terminal the app is shown on, which it hands over to an editor.
    private final TerminalHandOver terminal = new TerminalHandOver(this::runner, this::requestRedraw);
    private final RepositorySession session;
    // Quitting is up to the model, which may refuse; the runner stops on what the update returns.
    private final Consumer<Msg> dispatch = this::send;
    private final ContentPanel contentPanel;
    private final FilesPanel filesPanel = new FilesPanel(dispatch, () -> openContent(FilesPanel.ID), this::highlight);
    private final BranchesPanel branchesPanel = new BranchesPanel(
            dispatch, this::showBranchesTab, () -> openContent(BranchesPanel.ID), clock, this::highlight);
    private final CommitsPanel commitsPanel =
            new CommitsPanel(dispatch, () -> openContent(CommitsPanel.ID), this::highlight);
    private final StashPanel stashPanel = new StashPanel(dispatch, () -> openContent(StashPanel.ID), this::highlight);
    private final List<PullRequestsPanel> pullRequestsPanels =
            PullRequestsPanel.all(dispatch, this::openContent, this::highlight);
    private final Popups popups = new Popups(dispatch);
    // The screen the last frame showed; when the model shows another one, its pane gets the focus back.
    private Screen shownScreen = Screen.REPOSITORY;
    // The border between the left column and the content panel, which the user drags.
    private final BorderDrag borderDrag = new BorderDrag(dispatch);
    // Whether a Redraw is queued that no frame has been drawn since; only touched on the render thread.
    private boolean redrawPending;
    // The focus the last frame was drawn with that was not empty, which a click on nothing that takes it clears.
    private Option<String> lastFocus = Option.none();

    /**
     * @param env           what the repository the app starts in is read with; it opens other repositories in
     *                      the app
     * @param settings      the settings the session starts with, as {@code env} loaded them
     * @param themeOverride the theme for this session instead of the saved one, if any
     */
    public JazygitApp(
            Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, StyleEngine styleEngine) {
        this.session = new RepositorySession(
                env,
                terminal,
                this::runner,
                clock,
                this::requestRedraw,
                this::quit,
                model -> runner().focusManager().setFocus(PopupSlot.focusedPane(model)));
        this.settings = settings;
        this.themeOverride = themeOverride;
        this.styleEngine = styleEngine;
        this.contentPanel = new ContentPanel(
                Theme.spanStyles(styleEngine),
                this::leaveScrollArea,
                id -> runner().focusManager().setFocus(id),
                dispatch);
    }

    /**
     * Hands {@code msg} to the program of the repository shown.
     */
    private void send(Msg msg) {
        session.dispatch(msg);
    }

    /**
     * How the active theme highlights the selected row of a left pane, in use or not.
     */
    private Style highlight(boolean active) {
        return Theme.highlights(styleEngine).of(active);
    }

    /**
     * The left pane in use, whose highlighted row shows: the one with the focus, also while the content it
     * selects has the focus, or a popup that has it.
     */
    private String activePane(Model model, String focusedId) {
        return Option.of(focusedId)
                .filter(id -> !popups.owns(id))
                .orElse(model.shownPane())
                .map(id -> contentPanel.paneOfArea(model, id).getOrElse(id))
                .getOrNull();
    }

    /**
     * Moves the focus to the content shown for the pane {@code paneId}, so it can be scrolled by key.
     */
    private void openContent(String paneId) {
        contentPanel
                .areaOfPane(model(), paneId)
                .forEach(id -> runner().focusManager().setFocus(id));
    }

    /**
     * Moves the focus to the branches pane, as a click on it does, and shows the list {@code tab} in it.
     */
    private void showBranchesTab(BranchesTab tab) {
        runner().focusManager().setFocus(BranchesPanel.ID);
        dispatch.accept(new BranchesTabMsg.Chosen(tab));
    }

    /**
     * Shows {@code screen}, as clicking its name in the title of the status pane does.
     */
    private void showScreen(Screen screen) {
        dispatch.accept(new PrOverviewMsg.Chosen(screen));
    }

    /**
     * Gives the focus to the pane or area a left click lands on, which then handles the click as usual. TamboUI only
     * does so for a click that no element handles, but the layout around the panes hands the click to the pane, which
     * does. An open popup keeps the focus.
     */
    private void focusClicked(MouseEvent event) {
        String focused = runner().focusManager().focusedId();
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton() && !popups.owns(focused)) {
            runner().focusManager().focusAt(event.x(), event.y());
        }
    }

    /**
     * Gives the focus back to the pane whose content has it, and says whether there was one.
     */
    private boolean leaveScrollArea() {
        Option<String> pane =
                contentPanel.paneOfArea(model(), runner().focusManager().focusedId());
        pane.forEach(runner().focusManager()::setFocus);
        return pane.isDefined();
    }

    /**
     * Asks for a frame, for a change that draws none of its own: TamboUI draws one after an event that is handled,
     * and on a resize, but not after what is run on the render thread otherwise. At most one is queued; a frame
     * drawn for another reason before it comes takes its place. Called on the render thread.
     */
    void requestRedraw() {
        if (!redrawPending) {
            redrawPending = true;
            runner().tuiRunner().dispatch(new Redraw());
        }
    }

    @Override
    protected void onStart() {
        ToolkitRunner runner = runner();
        runner.styleEngine(styleEngine);
        runner.eventRouter().addGlobalHandler(this::handleRedraw);
        runner.eventRouter().addGlobalHandler(this::handleGlobalKey);
        runner.eventRouter().addGlobalHandler(this::handleMouse);
        session.start(settings, themeOverride);
        Timers.start(runner, dispatch, this::turnSpinners);
    }

    /**
     * Handled, a Redraw draws a frame, unless one was drawn since it was queued.
     */
    private EventResult handleRedraw(Event event) {
        return event instanceof Redraw && redrawPending ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * Dispatches what a key means wherever the focus is, if it means something there. An open popup owns the keys:
     * what it leaves unhandled reaches neither the global keys nor TamboUI's quit.
     */
    private EventResult handleGlobalKey(Event event) {
        if (event instanceof KeyEvent && model().popup().isDefined()) {
            return EventResult.HANDLED;
        }
        return Option.of(event)
                .filter(KeyEvent.class::isInstance)
                .flatMap(key -> Keymap.standard().handleGlobal((KeyEvent) key))
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * Drags the border between the columns, or else gives the focus to what a click lands on.
     */
    private EventResult handleMouse(Event event) {
        if (!(event instanceof MouseEvent mouse)) {
            return EventResult.UNHANDLED;
        }
        // A press on nothing that takes the focus clears it, without drawing; the frame gives it back.
        if (mouse.kind() == MouseEventKind.PRESS) {
            requestRedraw();
        }
        if (borderDrag.handle(mouse, model()).isHandled()) {
            return EventResult.HANDLED;
        }
        focusClicked(mouse);
        return EventResult.UNHANDLED;
    }

    /**
     * A spinner turns by being drawn.
     */
    private void turnSpinners() {
        if (Spinners.shown(model(), clock.instant())) {
            requestRedraw();
        }
    }

    @Override
    protected TuiConfig configure() {
        return terminal.configure();
    }

    @Override
    protected void onStop() {
        session.close();
    }

    /**
     * The model of the running program; the initial one before it runs.
     */
    Model model() {
        return session.model();
    }

    @Override
    protected Element render() {
        redrawPending = false;
        Model model = model();
        Theme.activate(styleEngine, model.preferences().theme());
        // A click on nothing that takes the focus cleared it; what had it keeps it.
        if (runner().focusManager().focusedId() == null) {
            lastFocus.forEach(runner().focusManager()::setFocus);
        }
        if (model.screen() != shownScreen) {
            shownScreen = model.screen();
            runner().focusManager().setFocus(PopupSlot.focusedPane(model));
        }
        String focusedId = runner().focusManager().focusedId();
        lastFocus = Option.of(focusedId).orElse(lastFocus);
        String activeId = activePane(model, focusedId);
        Column left = (model.screen() == Screen.PULL_REQUESTS
                        ? pullRequestPanes(model, focusedId, activeId)
                        : repositoryPanes(model, focusedId, activeId))
                .percent(model.preferences().leftWidth(model.screen()));
        var panes = row(
                left,
                column(
                                contentPanel.render(model, focusedId).fill(),
                                CommandLogPanel.render(model).length(COMMAND_LOG_HEIGHT))
                        .fill());
        // The fetch line keeps its row under the panes, also when nothing is fetched, so they do not move.
        Element layout = column(
                panes.fill(),
                FetchLine.render(model, clock.instant(), screenWidth()).length(FetchLine.HEIGHT));
        borderDrag.shown(panes, left);
        // The framework knows no focus before the first render.
        Popups.Rendered shown = popups.render(model, Option.of(focusedId));
        report(reports());
        shown.moveTo().forEach(runner().focusManager()::setFocus);
        redrawIfFocusMoves(focusedId);
        // What is behind an open popup is dimmed, so the popup stands out.
        Element screen = shown.dialog()
                .<Element>map(d -> stack(new Dimmed(layout, Theme.backdrop(styleEngine)), d))
                .getOrElse(layout);
        return new SelectedTextColoured(screen, Theme.selectedText(styleEngine));
    }

    /**
     * Asks for another frame once the frame being rendered is drawn, if the focus is no longer {@code drawnWith} by
     * then: a popup that opens or closes moves it, and TamboUI focuses the first pane after a frame drawn with no
     * focus or with one that is gone. The model does not know the focus, so does not ask for the frame.
     */
    private void redrawIfFocusMoves(String drawnWith) {
        runner().tuiRunner().runLater(() -> {
            if (!Objects.equals(runner().focusManager().focusedId(), drawnWith)) {
                requestRedraw();
            }
        });
    }

    /**
     * The left column of the repository screen: the status, the files, the branches, the commits and the stashes.
     */
    private Column repositoryPanes(Model model, String focusedId, String activeId) {
        Panel branches = branchesPanel.render(model, focusedId, activeId);
        Panel stash = stashPanel.render(model, focusedId, activeId);
        Option<Integer> stashHeight = stashHeight(model);
        return column(
                StatusPanel.render(model, Theme.spanStyles(styleEngine), this::showScreen),
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
        return column(panes.<Element>map(pane -> pane)
                .prepend(StatusPanel.render(model, Theme.spanStyles(styleEngine), this::showScreen))
                .toJavaArray(Element[]::new));
    }

    /**
     * What the widgets know that the model, which they were last rendered from, does not: the item highlighted in each
     * pane, which the content panel shows the preview of, and where the focus is outside the popups, which a closing
     * popup gives it back to. Empty once the model agrees with them. Called on the render thread.
     */
    List<Msg> reports() {
        Model model = model();
        String focusedId = runner().focusManager().focusedId();
        String activeId = activePane(model, focusedId);
        List<Msg> selections = model.screen() == Screen.PULL_REQUESTS
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
        // The framework knows no focus before the first render.
        Option<Msg> focused = Option.of(focusedId)
                .filter(id -> !popups.owns(id))
                .filter(id -> !model.shownPane().contains(id))
                .map(id -> new Msg.Focused(id, ContentPanel.isArea(id)));
        return selections.appendAll(focused);
    }

    /**
     * Tells the model what the widgets know, once the frame being rendered is drawn: never during a render, which
     * draws one model throughout. The next frame draws what the reports change.
     */
    private void report(List<Msg> reports) {
        if (!reports.isEmpty()) {
            runner().tuiRunner().runLater(() -> reports.forEach(dispatch));
        }
    }

    /**
     * The columns of the screen; as many as there could be when that is not known.
     */
    private int screenWidth() {
        return Try.of(() -> runner().tuiRunner().backend().size().width()).getOrElse(Integer.MAX_VALUE);
    }

    /**
     * The height of the stash pane: just its rows, but at most the share of the left column that is
     * left once the other panes have theirs, and at least an empty pane (a blank line in a border).
     * Empty when the terminal size is not known.
     */
    private Option<Integer> stashHeight(Model model) {
        int rows = Math.max(1, model.repo().stashes().getOrElse(List.empty()).size());
        // The left column is the screen without the fetch line.
        return Try.of(() -> runner().tuiRunner().backend().size().height() - FetchLine.HEIGHT)
                .toOption()
                .map(height -> height
                        - StatusPanel.HEIGHT
                        - height * (FILES_PERCENT + BRANCHES_PERCENT + COMMITS_PERCENT) / 100)
                .map(share -> Math.max(MIN_STASH_HEIGHT, Math.min(rows + 2, share)));
    }

    /**
     * Asks for a frame, as {@link #requestRedraw()} does.
     */
    private record Redraw() implements Event {}
}
