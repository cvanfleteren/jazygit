package net.vanfleteren.jazygit.feature.start;

import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.CliGitInfoProvider;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * The IO of opening something else when jazygit starts outside a repository. It runs before there is a repository,
 * so it needs nothing of an {@link Env}: the app that asks runs it with {@link #run()}.
 */
public sealed interface StartCmd extends Cmd {

    /**
     * The folder {@code path} names: from the home directory when it starts with {@code ~}, else from {@code from}
     * when it is relative.
     */
    static Path resolve(Path from, String path) {
        Path home = Path.of(System.getProperty("user.home"));
        Path named =
                path.equals("~") ? home : path.startsWith("~/") ? home.resolve(path.substring(2)) : from.resolve(path);
        return named.toAbsolutePath().normalize();
    }

    private static Msg opened(CliGitInfoProvider repository) {
        return new StartMsg.Opened(repository.worktree().toString());
    }

    /**
     * Performs the IO, and returns the {@link Msg} that reports its outcome.
     */
    Msg run();

    @Override
    default Msg run(Env env) {
        return run();
    }

    @Override
    default Msg failed(Failure failure) {
        return new StartMsg.Failed(failure);
    }

    /**
     * Makes {@code directory} a repository, and opens it.
     */
    record Init(String directory) implements StartCmd {

        @Override
        public Msg run() {
            return CliGitInfoProvider.init(Path.of(directory)).fold(this::failedWith, StartCmd::opened);
        }
    }

    /**
     * Opens the repository the folder {@code path} names is in, a relative one from {@code from}.
     */
    record Open(String from, String path) implements StartCmd {

        @Override
        public Msg run() {
            Path directory = resolve(Path.of(from), path);
            return CliGitInfoProvider.open(directory)
                    .fold(
                            error -> error instanceof GitError.NotARepository
                                    ? new StartMsg.NoRepository(directory.toString(), Files.isDirectory(directory))
                                    : failedWith(error),
                            StartCmd::opened);
        }
    }
}
