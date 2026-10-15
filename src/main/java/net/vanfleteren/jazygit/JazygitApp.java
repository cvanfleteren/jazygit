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
import dev.tamboui.style.Style;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.branch.BranchDialogs;
import net.vanfleteren.jazygit.feature.branch.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.feature.commit.CommitDialogs;
import net.vanfleteren.jazygit.feature.discard.DiscardDialog;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.github.PullRequestDialog;
import net.vanfleteren.jazygit.feature.github.PullRequestDialogs;
import net.vanfleteren.jazygit.state.PullRequestsMsg;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.feature.error.ErrorDialog;
import net.vanfleteren.jazygit.feature.stash.StashDialogs;
import net.vanfleteren.jazygit.feature.theme.ThemeDialog;
import net.vanfleteren.jazygit.feature.repos.RecentReposDialog;
import net.vanfleteren.jazygit.cli.StartupCli;
import net.vanfleteren.jazygit.cli.StartupOptions;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.RecentRepositoriesFile;
import net.vanfleteren.jazygit.settings.SettingsFile;
import net.vanfleteren.jazygit.settings.SettingsPaths;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.git.CliGitInfoProvider;
import net.vanfleteren.jazygit.git.GitError;
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
import net.vanfleteren.jazygit.ui.widgets.Dimmed;
import net.vanfleteren.jazygit.ui.widgets.PopupView;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

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
    // Where the commands that threw are written down.
    private static final Path BUG_LOG = Path.of(System.getProperty("java.io.tmpdir"), "jazygit-bugs.log");
    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500);
    private static final Duration FETCH_INTERVAL = Duration.ofMinutes(1);

    private final Env env;
    private final SettingsStore.Loaded settings;
    private final Option<ThemeName> themeOverride;
    private final StyleEngine styleEngine;
    private final Consumer<Msg> dispatch = msg -> this.runtime.forEach(running -> running.program().dispatch(msg));
    private final ContentPanel contentPanel;
    private final FilesPanel filesPanel = new FilesPanel(dispatch, () -> openContent(FilesPanel.ID), this::highlight);
    private final BranchesPanel branchesPanel = new BranchesPanel(dispatch, this::showBranchesTab,
            Clock.systemUTC(), this::highlight);
    private final CommitsPanel commitsPanel = new CommitsPanel(dispatch, () -> openContent(CommitsPanel.ID),
            this::highlight);
    private final StashPanel stashPanel = new StashPanel(dispatch, this::highlight);
    // Every popup, with where the focus goes when it closes. At most one is open.
    private final List<PopupSlot> popups = List.of(
            new PopupSlot(new TextInputDialog<>(BranchDialogs.NEW_BRANCH, dispatch), JazygitApp::focusedPane),
            new PopupSlot(new ChoiceDialog<>(DiscardDialog.SPEC, dispatch), model -> FilesPanel.ID),
            new PopupSlot(new ChoiceDialog<>(ThemeDialog.SPEC, dispatch), JazygitApp::focusedPane),
            new PopupSlot(new PickDialog<>(RecentReposDialog.SPEC, dispatch), JazygitApp::focusedPane),
            new PopupSlot(new ChoiceDialog<>(BranchDialogs.REBASE, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new ChoiceDialog<>(BranchDialogs.MERGE, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new ChoiceDialog<>(BranchDialogs.DELETE_BRANCH, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(BranchDialogs.FORCE_PUSH, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new TextInputDialog<>(BranchDialogs.SET_UPSTREAM, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(BranchDialogs.STASH_CHECKOUT, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new PickDialog<>(PullRequestDialogs.BASE, dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new PullRequestDialog(dispatch), model -> BranchesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.AMEND, dispatch), model -> FilesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(StashDialogs.DROP, dispatch), model -> StashPanel.ID),
            new PopupSlot(new TextInputDialog<>(StashDialogs.STASH, dispatch), model -> FilesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.STAGE_ALL, dispatch), model -> FilesPanel.ID),
            new PopupSlot(new CommitDialog(dispatch), model -> FilesPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.SQUASH, dispatch), model -> CommitsPanel.ID),
            new PopupSlot(new ChoiceDialog<>(CommitDialogs.FIXUP, dispatch), model -> CommitsPanel.ID),
            new PopupSlot(new ChoiceDialog<>(CommitDialogs.RESET, dispatch), model -> CommitsPanel.ID),
            new PopupSlot(new ConfirmDialog<>(CommitDialogs.HARD_RESET, dispatch), model -> CommitsPanel.ID),
            new PopupSlot(CommitDialog.reword(dispatch), model -> CommitsPanel.ID),
            // Help is opened from a pane, which has the focus then.
            new PopupSlot(new HelpDialog(dispatch), JazygitApp::focusedPane),
            // Errors come up wherever the user is.
            new PopupSlot(new ConfirmDialog<>(ErrorDialog.SPEC, dispatch), JazygitApp::focusedPane));
    // What runs while the app is started.
    private Option<Runtime> runtime = Option.none();

    /**
     * The program and the threads it runs git on, which exist from the start of the app to its stop, or until
     * another repository is opened in its place.
     *
     * @param io    where git is called
     * @param fetch where git talks to the remotes
     */
    private record Runtime(ThreadPoolExecutor io, ThreadPoolExecutor fetch, Program program) {
    }

    /**
     * @param env           what the repository the app starts in is read with; it opens other repositories in
     *                      the app
     * @param settings      the settings the session starts with, as {@code env} loaded them
     * @param themeOverride the theme for this session instead of the saved one, if any
     */
    public JazygitApp(Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, StyleEngine styleEngine) {
        this.env = new Env(env.git(), env.gitHub(), env.settings(), env.recent(), this::switchTo);
        this.settings = settings;
        this.themeOverride = themeOverride;
        this.styleEngine = styleEngine;
        this.contentPanel = new ContentPanel(Theme.spanStyles(styleEngine), this::leaveScrollArea,
                id -> runner().focusManager().setFocus(id));
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
                .filter(id -> popups.forAll(slot -> !slot.view().ids().contains(id)))
                .orElse(model.focusedPane())
                .map(id -> contentPanel.paneOfArea(id).getOrElse(id))
                .getOrNull();
    }

    /**
     * Moves the focus to the content shown for the pane {@code paneId}, so it can be scrolled by key.
     */
    private void openContent(String paneId) {
        contentPanel.areaOfPane(paneId).forEach(id -> runner().focusManager().setFocus(id));
    }

    /**
     * Moves the focus to the branches pane, as a click on it does, and shows the list {@code tab} in it.
     */
    private void showBranchesTab(BranchesTab tab) {
        runner().focusManager().setFocus(BranchesPanel.ID);
        dispatch.accept(new BranchesTabMsg.Chosen(tab));
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
        startRuntime(env, settings, themeOverride);
        runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> dispatch.accept(new Msg.Tick())),
                REFRESH_INTERVAL);
        runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> dispatch.accept(new FetchMsg.Tick())),
                FETCH_INTERVAL);
    }

    /**
     * Starts showing the repository {@code env} reads, from scratch.
     */
    private void startRuntime(Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride) {
        ToolkitRunner runner = runner();
        runner.focusManager().setFocus(FilesPanel.ID);
        ThreadPoolExecutor io = singleThread("git-io");
        ThreadPoolExecutor fetch = singleThread("git-fetch");
        runtime = Option.of(new Runtime(io, fetch, Program.start(env, settings, themeOverride, io, fetch, runner::runOnRenderThread, JazygitApp::logBug)));
        // The first fetch is a while off; the pull requests show from the start.
        dispatch.accept(new PullRequestsMsg.Refresh());
    }

    /**
     * Opens the repository containing {@code directory} in place of the one shown, as {@link Repositories} asks;
     * called on the thread of a command.
     */
    private Either<GitError.OpenError, Path> switchTo(Path directory) {
        return CliGitInfoProvider.open(directory)
                .peek(provider -> runner().runOnRenderThread(() -> show(provider)))
                .map(CliGitInfoProvider::workTree);
    }

    /**
     * Shows the repository {@code git} reads instead of the one shown, as if the app had been started there; the
     * theme and the settings stay as they are in this session.
     */
    private void show(CliGitInfoProvider git) {
        runtime.forEach(running -> {
            Model last = running.program().model();
            // Not waiting for the command that runs keeps the UI going. It finishes by itself, in the other
            // repository, and its outcome goes to the old program, which nobody looks at anymore.
            stop(running);
            startRuntime(new Env(git, new CliGitHub(git.workTree()), env.settings(), env.recent(), env.repositories()),
                    new SettingsStore.Loaded(last.settings(), Option.none()), last.themeOverride());
        });
    }

    /**
     * Drops what {@code running} has queued, and stops its fetch half-way; the git command that runs is not
     * interrupted, which would kill it half-way, say in the middle of a rebase.
     */
    private static void stop(Runtime running) {
        running.io().getQueue().clear();
        running.io().shutdown();
        running.fetch().shutdownNow();
    }

    private static ThreadPoolExecutor singleThread(String name) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                Thread.ofPlatform().name(name).daemon().factory());
    }

    /**
     * Writes how a command threw to {@link #BUG_LOG}, as the terminal is the TUI's; failing that, it is lost.
     */
    private static void logBug(Throwable bug) {
        StringWriter trace = new StringWriter();
        bug.printStackTrace(new PrintWriter(trace));
        Try.run(() -> Files.writeString(BUG_LOG, Instant.now() + " " + trace + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }


    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder().mouseCapture(true).build();
    }

    @Override
    protected void onStop() {
        runtime.forEach(running -> {
            // The git command that runs is given a moment to finish, and else finishes by itself once the app is
            // gone.
            stop(running);
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
        String activeId = activePane(model, focusedId);
        Panel branches = branchesPanel.render(model, focusedId, activeId);
        // The highlighted branch lives in the list widget, which knows it once rendered; the
        // content panel then shows the log of that branch.
        branchesPanel.selectionChange(model).forEach(dispatch);
        commitsPanel.selectionChange(model).forEach(dispatch);
        filesPanel.selectionChange(model).forEach(dispatch);
        Panel stash = stashPanel.render(model, focusedId, activeId);
        Option<Integer> stashHeight = stashHeight(model);
        var panes = row(
                column(
                        StatusPanel.render(model),
                        filesPanel.render(model, focusedId, activeId).percent(FILES_PERCENT),
                        branches.percent(BRANCHES_PERCENT),
                        // With the stash sized to its content, the commits take what is left, which is
                        // their share when the stash is as tall as it may get.
                        stashHeight.isDefined()
                                ? commitsPanel.render(model, focusedId, activeId).fill()
                                : commitsPanel.render(model, focusedId, activeId).percent(COMMITS_PERCENT),
                        stashHeight.map(stash::length).getOrElse(stash::fill))
                        .percent(30),
                column(
                        contentPanel.render(model, focusedId)
                                .fill(),
                        CommandLogPanel.render(model).length(COMMAND_LOG_HEIGHT))
                        .fill());
        // The fetch line keeps its row under the panes, also when nothing is fetched, so they do not move.
        Element layout = column(panes.fill(), FetchLine.render(model).length(FetchLine.HEIGHT));
        // The open popup takes the focus, and gives it back to where it was opened from.
        List<Shown> shown = popups.map(slot -> new Shown(slot.view().render(model), slot.view().ids(),
                slot.returnTo().apply(model)));
        // The framework knows no focus before the first render. Where the focus is outside the popups is kept
        // in the model, as a closing popup gives it back there.
        Option<String> focus = Option.of(focusedId);
        focus.filter(id -> shown.forAll(p -> !p.ids().contains(id)))
                .filter(id -> !model.focusedPane().contains(id))
                .map(Msg.Focused::new)
                .forEach(dispatch);
        Option<Shown> open = shown.find(p -> p.element().isDefined());
        open.fold(
                        // A popup that just closed gives the focus back.
                        () -> shown.find(p -> focus.exists(p.ids()::contains)).map(Shown::returnTo),
                        // A popup that just opened takes the focus.
                        p -> focus.exists(p.ids()::contains) ? Option.<String>none() : p.ids().headOption())
                .forEach(runner().focusManager()::setFocus);
        // What is behind an open popup is dimmed, so the popup stands out.
        return open.flatMap(Shown::element).<Element>map(d -> stack(new Dimmed(layout, Theme.backdrop(styleEngine)), d)).getOrElse(layout);
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
     * Where the focus was outside the popups, which a popup opened from anywhere gives it back to.
     */
    private static String focusedPane(Model model) {
        return model.focusedPane().getOrElse(FilesPanel.ID);
    }

    /**
     * A popup, and the pane that gets the focus back when it closes.
     */
    private record PopupSlot(PopupView view, Function<Model, String> returnTo) {
    }

    /**
     * A popup as rendered: its dialog while it is open.
     */
    private record Shown(Option<Element> element, List<String> ids, String returnTo) {
    }

    /**
     * @param args as {@link StartupCli} reads them; {@code --help} lists them
     */
    public static void main(String[] args) {
        StartupCli.parse(args, new PrintWriter(System.out), new PrintWriter(System.err))
                .peekLeft(exit -> System.exit(exit.code()))
                .forEach(JazygitApp::start);
    }

    private static void start(StartupOptions options) {
        Path start = options.path().getOrElse(Path.of("")).toAbsolutePath();
        Either<String, JazygitApp> app = CliGitInfoProvider.open(start)
                .mapLeft(ErrorText::of)
                .flatMap(provider -> Theme.createEngine()
                        .mapLeft(failed -> Messages.get("theme.loadFailed", failed.theme(), ErrorText.reason(failed.cause())))
                        .map(engine -> {
                            RecentRepositories recent = new RecentRepositoriesFile(SettingsPaths.stateFile());
                            recent.opened(provider.workTree()).onFailure(JazygitApp::logBug);
                            Env env = new Env(provider, new CliGitHub(provider.workTree()),
                                    new SettingsFile(SettingsPaths.configFile()), recent, Repositories.NONE);
                            return new JazygitApp(env, env.settings().load(), options.theme(), engine);
                        }));
        app.flatMap(jazygit -> Try.run(jazygit::run).toEither().mapLeft(ErrorText::reason))
                .orElseRun(problem -> {
                    System.err.println(problem);
                    System.exit(1);
                });
    }
}
