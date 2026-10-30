package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
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
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.settings.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.*;
import net.vanfleteren.jazygit.ui.widgets.*;

/**
 * jazygit, a lazygit-style terminal UI for Git built with TamboUI: lays out the panes, the content and the open
 * popup of the {@link Model}, and routes what the user does to it.
 *
 * <p>Rendering never touches git: it only draws the current {@link Model}. Git is read on a
 * background thread by the {@link Program}, which publishes new models on the render thread.
 */
public class JazygitApp extends ToolkitApp {

    // Rows of the command log panel, borders included.
    private static final int COMMAND_LOG_HEIGHT = CommandLogPanel.VISIBLE_LINES + 2;

    private final SettingsStore.Loaded settings;
    private final Option<ThemeName> themeOverride;
    private final StyleEngine styleEngine;
    // Tells the time a frame is drawn at, which the spinners turn by.
    private final Clock clock = Clock.systemUTC();
    // The terminal the app is shown on, which it hands over to an editor.
    private final TerminalHandOver terminal =
            new TerminalHandOver(this::runner, this::requestRedraw, focused -> send(new Msg.TerminalFocused(focused)));
    private final RepositorySession session;
    // Quitting is up to the model, which may refuse; the runner stops on what the update returns.
    private final Consumer<Msg> dispatch = this::send;
    private final Popups popups = new Popups(dispatch);
    private final ContentPanel contentPanel;
    private final FocusKeeper focus;
    private final LeftColumn leftColumn;
    // The borders of the left column and of the panes in it, which the user drags.
    private final BorderDrag borderDrag = new BorderDrag(dispatch);
    // Whether a Redraw is queued that no frame has been drawn since; only touched on the render thread.
    private boolean redrawPending;

    /**
     * @param env           what the repository the app starts in is read with; it opens other repositories in
     *                      the app
     * @param settings      the settings the session starts with, as {@code env} loaded them
     * @param themeOverride the theme for this session instead of the saved one, if any
     */
    public JazygitApp(
            Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, StyleEngine styleEngine) {
        this.settings = settings;
        this.themeOverride = themeOverride;
        this.styleEngine = styleEngine;
        this.contentPanel = new ContentPanel(
                Theme.spanStyles(styleEngine),
                this::leaveScrollArea,
                id -> runner().focusManager().setFocus(id),
                dispatch);
        this.focus = new FocusKeeper(this::runner, this::model, contentPanel, popups, this::requestRedraw);
        this.leftColumn = new LeftColumn(dispatch, styleEngine, clock, focus::openContent, this::showBranchesTab);
        this.session = new RepositorySession(
                env, terminal, this::runner, clock, this::requestRedraw, this::quit, this::started);
    }

    /**
     * Shows the repository a program was started for, whose first model is {@code model}: the one the app starts in,
     * or one opened in its place.
     */
    private void started(Model model) {
        focus.moveToPaneOf(model);
        terminal.title(Messages.get("window.title", model.repositoryName()));
    }

    /**
     * Hands {@code msg} to the program of the repository shown.
     */
    private void send(Msg msg) {
        session.dispatch(msg);
    }

    /**
     * Gives the focus back to the pane whose content has it, and says whether there was one.
     */
    private boolean leaveScrollArea() {
        return focus.leaveScrollArea();
    }

    /**
     * Moves the focus to the branches pane, as a click on it does, and shows the list {@code tab} in it.
     */
    private void showBranchesTab(BranchesTab tab) {
        focus.moveTo(BranchesPanel.ID);
        dispatch.accept(new BranchesTabMsg.Chosen(tab));
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
     * Drags the border between the columns or between the panes of the left column, or else gives the focus to what a click lands on.
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
        focus.clicked(mouse);
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
        String focusedId = focus.settle(model);
        String activeId = focus.activePane(model, focusedId);
        // The left column is the screen without the fetch line.
        Option<Integer> leftHeight = Try.of(
                        () -> runner().tuiRunner().backend().size().height() - FetchLine.HEIGHT)
                .toOption();
        LeftColumn.Rendered rendered = leftColumn.render(model, focusedId, activeId, leftHeight);
        Column left = rendered.column().percent(model.preferences().leftWidth(model.screen()));
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
        borderDrag.shown(panes, left, rendered.panes());
        // The framework knows no focus before the first render.
        Popups.Rendered shown = popups.render(model, Option.of(focusedId));
        report(reports());
        shown.moveTo().forEach(focus::moveTo);
        focus.redrawIfMoves(focusedId);
        // What is behind an open popup is dimmed, so the popup stands out, unless the popup previews its choices there.
        Element behind = shown.dimsBehind() ? new Dimmed(layout, Theme.backdrop(styleEngine)) : layout;
        Element screen = shown.dialog().<Element>map(d -> stack(behind, d)).getOrElse(layout);
        return new SelectedTextColoured(screen, Theme.selectedText(styleEngine));
    }

    /**
     * What the widgets know that the model, which they were last rendered from, does not: the item highlighted in each
     * pane, which the content panel shows the preview of, and where the focus is outside the popups, which a closing
     * popup gives it back to. Empty once the model agrees with them. Called on the render thread.
     */
    List<Msg> reports() {
        Model model = model();
        String focusedId = focus.focusedId();
        return leftColumn
                .selectionChanges(model, focus.activePane(model, focusedId))
                .appendAll(focus.report(model, focusedId));
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
     * Asks for a frame, as {@link #requestRedraw()} does.
     */
    private record Redraw() implements Event {}
}
