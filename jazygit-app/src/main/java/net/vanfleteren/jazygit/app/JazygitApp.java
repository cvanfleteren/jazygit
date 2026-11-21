package net.vanfleteren.jazygit.app;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.app.askpass.PopupAsking;
import net.vanfleteren.jazygit.app.askpass.SshPromptDialog;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.core.askpass.AskpassServer;
import net.vanfleteren.jazygit.core.git.branches.BatchSsh;
import net.vanfleteren.jazygit.core.process.SshAsking;
import net.vanfleteren.jazygit.features.branches.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.files.step.FileStepEnded;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.features.changes.panes.stash.StashPanel;
import net.vanfleteren.jazygit.features.chrome.help.HelpMsg;
import net.vanfleteren.jazygit.features.chrome.help.HelpPopup;
import net.vanfleteren.jazygit.features.chrome.layout.LayoutMsg;
import net.vanfleteren.jazygit.kernel.content.ContentPanel;
import net.vanfleteren.jazygit.kernel.content.ContentView;
import net.vanfleteren.jazygit.kernel.settings.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.*;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSources;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.*;

/**
 * jazygit, a lazygit-style terminal UI for Git built with TamboUI: lays out the panes, the content and the open
 * popup of the {@link Model}, and routes what the user does to it.
 *
 * <p>Rendering never touches git: it only draws the current {@link Model}. Git is read on a
 * background thread by the {@link Program}, which publishes new models on the render thread.
 */
public class JazygitApp extends ToolkitApp {

    // How long after a frame of the left column or its panes on their way the next one is drawn (ADR 81, 92).
    private static final Duration SLIDE_FRAME = Duration.ofMillis(16);

    private final SettingsStore.Loaded settings;
    private final Option<ThemeName> themeOverride;
    private final StyleEngine styleEngine;
    // Tells the time a frame is drawn at, which the spinners turn by.
    private final Clock clock = Clock.systemUTC();
    // The terminal the app is shown on, which it hands over to an editor.
    // Counts the frames drawn, when measured (scripts/framebench).
    private final FrameStats frameStats = FrameStats.fromProperty();
    private final TerminalHandOver terminal = new TerminalHandOver(
            this::runner, this::requestRedraw, focused -> send(new TerminalMsg.Focused(focused)), frameStats::drawn);
    private final RepositorySession session;
    // Quitting is up to the model, which may refuse; the runner stops on what the update returns.
    private final Consumer<Msg> dispatch = this::send;
    // Asks the user what ssh asks, in a popup of the app's own (ADR 150).
    private final PopupAsking asking =
            new PopupAsking(action -> runner().runOnRenderThread(action), dispatch, terminal::pasteAsKeys);
    private final Popups popups = new Popups(dispatch, List.of(SshPromptDialog.slot(asking)));
    private final ContentPanel contentPanel;
    private final FocusKeeper focus;
    private final LeftColumn leftColumn;
    private final CommandLogPanel commandLogPanel;
    // Whether the command log takes the focus once the popup is closed: it had the focus when the popup opened, or the
    // help asked for it (ADR 129). A popup gives the focus back to the pane the model knows, which the log is not.
    // Only touched on the render thread.
    private boolean logAfterPopup;
    // The list the files of a commit are of, which takes the focus once the help that Escape was chosen from closed.
    private Option<PaneId> listAfterPopup = Option.none();
    // A step of J or K whose file is still to be shown, from what the content showed before it: one whose changes the
    // diff filters hide all of is stepped past once it shows (ADR 144).
    private Option<Stepping> stepping = Option.none();
    // The borders of the left column and of the panes in it, which the user drags.
    private final BorderDrag borderDrag = new BorderDrag(dispatch);
    // Whether a Redraw is queued that no frame has been drawn since; only touched on the render thread.
    private boolean redrawPending;
    // The left column as the frame before drew it, maybe on its way to the width chosen (ADR 81), its panes, maybe on
    // their way to the heights chosen (ADR 92), and whether a frame that moves them on is asked for; only touched on
    // the render thread.
    private Option<ColumnSlide> leftSlide = Option.none();
    private Option<PaneSlide> paneSlide = Option.none();
    // The command log as the frame before drew it, maybe on its way to its height, which the left column collapsing
    // shrinks; only touched on the render thread.
    private Option<CommandLogSlide> logSlide = Option.none();
    private boolean slideFramePending;
    // The one wake for when a time the screen shows changes next (ADR 124): it tells the model the time, and draws.
    private final ShownTimeWake shownTimeWake = new ShownTimeWake(
            (action, delay) -> {
                ToolkitRunner.ScheduledAction scheduled =
                        runner().schedule(() -> runner().runOnRenderThread(action), delay);
                return scheduled::cancel;
            },
            () -> {
                dispatch.accept(new TimerMsg.Clock());
                requestRedraw();
            });
    // The model the wake was last worked out for: a frame of the same one, as a spinner's, cannot change it. Every
    // message makes a new model, and the wake dispatches one; only touched on the render thread.
    private Model clocked;

