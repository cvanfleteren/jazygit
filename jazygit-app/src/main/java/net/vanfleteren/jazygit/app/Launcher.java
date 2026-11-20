package net.vanfleteren.jazygit.app;

import dev.tamboui.css.engine.StyleEngine;
import io.vavr.Lazy;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.PrintWriter;
import java.nio.file.Path;
import net.vanfleteren.jazygit.app.cli.StartupCli;
import net.vanfleteren.jazygit.app.cli.StartupOptions;
import net.vanfleteren.jazygit.core.askpass.AskpassHelper;
import net.vanfleteren.jazygit.core.askpass.AskpassServer;
import net.vanfleteren.jazygit.core.git.CliGit;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.files.RepositoryIdentity;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.features.branches.repos.RecentReposCmd;
import net.vanfleteren.jazygit.kernel.settings.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSources;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.slf4j.LoggerFactory;

/**
 * Starts jazygit: reads the command line, opens the repository to show, and runs {@link JazygitApp} on it.
 */
final class Launcher {

    private Launcher() {}

    /**
     * @param args as {@link StartupCli} reads them; {@code --help} lists them
     */
    static void main(String[] args) {
        // Run by ssh as its SSH_ASKPASS, jazygit only passes what ssh asks to the jazygit that runs ssh (ADR 150).
        if (AskpassHelper.asked()) {
            AskpassHelper.main(args);
            return;
        }
        // What a thread does not catch, as zt-exec's when memory runs out, would be printed over the TUI; and so would
        // what the handler throws, as when memory is still short.
        Thread.setDefaultUncaughtExceptionHandler((thread, bug) -> {
            try {
                BugLog.log(bug);
            } catch (Throwable unwritten) {
                // Lost, as the bug log loses what it cannot write.
            }
        });
        StartupCli.parse(args, new PrintWriter(System.out), new PrintWriter(System.err))
                .peekLeft(exit -> System.exit(exit.code()))
                .forEach(Launcher::start);
    }

    private static void start(StartupOptions options) {
        // ssh asks the user through the app for what the user starts (ADR 150); the socket only opens for the first
        // operation that lets ssh ask (ADR 153). Only made once the app is, after the debug log: the first logger
        // made fixes where the log goes.
        Lazy<AskpassServer> askpass = Lazy.of(() -> {
            AskpassServer server = AskpassServer.in(Path.of(System.getProperty("java.io.tmpdir")));
            // Also when the app exits without returning here.
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            return server;
        });
        try {
            start(options, askpass);
        } finally {
            if (askpass.isEvaluated()) {
                askpass.get().close();
            }
        }
    }

    private static void start(StartupOptions options, Lazy<AskpassServer> askpass) {
        Path start = options.path().getOrElse(Path.of("")).toAbsolutePath();
        Option<Path> debugLog = Option.when(options.debug(), SettingsPaths::debugLog);
        Either<String, Option<JazygitApp>> app = keymap(Keymap.standard())
                .flatMap(keys -> contents(ContentSources.standard()))
                .flatMap(contents -> DebugLog.start(debugLog)
                        .toEither()
                        .mapLeft(failed ->
                                Messages.get("app.debugLog.failed", debugLog.get(), ErrorText.reason(failed))))
                .flatMap(logging -> Theme.createEngine()
                        .mapLeft(failed ->
                                Messages.get("app.theme.loadFailed", failed.theme(), ErrorText.reason(failed.cause()))))
                .flatMap(engine -> {
                    RecentRepositories recent = new RecentRepositoriesFile(
                            SettingsPaths.stateFile(),
                            directory -> RepositoryIdentity.read(directory).map(RepositoryIdentity::commonDir));
                    SettingsStore settings = new SettingsFile(SettingsPaths.configFile(), SettingKeys.KERNEL);
                    SettingsStore.Loaded loaded = settings.load();
                    FormerState.forget(SettingsPaths.stateFile())
                            .onFailure(failed -> LoggerFactory.getLogger(Launcher.class)
                                    .debug("What was kept for all repositories at once could not be deleted", failed));
                    return open(start, recent, loaded, options.theme(), engine)
                            .map(opened -> opened.map(git -> {
                                recent.opened(git.worktree()).onFailure(BugLog::log);
                                Env env = RepositoryEnv.of(
                                        git,
                                        loaded.settings(),
                                        settings,
                                        recent,
                                        new UnseenPullRequestsFile(SettingsPaths.repositories()),
                                        new CheckTimingsFile(SettingsPaths.repositories()),
                                        new CollapsedFile(SettingsPaths.repositories()),
                                        Repositories.NONE,
                                        Terminal.NONE,
                                        askpass.get());
                                return new JazygitApp(env, loaded, options.theme(), engine, Option.of(askpass.get()));
                            }));
                });
        // Quitting instead of choosing what to open starts no app.
        app.flatMap(jazygit -> Try.run(() -> {
                            if (jazygit.isDefined()) {
                                jazygit.get().run();
                            }
                        })
                        .toEither()
                        .mapLeft(ErrorText::reason))
                .orElseRun(problem -> {
                    System.err.println(problem);
                    System.exit(1);
                });
        debugLog.forEach(log -> System.err.println(Messages.get("app.debugLog.written", log)));
    }

    /**
     * The keymap of the app, or why it has none: the views find it as a service ({@link Keymap.Source}), and without
     * it they would start with no key that works. A services file lost in packaging shows here, not as a dead UI.
     */
    static Either<String, Keymap> keymap(Keymap standard) {
        return standard.globals().isEmpty()
                ? Either.left(Messages.get("app.keymap.missing", Keymap.Source.class.getName()))
                : Either.right(standard);
    }

    /**
     * The sources of what the content panel shows, or why there are none: the content panel finds them as a service
     * ({@link ContentSources.Provider}), and without it would show nothing but that it is loading (ADR 154).
     */
    static Either<String, ContentSources> contents(ContentSources standard) {
        return standard.sources().isEmpty()
                ? Either.left(Messages.get("app.contents.missing", ContentSources.Provider.class.getName()))
                : Either.right(standard);
    }

    /**
     * The repository containing {@code start}. Outside one, the user is asked what to open instead (ADR 28), drawn
     * as {@code settings} and {@code themeOverride} say: none when they quit.
     */
    private static Either<String, Option<Git>> open(
            Path start,
            RecentRepositories recent,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            StyleEngine engine) {
        return CliGit.open(start)
                .fold(
                        error -> error instanceof GitError.NotARepository
                                ? StartApp.choose(
                                                start, RecentReposCmd.existing(recent), settings, themeOverride, engine)
                                        .toEither()
                                        .mapLeft(ErrorText::reason)
                                        .flatMap(chosen -> chosen.fold(
                                                () -> Either.right(Option.none()),
                                                worktree -> CliGit.open(worktree)
                                                        .mapLeft(ErrorText::of)
                                                        .map(Option::of)))
                                : Either.left(ErrorText.of(error)),
                        git -> Either.right(Option.of(git)));
    }
}
