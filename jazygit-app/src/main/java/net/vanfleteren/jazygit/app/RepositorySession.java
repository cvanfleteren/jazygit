package net.vanfleteren.jazygit.app;

import dev.tamboui.toolkit.app.ToolkitRunner;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
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
    // The screen of the repository shown before the one that runs, while that one reads what its panes show.
    private Option<HeldFrame> held = Option.none();

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
                shown(env.git().worktree()),
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
     * The model a frame drawn at {@code now} shows: that of the running program, or the screen of the repository shown
     * before it while it reads what its panes show, as {@link HeldFrame} says; that is let go once it is not held.
     */
    Model drawn(Instant now) {
        Model running = model();
        held = held.flatMap(frame -> frame.still(running, now));
        return held.map(HeldFrame::shown).getOrElse(running);
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
     * The repository at {@code worktree}, with the git directory its worktrees share, if it can be found.
     */
    private static Carried.Shown shown(Path worktree) {
        // Real, as the common git directory is, so the worktree shown, opened anew, is told from another one.
        return new Carried.Shown(
                Try.of(worktree::toRealPath).getOrElse(worktree),
                RepositoryIdentity.read(worktree).map(RepositoryIdentity::commonDir));
    }

    /**
     * Starts showing the repository {@code env} reads, {@code shown}, in the theme its git
     * configuration gives it if no other is asked for, where {@code landing} says, with what {@code carried} keeps of
     * what the worktree shown before knew (ADR 182); from scratch when it carries nothing.
     */
    private void open(
            Env env,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            RepositoryTheme repositoryTheme,
            Landing landing,
            Carried.Shown shown,
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
        runtime = Option.of(new Runtime(io, background, program, shown));
        started.accept(program.model());
        // The first tick of the fetch is a while off, so it fetches now. The features read what they show of the
        // repository from the start, as they answer its opening. Another worktree of the repository shown fetches
        // into the same remote-tracking branches: once they were fetched into, it waits for the tick (ADR 182).
        if (!carried.fetched()) {
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
                    Carried.Shown opened = shown(git.worktree());
                    runner.get().runOnRenderThread(() -> show(git, theme, landing, opened));
                })
                .map(Git::worktree);
    }

    /**
     * Shows the repository {@code git} reads instead of the one shown, as if the app had been started there, where
     * {@code landing} says; the settings, and a theme asked for at startup, stay as they are in this session, and
     * {@code repositoryTheme} is the one its git configuration gives it. When {@code opened} is another worktree of the
     * same repository, it opens with what the worktrees share of what was known, as {@link Carried#between} says
     * (ADR 182); the worktree shown, opened anew, opens from nothing.
     */
    private void show(Git git, RepositoryTheme repositoryTheme, Landing landing, Carried.Shown opened) {
        runtime.forEach(running -> {
            Model last = running.program().model();
            Instant now = clock.instant();
            held = Option.of(HeldFrame.of(drawn(now), now));
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
                    opened,
                    Carried.between(running.shown(), opened, last));
            // What this session knew of the settings file goes on, so a setting only it has is not taken for one
            // the file has, and a problem it was showing still shows (ADR 111).
            runtime.forEach(next -> next.program().dispatch(SettingsPanelUpdate.carried(last)));
            requestRedraw.run();
            // The frame once the screen before is held no longer, for a repository that takes that long to read.
            // On the runner's scheduler, as the app's other wakes, so it stops with the app.
            runner.get().schedule(() -> runner.get().runOnRenderThread(requestRedraw), Jobs.SHOWN_AFTER);
        });
    }

    /**
     * The program and the threads it runs git on, which exist from the start of the app to its stop, or until
     * another repository is opened in its place.
     *
     * @param io         where git is called
     * @param background where the background commands run, like talking to the remotes and to GitHub
     * @param shown      the repository shown, with the git directory its worktrees share
     */
    private record Runtime(
            ThreadPoolExecutor io, ThreadPoolExecutor background, Program program, Carried.Shown shown) {}
}