    /**
     * @param env           what the repository the app starts in is read with; it opens other repositories in
     *                      the app
     * @param settings      the settings the session starts with, as {@code env} loaded them
     * @param themeOverride the theme for this session instead of the saved one, if any
     */
    public JazygitApp(
            Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, StyleEngine styleEngine) {
        this(env, settings, themeOverride, styleEngine, Option.none());
    }

    /**
     * The app as {@link #JazygitApp(Env, SettingsStore.Loaded, Option, StyleEngine)}, where ssh asks the user through
     * {@code askpass}, if any, for what the user starts (ADR 150); {@code env} runs ssh through it already.
     */
    public JazygitApp(
            Env env,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            StyleEngine styleEngine,
            Option<AskpassServer> askpass) {
        askpass.forEach(server -> server.answeredBy(asking));
        this.settings = settings;
        this.themeOverride = themeOverride;
        this.styleEngine = styleEngine;
        this.contentPanel = new ContentPanel(
                ContentSources.standard(),
                Theme.spanStyles(styleEngine),
                this::leaveScrollArea,
                area -> runner().focusManager().setFocus(area.id()),
                dispatch);
        this.focus = new FocusKeeper(this::runner, this::model, contentPanel, popups, this::requestRedraw);
        this.leftColumn = new LeftColumn(
                dispatch,
                styleEngine,
                clock,
                focus::openContent,
                this::showBranchesTab,
                highlights -> new CommitsPanel(dispatch, highlights, Features.FILLS),
                highlights -> new StashPanel(dispatch, highlights),
                Screens.standard());
        this.commandLogPanel = new CommandLogPanel(
                Theme.spanStyles(styleEngine),
                ZoneId.systemDefault(),
                id -> runner().focusManager().setFocus(id),
                this::leaveCommandLog);
        this.session = new RepositorySession(
                env,
                terminal,
                askpass.<SshAsking>map(server -> server).getOrElse(BatchSsh.INSTANCE),
                this::runner,
                clock,
                this::requestRedraw,
                this::quit,
                this::started);
    }

    /**
     * Shows the repository a program was started for, whose first model is {@code model}: the one the app starts in,
     * or one opened in its place.
     */
    private void started(Model model) {
        focus.moveToPaneOf(model);
        terminal.title(Messages.get("app.window.title", model.repositoryName()));
    }

    /**
     * Hands {@code msg} to the program of the repository shown. A key that moves the highlight of the files pane from
     * the content panel moves it here, as the highlight is the pane's, not the model's (ADR 117); run from the help,
     * the help closes first.
     */
    private void send(Msg msg) {
        switch (msg) {
            case FileStepMsg(FileStepMsg.Step step) -> stepFile(step);
            case HelpMsg.Chosen(FileStepMsg(FileStepMsg.Step step)) -> {
                session.dispatch(new PopupMsg.Dismissed(HelpPopup.class));
                stepFile(step);
            }
            // The focus is the app's, not the model's: it moves it into the command log itself (ADR 129).
            case CommandLogFocused() -> focus.moveToCommandLog();
            // Escape in the files of a commit is back to the list they were listed from, where they follow the
            // highlight; the list says which files the pane lists then (ADR 141). The model lets go of the lock that
            // kept them to that list (ADR 149).
            case CommitFilesMsg.Closed closed -> {
                model().repoView().files().backTo().forEach(focus::moveTo);
                session.dispatch(closed);
            }
            // Escape in the working tree the locked list follows lets go there too, back to that list (ADR 149);
            // Escape in the list itself keeps the focus where it is.
            case SelectionMsg.FilesUnlocked unlocked -> {
                if (focus.focus().contains(Scopes.FILES.pane())) {
                    model().repoView().files().backTo().forEach(focus::moveTo);
                }
                session.dispatch(unlocked);
            }
            // The help gives the focus back to the files as it closes, so the list takes it in the frame after.
            case HelpMsg.Chosen(CommitFilesMsg.Closed closed) -> {
                listAfterPopup = model().repoView().files().backTo();
                session.dispatch(new PopupMsg.Dismissed(HelpPopup.class));
                session.dispatch(closed);
            }
            // The help gives the focus back as it closes, so the log takes it in the frame after.
            case HelpMsg.Chosen(CommandLogFocused()) -> {
                logAfterPopup = true;
                session.dispatch(new PopupMsg.Dismissed(HelpPopup.class));
            }
            default -> session.dispatch(msg);
        }
    }

