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
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.cli.StartupCli;
import net.vanfleteren.jazygit.cli.StartupOptions;
import net.vanfleteren.jazygit.feature.branch.BranchFeature;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.cherrypick.CherryPickDialogs;
import net.vanfleteren.jazygit.feature.commit.CommitFeature;
import net.vanfleteren.jazygit.feature.discard.DiscardDialog;
import net.vanfleteren.jazygit.feature.error.ErrorDialog;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.github.GitHubFeature;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.feature.inprogress.InProgressDialogs;
import net.vanfleteren.jazygit.feature.layout.LayoutMsg;
import net.vanfleteren.jazygit.feature.repos.RecentReposDialog;
import net.vanfleteren.jazygit.feature.settings.SettingsPanelDialogs;
import net.vanfleteren.jazygit.feature.stash.StashDialogs;
import net.vanfleteren.jazygit.feature.theme.ThemeDialog;
import net.vanfleteren.jazygit.feature.worktree.WorktreeFeature;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.gh.GhToken;
import net.vanfleteren.jazygit.git.CliGitInfoProvider;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.RepositoryIdentity;
import net.vanfleteren.jazygit.i18n.Messages;
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
    // Where the commands that threw are written down.
    private static final Path BUG_LOG = Path.of(System.getProperty("java.io.tmpdir"), "jazygit-bugs.log");
    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500);
    private static final Duration FETCH_INTERVAL = Duration.ofMinutes(1);
    // How many background commands run at once: a fetch, the pull requests and a preview, but not so many git or
    // gh processes that a burst of them slows the machine down or eats into GitHub's rate limit.
    private static final int BACKGROUND_THREADS = 4;

    private final Env env;
    private final SettingsStore.Loaded settings;
    private final Option<ThemeName> themeOverride;
    private final StyleEngine styleEngine;
    // Quitting stops the runner, which the model knows nothing of.
    private final Consumer<Msg> dispatch = msg -> {
        if (msg instanceof Msg.Quit) {
            runner().quit();
        } else {
            this.runtime.forEach(running -> running.program().dispatch(msg));
        }
    };
    private final ContentPanel contentPanel;
    private final FilesPanel filesPanel = new FilesPanel(dispatch, () -> openContent(FilesPanel.ID), this::highlight);
    private final BranchesPanel branchesPanel = new BranchesPanel(
            dispatch, this::showBranchesTab, () -> openContent(BranchesPanel.ID), Clock.systemUTC(), this::highlight);
    private final CommitsPanel commitsPanel =
            new CommitsPanel(dispatch, () -> openContent(CommitsPanel.ID), this::highlight);
    private final StashPanel stashPanel = new StashPanel(dispatch, this::highlight);
    private final List<PullRequestsPanel> pullRequestsPanels =
            PullRequestsPanel.all(dispatch, this::openContent, this::highlight);
    // Every popup, with where the focus goes when it closes. At most one is open; errors come last.
    private final List<PopupSlot> popups = List.of(
                    BranchFeature.popups(dispatch),
                    WorktreeFeature.popups(dispatch),
                    GitHubFeature.popups(dispatch),
                    CommitFeature.popups(dispatch),
                    StashDialogs.popups(dispatch),
                    CherryPickDialogs.popups(dispatch),
                    DiscardDialog.popups(dispatch),
                    ThemeDialog.popups(dispatch),
                    RecentReposDialog.popups(dispatch),
                    SettingsPanelDialogs.popups(dispatch),
                    InProgressDialogs.popups(dispatch),
                    HelpDialog.popups(dispatch),
                    ErrorDialog.popups(dispatch))
            .flatMap(Function.identity());
    // What runs while the app is started.
    private Option<Runtime> runtime = Option.none();
    // How the terminal is set up, and the backend that hands it over to an editor, once the app is started.
    private TuiConfig config = TuiConfig.defaults();
    private Option<HandOverBackend> backend = Option.none();
    // The screen the last frame showed; when the model shows another one, its pane gets the focus back.
    private Screen shownScreen = Screen.REPOSITORY;
    // The panes and the left column of the last frame, which tell where the border between them is.
    private Option<Element> shownPanes = Option.none();
    private Option<Element> shownLeft = Option.none();
    // The screen whose left column the user drags the border of, while they do.
    private Option<Screen> dragging = Option.none();

    /**
     * @param env           what the repository the app starts in is read with; it opens other repositories in
     *                      the app
     * @param settings      the settings the session starts with, as {@code env} loaded them
     * @param themeOverride the theme for this session instead of the saved one, if any
     */
    public JazygitApp(
            Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, StyleEngine styleEngine) {
        this.env = new Env(env.git(), env.gitHub(), env.settings(), env.recent(), this::switchTo, this::handOver);
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
     * What commands run against for the repository {@code git} reads: gh at the executable {@code settings} name,
     * and both gh and git as the account configured for the repository, if any.
     */
    private static Env env(
            CliGitInfoProvider git,
            Settings settings,
            SettingsStore store,
            RecentRepositories recent,
            Repositories repositories,
            Terminal terminal) {
        GhToken token = new GhToken(git.workTree(), settings.gh(), () -> GitHubAccount.configured(git));
        // Without the token, git still talks to the remotes, as the credential helper's own account.
        return new Env(
                git.withCredentials(() -> token.environment().getOrElse(HashMap.empty())),
                new CliGitHub(git.workTree(), settings.gh(), token),
                store,
                recent,
                repositories,
                terminal);
    }

    /**
     * Drops what {@code running} has queued, and stops its background commands, like a fetch, half-way; the git
     * command that runs is not interrupted, which would kill it half-way, say in the middle of a rebase.
     */
    private static void stop(Runtime running) {
        running.io().getQueue().clear();
        running.io().shutdown();
        running.background().shutdownNow();
    }

    private static ThreadPoolExecutor singleThread(String name) {
        return threads(1, Thread.ofPlatform().name(name));
    }

    /**
     * {@code size} threads named after {@code name}, taking what they cannot run yet in turn.
     */
    private static ThreadPoolExecutor threads(int size, Thread.Builder.OfPlatform name) {
        return new ThreadPoolExecutor(
                size,
                size,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                name.daemon().factory());
    }

    /**
     * Writes how a command threw to {@link #BUG_LOG}, as the terminal is the TUI's; failing that, it is lost.
     */
    private static void logBug(Throwable bug) {
        StringWriter trace = new StringWriter();
        bug.printStackTrace(new PrintWriter(trace));
        Try.run(() -> Files.writeString(
                BUG_LOG,
                Instant.now() + " " + trace + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND));
    }

    /**
     * @param args as {@link StartupCli} reads them; {@code --help} lists them
     */
    static void main(String[] args) {
        StartupCli.parse(args, new PrintWriter(System.out), new PrintWriter(System.err))
                .peekLeft(exit -> System.exit(exit.code()))
                .forEach(JazygitApp::start);
    }

    private static void start(StartupOptions options) {
        Path start = options.path().getOrElse(Path.of("")).toAbsolutePath();
        Either<String, JazygitApp> app = CliGitInfoProvider.open(start)
                .mapLeft(ErrorText::of)
                .flatMap(provider -> Theme.createEngine()
                        .mapLeft(failed ->
                                Messages.get("theme.loadFailed", failed.theme(), ErrorText.reason(failed.cause())))
                        .map(engine -> {
                            RecentRepositories recent = new RecentRepositoriesFile(
                                    SettingsPaths.stateFile(),
                                    directory ->
                                            RepositoryIdentity.read(directory).map(RepositoryIdentity::commonDir));
                            recent.opened(provider.workTree()).onFailure(JazygitApp::logBug);
                            SettingsStore settings = new SettingsFile(SettingsPaths.configFile());
                            SettingsStore.Loaded loaded = settings.load();
                            Env env = env(
                                    provider, loaded.settings(), settings, recent, Repositories.NONE, Terminal.NONE);
                            return new JazygitApp(env, loaded, options.theme(), engine);
                        }));
        app.flatMap(jazygit -> Try.run(jazygit::run).toEither().mapLeft(ErrorText::reason))
                .orElseRun(problem -> {
                    System.err.println(problem);
                    System.exit(1);
                });
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
        if (event.kind() == MouseEventKind.PRESS
                && event.isLeftButton()
                && popups.forAll(slot -> !slot.view().ids().contains(focused))) {
            runner().focusManager().focusAt(event.x(), event.y());
        }
    }

    /**
     * Drags the border between the left column and the content panel: a left press on it starts, unless a popup is
     * open, and the release lets go. A press lets go too, for a release the terminal did not report. The press and
     * the release are handled, so the press does not move the focus and the release draws the width kept.
     *
     * <p>A move while dragging is not handled, though it resizes: TamboUI draws a frame after every event that is
     * handled, and the terminal reports a move for every cell the mouse crosses, which would draw every width in
     * between, lagging behind the mouse. Not handled, the moves are only drawn on the next tick, the last one first.
     */
    private EventResult dragBorder(MouseEvent event) {
        if (dragging.isDefined() && event.kind() != MouseEventKind.PRESS) {
            Screen screen = dragging.get();
            if (event.kind() == MouseEventKind.DRAG || event.kind() == MouseEventKind.RELEASE) {
                leftWidth(event.x())
                        .filter(width -> width != model().preferences().leftWidth(screen))
                        .forEach(width -> dispatch.accept(new LayoutMsg.Resized(screen, width)));
            }
            if (event.kind() == MouseEventKind.RELEASE) {
                settle();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }
        settle();
        if (event.kind() == MouseEventKind.PRESS
                && event.isLeftButton()
                && model().popup().isEmpty()
                && onBorder(event.x(), event.y())) {
            dragging = Option.of(model().screen());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Lets go of the border that is dragged, if one is.
     */
    private void settle() {
        dragging.forEach(screen -> dispatch.accept(new LayoutMsg.Settled(screen)));
        dragging = Option.none();
    }

    /**
     * Whether {@code (x, y)} is on the border between the left column and the content panel, as the last frame drew
     * them.
     */
    private boolean onBorder(int x, int y) {
        return shownPanes
                .flatMap(panes -> Option.of(panes.renderedArea()))
                .exists(panes -> shownLeft
                        .flatMap(left -> Option.of(left.renderedArea()))
                        .exists(left -> LeftBorder.contains(panes, left, x, y)));
    }

    /**
     * How wide the left column is, in percent of the width of the panes the last frame drew, with its right edge at
     * column {@code x}.
     */
    private Option<Integer> leftWidth(int x) {
        return shownPanes
                .flatMap(panes -> Option.of(panes.renderedArea()))
                .flatMap(panes -> LeftBorder.width(panes, x));
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

    @Override
    protected void onStart() {
        ToolkitRunner runner = runner();
        runner.styleEngine(styleEngine);
        runner.eventRouter()
                .addGlobalHandler(event -> Option.of(event)
                        .filter(KeyEvent.class::isInstance)
                        .flatMap(key -> Keymap.standard().handleGlobal((KeyEvent) key))
                        .map(msg -> {
                            dispatch.accept(msg);
                            return EventResult.HANDLED;
                        })
                        .getOrElse(EventResult.UNHANDLED));
        runner.eventRouter().addGlobalHandler(event -> {
            if (event instanceof MouseEvent mouse) {
                if (dragBorder(mouse).isHandled()) {
                    return EventResult.HANDLED;
                }
                focusClicked(mouse);
            }
            return EventResult.UNHANDLED;
        });
        startRuntime(env, settings, themeOverride, Landing.START);
        runner.scheduleRepeating(
                () -> runner.runOnRenderThread(() -> dispatch.accept(new Msg.Tick(Instant.now()))), REFRESH_INTERVAL);
        runner.scheduleRepeating(
                () -> runner.runOnRenderThread(() -> dispatch.accept(new FetchMsg.Tick())), FETCH_INTERVAL);
        runner.scheduleRepeating(
                () -> runner.runOnRenderThread(() -> dispatch.accept(new PrOverviewMsg.Tick())), FETCH_INTERVAL);
    }

    /**
     * Starts showing the repository {@code env} reads, from scratch, where {@code landing} says.
     */
    private void startRuntime(
            Env env, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, Landing landing) {
        ToolkitRunner runner = runner();
        ThreadPoolExecutor io = singleThread("git-io");
        ThreadPoolExecutor background =
                threads(BACKGROUND_THREADS, Thread.ofPlatform().name("git-background-", 1));
        Program program = Program.start(
                env, settings, themeOverride, landing, io, background, runner::runOnRenderThread, JazygitApp::logBug);
        runtime = Option.of(new Runtime(io, background, program));
        runner.focusManager().setFocus(PopupSlot.focusedPane(program.model()));
        // The model tells the time from the ticks; the first one is half a second off, and the ages it shows are
        // told from the start. The loads it would start are running already, so it starts none.
        dispatch.accept(new Msg.Tick(Instant.now()));
        // The first fetch is a while off; the pull requests show from the start.
        dispatch.accept(new PullRequestsMsg.Refresh());
    }

    /**
     * Opens the repository containing {@code directory} in place of the one shown, as {@link Repositories} asks;
     * called on the thread of a command.
     */
    private Either<GitError, Path> switchTo(Path directory, Landing landing) {
        return CliGitInfoProvider.open(directory)
                .peek(provider -> runner().runOnRenderThread(() -> show(provider, landing)))
                .map(CliGitInfoProvider::workTree);
    }

    /**
     * Shows the repository {@code git} reads instead of the one shown, as if the app had been started there, where
     * {@code landing} says; the theme and the settings stay as they are in this session.
     */
    private void show(CliGitInfoProvider git, Landing landing) {
        runtime.forEach(running -> {
            Model last = running.program().model();
            // Not waiting for the command that runs keeps the UI going. It finishes by itself, in the other
            // repository, and its outcome goes to the old program, which nobody looks at anymore.
            stop(running);
            startRuntime(
                    env(
                            git,
                            last.preferences().settings(),
                            env.settings(),
                            env.recent(),
                            env.repositories(),
                            env.terminal()),
                    new SettingsStore.Loaded(last.preferences().settings(), Option.none()),
                    last.preferences().themeOverride(),
                    landing);
        });
    }

    /**
     * Runs {@code program} with the terminal handed over to it, as {@link Terminal} asks; called on the thread of a
     * command. The app keeps running meanwhile, but draws nothing and reads no keys, and is drawn anew afterwards.
     */
    private <T> T handOver(Supplier<T> program) {
        return backend.fold(program, handing -> {
            onRenderThread(() -> Try.run(() -> handing.leave(config)).get());
            try {
                return program.get();
            } finally {
                onRenderThread(() -> {
                    Try.run(() -> handing.comeBack(config)).get();
                    runner().tuiRunner().terminal().clear();
                });
            }
        });
    }

    /**
     * Runs {@code action} on the render thread, between two frames, and waits for it.
     */
    private void onRenderThread(Runnable action) {
        CompletableFuture.runAsync(action, runner()::runOnRenderThread).join();
    }

    @Override
    protected TuiConfig configure() {
        // Without a backend of its own, the app still runs, but cannot hand the terminal over.
        backend = Try.of(HandOverBackend::system).onFailure(JazygitApp::logBug).toOption();
        TuiConfig.Builder builder = TuiConfig.builder().mouseCapture(true);
        config = backend.map(builder::backend).getOrElse(builder).build();
        return config;
    }

    @Override
    protected void onStop() {
        runtime.forEach(running -> {
            // The git command that runs is given a moment to finish, and else finishes by itself once the app is
            // gone.
            stop(running);
            Try.of(() -> running.io().awaitTermination(2, TimeUnit.SECONDS))
                    .onFailure(
                            InterruptedException.class,
                            e -> Thread.currentThread().interrupt());
        });
        runtime = Option.none();
    }

    /**
     * The model of the running program; the initial one before it runs.
     */
    Model model() {
        return runtime.map(running -> running.program().model())
                .getOrElse(() -> Model.initial(env.git().repositoryName()));
    }

    @Override
    protected Element render() {
        Model model = model();
        Theme.activate(styleEngine, model.preferences().theme());
        if (model.screen() != shownScreen) {
            shownScreen = model.screen();
            runner().focusManager().setFocus(PopupSlot.focusedPane(model));
        }
        String focusedId = runner().focusManager().focusedId();
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
        Element layout = column(panes.fill(), FetchLine.render(model).length(FetchLine.HEIGHT));
        shownPanes = Option.of(panes);
        shownLeft = Option.of(left);
        // The open popup takes the focus, and gives it back to where it was opened from.
        List<Shown> shown = popups.map(slot -> new Shown(
                slot.view().render(model), slot.view().ids(), slot.returnTo().apply(model)));
        // The framework knows no focus before the first render.
        Option<String> focus = Option.of(focusedId);
        report(reports());
        Option<Shown> open = shown.find(p -> p.element().isDefined());
        open.fold(
                        // A popup that just closed gives the focus back.
                        () -> shown.find(p -> focus.exists(p.ids()::contains)).map(Shown::returnTo),
                        // A popup that just opened takes the focus.
                        p -> focus.exists(p.ids()::contains)
                                ? Option.<String>none()
                                : p.ids().headOption())
                .forEach(runner().focusManager()::setFocus);
        // What is behind an open popup is dimmed, so the popup stands out.
        Element screen = open.flatMap(Shown::element)
                .<Element>map(d -> stack(new Dimmed(layout, Theme.backdrop(styleEngine)), d))
                .getOrElse(layout);
        return new SelectedTextColoured(screen, Theme.selectedText(styleEngine));
    }

    /**
     * The left column of the repository screen: the status, the files, the branches, the commits and the stashes.
     */
    private Column repositoryPanes(Model model, String focusedId, String activeId) {
        Panel branches = branchesPanel.render(model, focusedId, activeId);
        Panel stash = stashPanel.render(model, focusedId, activeId);
        Option<Integer> stashHeight = stashHeight(model);
        return column(
                StatusPanel.render(model, this::showScreen),
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
     * The left column of the pull requests screen: the status, the pull requests that ask for the user's review,
     * the ones they opened, and the ones of the repository, sharing the height.
     */
    private Column pullRequestPanes(Model model, String focusedId, String activeId) {
        List<Panel> panes = pullRequestsPanels.map(
                pane -> pane.render(model, focusedId, activeId).fill());
        return column(panes.<Element>map(pane -> pane)
                .prepend(StatusPanel.render(model, this::showScreen))
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
                                filesPanel.selectionChange(model))
                        .flatMap(change -> change);
        // The framework knows no focus before the first render.
        Option<Msg> focused = Option.of(focusedId)
                .filter(id -> popups.forAll(slot -> !slot.view().ids().contains(id)))
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
     * The program and the threads it runs git on, which exist from the start of the app to its stop, or until
     * another repository is opened in its place.
     *
     * @param io         where git is called
     * @param background where the background commands run, like talking to the remotes and to GitHub
     */
    private record Runtime(ThreadPoolExecutor io, ThreadPoolExecutor background, Program program) {}

    /**
     * A popup, and the pane that gets the focus back when it closes.
     */

    /**
     * A popup as rendered: its dialog while it is open.
     */
    private record Shown(Option<Element> element, List<String> ids, String returnTo) {}
}
