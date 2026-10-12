package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitProcess.Output;

import java.nio.file.Path;
import java.time.Duration;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Talks to the remotes through the {@code git} command line tool, in the background: nothing may ask the
 * user anything.
 */
public final class CliRemote {

    // A first fetch of a big repository takes a while; a fetch that is still running is not started again.
    private static final Duration FETCH_TIMEOUT = Duration.ofMinutes(2);

    private CliRemote() {
    }

    /**
     * The remotes {@code git fetch --all} would fetch: all of them but those with {@code skipFetchAll} set.
     */
    public static Either<ProcessError, List<String>> remotes(Path workTree) {
        return GitProcess.run(workTree, git("remote"))
                .map(output -> List.ofAll(output.stdout().lines()).map(String::strip).filter(name -> !name.isEmpty()))
                .flatMap(remotes -> skippedByFetchAll(workTree).map(skipped -> remotes.removeAll(skipped)))
                .mapLeft(GitFailure::error);
    }

    /**
     * The remotes with {@code remote.<name>.skipFetchAll} set to true.
     */
    private static Either<GitFailure<ProcessError>, List<String>> skippedByFetchAll(Path workTree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.run(workTree, git("config", "--bool", "--get-regexp", "^remote\\..*\\.skipfetchall$"))
                .fold(failure -> failure.error() instanceof CommandFailed(String explanation, int exitCode)
                                && exitCode == 1 && explanation.isEmpty()
                                ? Either.right(List.empty())
                                : Either.left(failure),
                        output -> Either.right(skipped(output.stdout())));
    }

    /**
     * Parses {@code remote.<name>.skipfetchall <true|false>} lines into the names set to true.
     */
    static List<String> skipped(String raw) {
        return List.ofAll(raw.lines())
                .map(line -> line.split(" ", 2))
                .filter(fields -> fields.length == 2 && fields[1].strip().equals("true"))
                .map(fields -> fields[0].substring("remote.".length(), fields[0].length() - ".skipfetchall".length()));
    }

    /**
     * Runs {@code git fetch <remote>}. Whether deleted remote branches are pruned is left to git's own
     * {@code fetch.prune} configuration. Git's own prompts are already off; ssh would still ask for a
     * passphrase or about an unknown host on the terminal, so it runs in batch mode.
     */
    public static Either<GitFailure<ProcessError>, List<String>> fetch(Path workTree, String remote) {
        return configuredSsh(workTree).flatMap(ssh -> GitProcess.run(workTree, git("fetch", "--quiet", remote)
                        .withEnvironment(HashMap.of("GIT_SSH_COMMAND", batchMode(ssh)))
                        .withTimeout(FETCH_TIMEOUT))
                .map(Output::log));
    }

    /**
     * The ssh command git would run, or none when it runs plain {@code ssh}: {@code GIT_SSH_COMMAND}, else
     * {@code core.sshCommand}. Only looked up, so not logged.
     */
    private static Either<GitFailure<ProcessError>, Option<String>> configuredSsh(Path workTree) {
        Option<String> environment = Option.of(System.getenv("GIT_SSH_COMMAND")).filter(command -> !command.isBlank());
        if (environment.isDefined()) {
            return Either.right(environment);
        }
        // git config --get exits with 1, saying nothing, when the key is not set.
        return GitProcess.run(workTree, git("config", "--get", "core.sshCommand"))
                .fold(failure -> failure.error() instanceof CommandFailed(String explanation, int exitCode)
                                && exitCode == 1 && explanation.isEmpty()
                                ? Either.right(Option.none())
                                : Either.left(failure),
                        output -> Either.right(Option.of(output.stdout().strip()).filter(command -> !command.isEmpty())));
    }

    /**
     * {@code ssh}, the configured command, run so that it fails instead of asking anything.
     */
    static String batchMode(Option<String> ssh) {
        return ssh.getOrElse("ssh") + " -o BatchMode=yes";
    }
}