    /**
     * Moves the highlight of the files pane to its file {@code step} of the one highlighted, while the content the pane
     * shows has the focus, or had it before a popup took it; the content then shows that file, and keeps the focus. At
     * the last or the first file, the model says there is none. On the content of another pane, nothing happens.
     */
    private void stepFile(FileStepMsg.Step step) {
        Model model = model();
        if (!focus.inContentOf(model, FilesPanel.PANE)) {
            return;
        }
        List<ScrolledContent> before =
                contentPanel.filtered(model, focus.focus()).contents();
        if (leftColumn.stepFile(model, step)) {
            stepping = Option.when(
                    !model.preferences().diffFilters().active().isEmpty(), () -> new Stepping(step, before));
            requestRedraw();
        } else {
            stepping = Option.none();
            session.dispatch(new FileStepEnded(step));
        }
    }

    /**
     * Steps on past the file a step of J or K moved to, once it shows, when the diff filters hide all its changes
     * (ADR 144); the step ends once a file shows that they do not, or something else than a diff.
     */
    private void stepPastFiltered(Model model, Option<Focus> focused) {
        stepping.forEach(step -> {
            ContentView.Filtered shown = contentPanel.filtered(model, focused);
            if (shown.contents().equals(step.from())) {
                return;
            }
            stepping = Option.none();
            if (shown.hidden() == ContentView.Hidden.ALL) {
                runner().tuiRunner().runLater(() -> stepFile(step.step()));
            }
        });
    }

    /**
     * Gives the focus back to the pane whose content has it, and says whether there was one. A collapsed left column
     * opens first, so the pane is drawn (ADR 104).
     */
    private boolean leaveScrollArea() {
        Model model = model();
        if (model.preferences().leftCollapsed(model.screen()) && focus.inPaneContent()) {
            dispatch.accept(new LayoutMsg.Expanded(model.screen()));
        }
        return focus.leaveScrollArea();
    }

