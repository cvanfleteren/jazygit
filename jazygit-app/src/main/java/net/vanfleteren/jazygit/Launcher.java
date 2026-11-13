package net.vanfleteren.jazygit;

import dev.tamboui.css.engine.StyleEngine;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.PrintWriter;
import java.nio.file.Path;
import net.vanfleteren.jazygit.cli.StartupCli;
import net.vanfleteren.jazygit.cli.StartupOptions;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.git.CliGit;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.files.RepositoryIdentity;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.text.ErrorText;
import net.vanfleteren.jazygit.ui.theme.Theme;
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
        Path start = options.path().getOrElse(Path.of("")).toAbsolutePath();
        Option<Path> debugLog = Option.when(options.debug(), SettingsPaths::debugLog);
        Either<String, Option<JazygitApp>> app = DebugLog.start(debugLog)
                .toEither()
                .mapLeft(failed -> Messages.get("debugLog.failed", debugLog.get(), ErrorText.reason(failed)))
                .flatMap(logging -> Theme.createEngine()
                        .mapLeft(failed ->
                                Messages.get("theme.loadFailed", failed.theme(), ErrorText.reason(failed.cause()))))
                .flatMap(engine -> {
                    RecentRepositories recent = new RecentRepositoriesFile(
                            SettingsPaths.stateFile(),
                            directory -> RepositoryIdentity.read(directory).map(RepositoryIdentity::commonDir));
                    SettingsStore settings = new SettingsFile(SettingsPaths.configFile());
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
                                        Terminal.NONE);
                                return new JazygitApp(env, loaded, options.theme(), engine);
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
        debugLog.forEach(log -> System.err.println(Messages.get("debugLog.written", log)));
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
                                ? Try.of(() -> StartApp.choose(
                                                start,
                                                RecentReposCmd.existing(recent),
                                                settings,
                                                themeOverride,
                                                engine))
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
