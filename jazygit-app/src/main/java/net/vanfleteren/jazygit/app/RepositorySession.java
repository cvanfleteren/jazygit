package net.vanfleteren.jazygit.app;

import dev.tamboui.toolkit.app.ToolkitRunner;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.core.git.CliGit;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.files.RepositoryIdentity;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.process.SshAsking;
import net.vanfleteren.jazygit.features.changes.fetch.FetchMsg;
import net.vanfleteren.jazygit.features.chrome.settings.SettingsPanelUpdate;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;

/**
 * What runs for the repository shown: the {@link Program} and the threads it runs git on, from the start of the app
 * to its stop. Opening another repository starts them anew for that one.
 */
final class RepositorySession {

    // How many background commands run at once: a fetch, the pull requests and a preview, but not so many git or
    // gh processes that a burst of them slows the machine down or eats into GitHub's rate limit.
    private static final int BACKGROUND_THREADS = 4;

    private final Env env;
    // How ssh may ask the user in each repository opened (ADR 150).
    private final SshAsking ssh;
    private final Supplier<ToolkitRunner> runner;
    private final Clock clock;
    private final Runnable requestRedraw;
    private final Runnable quit;
    private final Consumer<Model> started;
    // What runs while the app is started.
    private Option<Runtime> runtime = Option.none();

    /**
     * @param env           what the repository the app starts in is read with; other repositories are opened here
     * @param terminal      the terminal the app is shown on
     * @param ssh           how ssh may ask the user in another repository opened, as it may in {@code env}'s
     * @param runner        the runner of the app, once it is started
     * @param requestRedraw asks for a frame, on the render thread
     * @param quit          stops the app, as the model asks
     * @param started       gets the first model of each program started, on the render thread
     */
    RepositorySession(
            Env env,
            Terminal terminal,
            SshAsking ssh,
            Supplier<ToolkitRunner> runner,
            Clock clock,
            Runnable requestRedraw,
            Runnable quit,
            Consumer<Model> started) {
        this.env = env.withApp(this::switchTo, terminal);
        this.ssh = ssh;
        this.runner = runner;
        this.clock = clock;
        this.requestRedraw = requestRedraw;
        this.quit = quit;
        this.started = started;
    }

