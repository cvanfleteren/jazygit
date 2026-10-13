package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import dev.tamboui.css.engine.StyleEngine;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.branch.BranchDialogs;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.feature.commit.CommitDialogs;
import net.vanfleteren.jazygit.feature.discard.DiscardDialog;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.github.PullRequestDialog;
import net.vanfleteren.jazygit.feature.github.PullRequestDialogs;
import net.vanfleteren.jazygit.feature.github.PullRequestMsg;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.feature.error.ErrorDialog;
import net.vanfleteren.jazygit.feature.help.HelpPopup;
import net.vanfleteren.jazygit.feature.stash.StashDialogs;
import net.vanfleteren.jazygit.feature.theme.ThemeDialog;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.git.CliGitInfoProvider;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Program;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.CommandLogPanel;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.ContentPanel;
import net.vanfleteren.jazygit.ui.FetchLine;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.StashPanel;
import net.vanfleteren.jazygit.ui.StatusPanel;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PopupView;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500);
    private static final Duration FETCH_INTERVAL = Duration.ofMinutes(1);

    private final Env env;
    private final StyleEngine styleEngine;
    private final Consumer<Msg> dispatch = msg -> this.runtime.forEach(running -> running.program().dispatch(msg));
    private final ContentPanel contentPanel;
    private final FilesPanel filesPanel = new FilesPanel(dispatch, () -> openContent(FilesPanel.ID));
    private final BranchesPanel branchesPanel = new BranchesPanel(dispatch);
    private final CommitsPanel commitsPanel = new CommitsPanel(dispatch, () -> openContent(CommitsPanel.ID));
    private final StashPanel stashPanel = new StashPanel(dispatch);
    // Every popup, with where the focus goes when it closes. At most one is open.
    private final List<PopupSlot> popups = List.of(
            new PopupSlot(new TextInputDialog<>(BranchDialogs.NEW_BRANCH, dispatch), () -> this.lastFocus),
            new PopupSlot(new ChoiceDialog<>(DiscardDialog.SPEC, dispatch), () -> FilesPanel.ID),
            new PopupSlot(new ChoiceDialog<>(ThemeDialog.SPEC, dispatch), () -> this.lastFocus),
            new PopupSlot(new ChoiceDialog<>(BranchDialogs.REBASE, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new ChoiceDialog<>(BranchDialogs.MERGE, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new ChoiceDialog<>(BranchDialogs.DELETE_BRANCH, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(BranchDialogs.FORCE_PUSH, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new TextInputDialog<>(BranchDialogs.SET_UPSTREAM, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(BranchDialogs.STASH_CHECKOUT, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new PickDialog<>(PullRequestDialogs.BASE, dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new PullRequestDialog(dispatch), () -> BranchesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.AMEND, dispatch), () -> FilesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(StashDialogs.DROP, dispatch), () -> StashPanel.ID),
            new PopupSlot(new TextInputDialog<>(StashDialogs.STASH, dispatch), () -> FilesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.STAGE_ALL, dispatch), () -> FilesPanel.ID),
            new PopupSlot(new CommitDialog(dispatch), () -> FilesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.SQUASH, dispatch), () -> CommitsPanel.ID),
            new PopupSlot(CommitDialog.reword(dispatch), () -> CommitsPanel.ID),
            new PopupSlot(new HelpDialog(dispatch), () -> this.helpOrigin),
            // Errors come up wherever the user is.
            new PopupSlot(new ConfirmDialog<>(ErrorDialog.SPEC, dispatch), () -> this.lastFocus));
    // The panel the help popup was opened from, which gets the focus back.
    private String helpOrigin = FilesPanel.ID;
    // Where the focus was when no popup had it, which the theme popup, opened from anywhere, gives it back to.
    private String lastFocus = FilesPanel.ID;
    // What runs while the app is started.
    private Option<Runtime> runtime = Option.none();

    /**
     * The program and the threads it runs git on, which exist from the start of the app to its stop.
     *
     * @param io    where git is called
     * @param fetch where git talks to the remotes
     */
    private record Runtime(ExecutorService io, ExecutorService fetch, Program program) {
    }

    public JazygitApp(Env env, StyleEngine styleEngine) {
        this.env = env;
        this.styleEngine = styleEngine;
        this.contentPanel = new ContentPanel(Theme.spanStyles(styleEngine), this::leaveScrollArea);
    }

    /**
     * Moves the focus to the content shown for the pane {@code paneId}, so it can be scrolled by key.
     */
    private void openContent(String paneId) {
        contentPanel.areaOfPane(paneId).forEach(id -> runner().focusManager().setFocus(id));
    }

    /**
     * Gives the focus back to the pane whose content has it, and says whether there was one.
     */
    private boolean leaveScrollArea() {
        Option<String> pane = contentPanel.paneOfArea(runner().focusManager().focusedId());
        pane.forEach(runner().focusManager()::setFocus);
        return pane.isDefined();
    }

    @Override
    protected void onStart() {
        ToolkitRunner runner = runner();
        runner.styleEngine(styleEngine);
        runner.eventRouter().addGlobalHandler(event -> Option.of(event)
                .filter(KeyEvent.class::isInstance)
                .flatMap(key -> Keymap.standard().handleGlobal((KeyEvent) key))
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED));
        runner.focusManager().setFocus(FilesPanel.ID);
        ExecutorService io = singleThread("git-io");
        ExecutorService fetch = singleThread("git-fetch");
        runtime = Option.of(new Runtime(io, fetch, Program.start(env, io, fetch, runner::runOnRenderThread)));
        runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> dispatch.accept(new Msg.Tick())),
                REFRESH_INTERVAL);
        runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> dispatch.accept(new FetchMsg.Tick())),
                FETCH_INTERVAL);
        // The first fetch is a while off; the pull requests show from the start.
        dispatch.accept(new PullRequestMsg.Refresh());
    }

    private static ExecutorService singleThread(String name) {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name(name).daemon().factory());
    }


    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder().mouseCapture(true).fpsOverlay(true).build();
    }

    @Override
    protected void onStop() {
        runtime.forEach(running -> {
            running.io().shutdownNow();
            running.fetch().shutdownNow();
            // Let a running git call finish before the process exits; a fetch is not waited for.
            Try.of(() -> running.io().awaitTermination(2, TimeUnit.SECONDS))
                    .onFailure(InterruptedException.class, e -> Thread.currentThread().interrupt());
        });
        runtime = Option.none();
    }

    @Override
    protected Element render() {
        Model model = runtime.map(running -> running.program().model())
                .getOrElse(() -> Model.initial(env.git().repositoryName()));
        Theme.activate(styleEngine, model.theme());
        String focusedId = runner().focusManager().focusedId();
        Panel branches = branchesPanel.render(model, focusedId);
        // The highlighted branch lives in the list widget, which knows it once rendered; the
        // content panel then shows the log of that branch.
        branchesPanel.selectionChange(model).forEach(dispatch);
        commitsPanel.selectionChange(model).forEach(dispatch);
        filesPanel.selectionChange(model).forEach(dispatch);
        Panel stash = stashPanel.render(model, focusedId);
        Option<Integer> stashHeight = stashHeight(model);
        var panes = row(
                column(
                        StatusPanel.render(model),
                        filesPanel.render(model, focusedId).percent(FILES_PERCENT),
                        branches.percent(BRANCHES_PERCENT),
                        // With the stash sized to its content, the commits take what is left, which is
                        // their share when the stash is as tall as it may get.
                        stashHeight.isDefined()
                                ? commitsPanel.render(model, focusedId).fill()
                                : commitsPanel.render(model, focusedId).percent(COMMITS_PERCENT),
                        stashHeight.map(stash::length).getOrElse(stash::fill))
                        .percent(30),
                column(
                        contentPanel.render(model, focusedId)
                                .fill(),
                        CommandLogPanel.render(model).length(COMMAND_LOG_HEIGHT))
                        .fill());
        // The fetch line keeps its row under the panes, also when nothing is fetched, so they do not move.
        Element layout = column(panes.fill(), FetchLine.render(model).length(FetchLine.HEIGHT));
        // The open popup takes the focus, and gives it back to the pane it was opened from.
        model.popup(HelpPopup.class).map(HelpPopup::scopeId).forEach(scopeId -> helpOrigin = scopeId);
        List<Shown> shown = popups.map(slot -> new Shown(slot.view().render(model), slot.view().ids(),
                slot.returnTo().get()));
        // The framework knows no focus before the first render.
        Option<String> focus = Option.of(focusedId);
        focus.filter(id -> shown.forAll(p -> !p.ids().contains(id))).forEach(id -> lastFocus = id);
        Option<Shown> open = shown.find(p -> p.element().isDefined());
        open.fold(
                        // A popup that just closed gives the focus back.
                        () -> shown.find(p -> focus.exists(p.ids()::contains)).map(Shown::returnTo),
                        // A popup that just opened takes the focus.
                        p -> focus.exists(p.ids()::contains) ? Option.<String>none() : p.ids().headOption())
                .forEach(runner().focusManager()::setFocus);
        return open.flatMap(Shown::element).<Element>map(d -> stack(layout, d)).getOrElse(layout);
    }

    /**
     * The height of the stash pane: just its rows, but at most the share of the left column that is
     * left once the other panes have theirs, and at least an empty pane (a blank line in a border).
     * Empty when the terminal size is not known.
     */
    private Option<Integer> stashHeight(Model model) {
        int rows = Math.max(1, model.stashes().getOrElse(List.empty()).size());
        // The left column is the screen without the fetch line.
        return Try.of(() -> runner().tuiRunner().backend().size().height() - FetchLine.HEIGHT)
                .toOption()
                .map(height -> height - StatusPanel.HEIGHT
                        - height * (FILES_PERCENT + BRANCHES_PERCENT + COMMITS_PERCENT) / 100)
                .map(share -> Math.max(MIN_STASH_HEIGHT, Math.min(rows + 2, share)));
    }

    /**
     * A popup, and the pane that gets the focus back when it closes.
     */
    private record PopupSlot(PopupView view, Supplier<String> returnTo) {
    }

    /**
     * A popup as rendered: its dialog while it is open.
     */
    private record Shown(Option<Element> element, List<String> ids, String returnTo) {
    }

    /**
     * @param args optional path inside the repository to open; defaults to the current directory
     */
    public static void main(String[] args) {
        Path start = List.of(args).headOption().map(Path::of).getOrElse(Path.of("")).toAbsolutePath();
        Either<String, JazygitApp> app = CliGitInfoProvider.open(start)
                .mapLeft(ErrorText::of)
                .flatMap(provider -> Theme.createEngine()
                        .mapLeft(failed -> Messages.get("theme.loadFailed", failed.theme(), failed.cause()))
                        .map(engine -> new JazygitApp(new Env(provider, new CliGitHub(provider.workTree())), engine)));
        app.flatMap(jazygit -> Try.run(jazygit::run).toEither().mapLeft(Throwable::toString))
                .orElseRun(problem -> {
                    System.err.println(problem);
                    System.exit(1);
                });
    }
}