    /**
     * Moves the focus to the branches pane, as a click on it does, and shows the list {@code tab} in it.
     */
    private void showBranchesTab(BranchesTab tab) {
        focus.moveTo(BranchesPanel.PANE);
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
     * what it leaves unhandled reaches neither the global keys nor TamboUI's quit. Right, which no pane took, moves
     * the focus from the pane into its content.
     */
    private EventResult handleGlobalKey(Event event) {
        if (event instanceof KeyEvent && model().popup().isDefined()) {
            return EventResult.HANDLED;
        }
        if (event instanceof KeyEvent key && key.matches(Actions.MOVE_RIGHT) && focus.openFocusedContent()) {
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
     * Drags the border between the columns or between the panes of the left column, opens a collapsed left column that
     * is clicked, or else gives the focus to what a click lands on.
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
        // A click on a collapsed left column opens it (ADR 104).
        Model model = model();
        boolean leftPress = mouse.kind() == MouseEventKind.PRESS && mouse.isLeftButton();
        boolean onCollapsedLeft = model.popup().isEmpty()
                && model.preferences().leftCollapsed(model.screen())
                && borderDrag.onLeftColumn(mouse.x(), mouse.y());
        if (leftPress && onCollapsedLeft) {
            dispatch.accept(new LayoutMsg.Expanded(model.screen()));
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
        asking.withdrawAll();
        session.close();
    }

    /**
     * The model of the running program; the initial one before it runs.
     */
    Model model() {
        return session.model();
    }

    /**
     * What asks the user what ssh asks, as the server hands it questions.
     */
    PopupAsking asking() {
        return asking;
    }

    @Override
    protected Element render() {
        frameStats.rendering();
        long started = System.nanoTime();
        redrawPending = false;
        Model model = model();
        Theme.activate(styleEngine, model.preferences().theme());
        Option<Focus> focused = focus.settle(model);
        Option<PaneId> active = focus.activePane(model, focused);
        // In rows, which the panes slide through, once the height of the screen is known (ADR 92).
        Option<List<Integer>> paneRows = leftHeight().map(column -> paneRows(model, column));
        LeftColumn.Rendered rendered = leftColumn.render(model, focused, active, paneRows);
        // In columns, which the column slides through, once the width of the screen is known (ADR 81).
        Column left = Try.of(() -> runner().tuiRunner().backend().size().width())
                .toOption()
                .map(width -> rendered.column().length(leftColumns(model, width)))
                .getOrElse(() -> model.preferences().leftCollapsed(model.screen())
                        ? rendered.column().length(LeftColumn.COLLAPSED_COLUMNS)
                        : rendered.column().percent(model.preferences().leftWidth(model.screen())));
        var panes = row(
                left,
                column(contentPanel.render(model, focused).fill(), commandLog(model, focused))
                        .fill());
        // The fetch line keeps its row under the panes, also when nothing is fetched, so they do not move.
        Element layout = column(
                panes.fill(),
                FetchLine.render(model, clock.instant(), screenWidth()).length(FetchLine.HEIGHT));
        borderDrag.shown(panes, left, rendered.panes());
        // The framework knows no focus before the first render.
        Popups.Rendered shown = popups.render(model, focused);
        report(reports());
        stepPastFiltered(model, focused);
        // A popup opening while the log has the focus gives it back to the log once it closes.
        if (model.popup().isDefined() && focused.contains(CommandLogPanel.FOCUS)) {
            logAfterPopup = true;
        }
        shown.moveTo().forEach(focus::moveTo);
        if (listAfterPopup.isDefined() && model.popup().isEmpty()) {
            listAfterPopup.forEach(focus::moveTo);
            listAfterPopup = Option.none();
        }
        if (logAfterPopup && model.popup().isEmpty()) {
            logAfterPopup = false;
            focus.moveToCommandLog();
        }
        focus.redrawIfMoves(focused);
        if (model != clocked) {
            clocked = model;
            shownTimeWake.at(ShownClocks.next(model, model.now()), clock.instant());
        }
        // What is behind an open popup is dimmed, so the popup stands out, unless the popup previews its choices there.
        Element behind = shown.dimsBehind() ? new Dimmed(layout, Theme.backdrop(styleEngine)) : layout;
        Element screen = shown.dialog().<Element>map(d -> stack(behind, d)).getOrElse(layout);
        frameStats.rendered(started);
        return new SelectedTextColoured(screen, Theme.selectedText(styleEngine));
    }

    /**
     * What the widgets know that the model, which they were last rendered from, does not: the item highlighted in each
     * pane, which the content panel shows the preview of, and where the focus is outside the popups, which a closing
     * popup gives it back to. Empty once the model agrees with them. Called on the render thread.
     */
    List<Msg> reports() {
        Model model = model();
        Option<Focus> focused = focus.focus();
        return leftColumn
                .selectionChanges(model, focus.activePane(model, focused))
                .appendAll(focus.report(model, focused))
                .appendAll(leftHeight()
                        .filter(rows -> !model.preferences().columnRows().contains(rows))
                        .map(LayoutMsg.Measured::new));
    }

    /**
     * The rows of the left column, which is the screen without the fetch line; none while that is not known.
     */
    private Option<Integer> leftHeight() {
        return Try.of(() -> runner().tuiRunner().backend().size().height() - FetchLine.HEIGHT)
                .toOption();
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
     * How many columns the left column takes in a frame drawn now on a screen {@code width} columns wide, as
     * {@link ColumnSlide#next} says (ADR 81), asking for the frame that moves it on while it is on its way.
     */
    private int leftColumns(Model model, int width) {
        Instant now = clock.instant();
        ColumnSlide shown = ColumnSlide.next(
                leftSlide,
                model.screen(),
                width,
                leftColumnsChosen(model, width),
                model.preferences().settings().animations(),
                model.preferences().dragged().isDefined(),
                now);
        leftSlide = Option.of(shown);
        slideOn(shown.moving(now));
        return shown.columnsAt(now);
    }

    /**
     * How many rows each pane of the left column takes in a frame drawn now in a column {@code column} rows tall, as
     * {@link PaneSlide#next} says (ADR 92), asking for the frame that moves them on while they are on their way.
     */
    private List<Integer> paneRows(Model model, int column) {
        Instant now = clock.instant();
        Screen screen = model.screen();
        Preferences preferences = model.preferences();
        PaneSlide shown = PaneSlide.next(
                paneSlide,
                screen,
                column,
                new PaneSlide.Chosen(preferences.leftHeights(screen), preferences.maximizedPane(screen)),
                LeftPanes.shown(model, screen, column),
                preferences.settings().animations(),
                preferences.draggedHeights().isDefined(),
                now);
        paneSlide = Option.of(shown);
        slideOn(shown.moving(now));
        return shown.rowsAt(now);
    }

    /**
     * The command log under the content panel, as tall as its lines in the rows the content panel spares of the right
     * column, which is as tall as the left one, once the height of the screen is known. It is drawn for the height it
     * goes to, in the rows it takes on its way there.
     */
    private Element commandLog(Model model, Option<Focus> focus) {
        Option<CommandLogPanel.Room> room = leftHeight()
                .flatMap(column -> Try.of(
                                () -> runner().tuiRunner().backend().size().width())
                        .toOption()
                        .map(width -> new CommandLogPanel.Room(column, width - leftColumnsChosen(model, width))));
        // With the focus, it grows to show more of what it logged (ADR 129).
        // The frame a popup closes in gives the log the focus once it is drawn: it is drawn to take it, as TamboUI
        // only keeps the focus on what a frame drew to take it.
        boolean focused = focus.contains(CommandLogPanel.FOCUS)
                || (logAfterPopup && model.popup().isEmpty());
        // A popup opened from the log keeps it open behind it, where it was scrolled to, until it gives it the focus
        // back.
        boolean open = focused || logAfterPopup;
        int rows = open ? commandLogPanel.focusedHeight(model, room) : CommandLogPanel.height(model, room);
        int shown = commandLogRows(model, room.map(CommandLogPanel.Room::rows).getOrElse(0), rows);
        return commandLogPanel.render(model, rows, shown, room, open, focused);
    }

    /**
     * Gives the focus back from the command log to where it was before it had it (ADR 129).
     */
    private void leaveCommandLog() {
        focus.leaveCommandLog(model());
    }

    /**
     * How many columns the left column of the screen shown is chosen to take on a screen {@code width} columns wide,
     * where it slides to: the command log wraps its lines in the rest, which does not change while it slides.
     */
    private static int leftColumnsChosen(Model model, int width) {
        return model.preferences().leftCollapsed(model.screen())
                ? LeftColumn.COLLAPSED_COLUMNS
                : LeftBorder.columns(width, model.preferences().leftWidth(model.screen()));
    }

    /**
     * How many rows the command log takes in a frame drawn now in a column {@code column} rows tall, on its way to
     * {@code rows}, as {@link CommandLogSlide#next} says, asking for the frame that moves it on while it is on its way.
     */
    private int commandLogRows(Model model, int column, int rows) {
        Instant now = clock.instant();
        CommandLogSlide shown = CommandLogSlide.next(
                logSlide,
                model.screen(),
                column,
                rows,
                model.preferences().settings().animations(),
                now);
        logSlide = Option.of(shown);
        slideOn(shown.moving(now));
        return shown.rowsAt(now);
    }

    /**
     * Asks for a frame a moment after this one while something is still {@code moving} on its way, one at a time.
     */
    private void slideOn(boolean moving) {
        if (moving && !slideFramePending) {
            slideFramePending = true;
            runner().schedule(
                            () -> runner().runOnRenderThread(() -> {
                                slideFramePending = false;
                                requestRedraw();
                            }),
                            SLIDE_FRAME);
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

    /**
     * A step of J or K, {@code step}, taken while the content showed {@code from}.
     */
    private record Stepping(FileStepMsg.Step step, List<ScrolledContent> from) {}
}