    /**
     * Drops what {@code running} has queued, and stops its background commands, like a fetch, half-way; the git
     * command that runs is not interrupted, which would kill it half-way, say in the middle of a rebase.
     */
    private static void stop(Runtime running) {
        running.program().stop();
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
     * Starts showing the repository the app starts in, as {@code settings}, {@code themeOverride} and its git
     * configuration say.
     */
    void start(SettingsStore.Loaded settings, Option<ThemeName> themeOverride) {
        open(
                env,
                settings,
                themeOverride,
                RepositoryTheme.read(env.git()),
                Landing.START,
                commonDir(env.git().worktree()),
                Carried.NONE);
    }

    /**
     * Hands {@code msg} to the running program; nothing runs before the start or after the stop.
     */
    void dispatch(Msg msg) {
        runtime.forEach(running -> running.program().dispatch(msg));
    }

    /**
     * The model of the running program; the initial one before it runs.
     */
    Model model() {
        return runtime.map(running -> running.program().model())
                .getOrElse(() -> Model.initial(env.git().repositoryName()));
    }

    /**
     * Stops what runs. The git command that runs is given a moment to finish, and else finishes by itself once the
     * app is gone.
     */
    void close() {
        runtime.forEach(running -> {
            stop(running);
            Try.of(() -> running.io().awaitTermination(2, TimeUnit.SECONDS))
                    .onFailure(
                            InterruptedException.class,
                            e -> Thread.currentThread().interrupt());
        });
        runtime = Option.none();
    }

    /**
     * The git directory the worktrees of the repository at {@code worktree} share, if it can be found.
     */
    private static Option<Path> commonDir(Path worktree) {
        return RepositoryIdentity.read(worktree).map(RepositoryIdentity::commonDir);
    }

    /**
     * Starts showing the repository {@code env} reads, whose worktrees share {@code commonDir}, in the theme its git
     * configuration gives it if no other is asked for, where {@code landing} says, with what {@code carried} keeps of
     * what the worktree shown before knew (ADR 182); from scratch when it carries nothing.
     */
    private void open(
            Env env,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            RepositoryTheme repositoryTheme,
            Landing landing,
            Option<Path> commonDir,
            Carried carried) {
        ToolkitRunner runner = this.runner.get();
        ThreadPoolExecutor io = singleThread("git-io");
        ThreadPoolExecutor background =
                threads(BACKGROUND_THREADS, Thread.ofPlatform().name("git-background-", 1));
        Program program = Program.start(
                env,
                settings,
                themeOverride,
                repositoryTheme,
                landing,
                carried,
                Features.REACTIONS,
                io,
                background,
                runner::runOnRenderThread,
                BugLog::log,
                requestRedraw,
                quit,
                clock);
        runtime = Option.of(new Runtime(io, background, program, commonDir));
        started.accept(program.model());
        // The first tick of the fetch is a while off, so it fetches now. The features read what they show of the
        // repository from the start, as they answer its opening. Another worktree of the repository shown fetches
        // into the same remote-tracking branches, which were fetched into already: it waits for the tick (ADR 182).
        if (!carried.any()) {
            program.dispatch(new FetchMsg.Tick());
        }
    }

    /**
     * Opens the repository containing {@code directory} in place of the one shown, as {@link Repositories} asks;
     * called on the thread of a command, which reads its theme too, so the render thread does not wait for git.
     */
    private Either<GitError, Path> switchTo(Path directory, Landing landing) {
        return CliGit.open(directory)
                .peek(git -> {
                    RepositoryTheme theme = RepositoryTheme.read(git);
                    Option<Path> commonDir = commonDir(git.worktree());
                    runner.get().runOnRenderThread(() -> show(git, theme, landing, commonDir));
                })
                .map(Git::worktree);
    }

    /**
     * Shows the repository {@code git} reads instead of the one shown, as if the app had been started there, where
     * {@code landing} says; the settings, and a theme asked for at startup, stay as they are in this session, and
     * {@code repositoryTheme} is the one its git configuration gives it. Another worktree of the same repository, one
     * whose worktrees share {@code commonDir} too, opens with what the worktrees share of what was known (ADR 182).
     */
    private void show(Git git, RepositoryTheme repositoryTheme, Landing landing, Option<Path> commonDir) {
        runtime.forEach(running -> {
            Model last = running.program().model();
            boolean sameRepository =
                    commonDir.isDefined() && running.commonDir().equals(commonDir);
            // Not waiting for the command that runs keeps the UI going. It finishes by itself, in the other
            // repository, and its outcome goes to the old program, which nobody looks at anymore.
            stop(running);
            open(
                    RepositoryEnv.reopen(env, git, last.preferences().settings(), ssh),
                    new SettingsStore.Loaded(
                            last.preferences().settings(),
                            Option.none(),
                            last.preferences().file().keys()),
                    last.preferences().themeOverride(),
                    repositoryTheme,
                    landing,
                    commonDir,
                    sameRepository ? Carried.from(last) : Carried.NONE);
            // What this session knew of the settings file goes on, so a setting only it has is not taken for one
            // the file has, and a problem it was showing still shows (ADR 111).
            runtime.forEach(opened -> opened.program().dispatch(SettingsPanelUpdate.carried(last)));
            requestRedraw.run();
        });
    }

    /**
     * The program and the threads it runs git on, which exist from the start of the app to its stop, or until
     * another repository is opened in its place.
     *
     * @param io         where git is called
     * @param background where the background commands run, like talking to the remotes and to GitHub
     * @param commonDir  the git directory the worktrees of the repository share, if it was found
     */
    private record Runtime(
            ThreadPoolExecutor io, ThreadPoolExecutor background, Program program, Option<Path> commonDir) {}
}
