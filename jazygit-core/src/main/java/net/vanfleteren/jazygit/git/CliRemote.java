package net.vanfleteren.jazygit.git;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Talks to the remotes through the {@code git} command line tool, in the background: nothing may ask the
 * user anything.
 */
final class CliRemote {

    // A first fetch of a big repository takes a while; a fetch that is still running is not started again.
    // Talking to a remote takes a while for a big repository, or over a slow connection.
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final String REMOTE_KEY = "remote.";
    private static final String SKIP_FETCH_ALL_KEY = ".skipfetchall";
    private static final String URL_KEY = ".url";

    private CliRemote() {}

    /**
     * The remotes {@code git fetch --all} would fetch: all of them but those with {@code skipFetchAll} set.
     */
    static Either<ProcessError, List<String>> remotes(Path worktree) {
        return GitProcess.read(worktree, GitCommand.reading("remote"), raw -> Either.right(names(raw)))
                .flatMap(remotes ->
                        skippedByFetchAll(worktree).map(remotes::removeAll).mapLeft(GitFailure::error));
    }

    private static List<String> names(String raw) {
        return GitOutput.nonBlankLines(raw).map(String::strip);
    }

    /**
     * The remotes with {@code remote.<name>.skipFetchAll} set to true.
     */
    private static Either<GitFailure<ProcessError>, List<String>> skippedByFetchAll(Path worktree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading("config", "--bool", "--get-regexp", "^remote\\..*\\.skipfetchall$"))
                .map(answer -> answer.map(output -> skipped(output.stdout())).getOrElse(List.empty()));
    }

    /**
     * Parses {@code remote.<name>.skipfetchall <true|false>} lines into the names set to true.
     */
    static List<String> skipped(String raw) {
        return GitOutput.nonBlankLines(raw)
                .flatMap(line -> GitOutput.fields(line, " ", 2).toOption())
                .filter(fields -> fields.last().strip().equals("true"))
                .flatMap(fields -> GitOutput.after(fields.head(), REMOTE_KEY))
                .filter(rest -> rest.endsWith(SKIP_FETCH_ALL_KEY))
                .map(rest -> rest.substring(0, rest.length() - SKIP_FETCH_ALL_KEY.length()))
                .filter(name -> !name.isEmpty());
    }

    /**
     * The URL of each remote, by its name. Only looked up, so not logged.
     */
    static Either<ProcessError, Map<String, String>> remoteUrls(Path worktree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(worktree, GitCommand.reading("config", "--get-regexp", "^remote\\..*\\.url$"))
                .map(answer -> answer.map(output -> urls(output.stdout())).getOrElse(HashMap.empty()))
                .mapLeft(GitFailure::error);
    }

    /**
     * Parses {@code remote.<name>.url <url>} lines into the URL of each remote; the first, for a remote with
     * several.
     */
    static Map<String, String> urls(String raw) {
        return GitOutput.nonBlankLines(raw)
                .flatMap(line -> GitOutput.fields(line, " ", 2).toOption())
                .flatMap(fields -> GitOutput.after(fields.head(), REMOTE_KEY)
                        .filter(rest -> rest.endsWith(URL_KEY))
                        .map(rest -> rest.substring(0, rest.length() - URL_KEY.length()))
                        .filter(name -> !name.isEmpty())
                        .map(name -> Tuple.of(name, fields.last().strip())))
                .reverse()
                .toMap(url -> url);
    }

    /**
     * Runs {@code command}, which talks to a remote, for its effect.
     *
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> run(
            Path worktree, GitCommand command, Map<String, String> credentials) {
        return toRemote(worktree, command, credentials).flatMap(remote -> GitProcess.log(worktree, remote));
    }

    /**
     * {@code command}, which talks to a remote, run so that ssh fails instead of asking for a passphrase or about
     * an unknown host on the terminal, which the TUI owns; and with the time the network takes.
     *
     * @param environment the value of the environment variable {@code GIT_SSH_COMMAND}, if it is set
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure<ProcessError>, GitCommand> toRemote(
            Path worktree, GitCommand command, Option<String> environment, Map<String, String> credentials) {
        return configuredSsh(worktree, environment)
                .map(ssh -> command.withEnvironment(credentials.put("GIT_SSH_COMMAND", batchMode(ssh)))
                        .withTimeout(TIMEOUT));
    }

    /**
     * {@code command}, which talks to a remote, as {@link #toRemote(Path, GitCommand, Option, Map)} runs it, with
     * the ssh command of this process's environment.
     */
    static Either<GitFailure<ProcessError>, GitCommand> toRemote(
            Path worktree, GitCommand command, Map<String, String> credentials) {
        return toRemote(worktree, command, Option.of(System.getenv("GIT_SSH_COMMAND")), credentials);
    }

    /**
     * The ssh command git would run, or none when it runs plain {@code ssh}: {@code GIT_SSH_COMMAND}, else
     * {@code core.sshCommand}. Only looked up, so not logged.
     */
    private static Either<GitFailure<ProcessError>, Option<String>> configuredSsh(
            Path worktree, Option<String> environment) {
        Option<String> set = environment.filter(command -> !command.isBlank());
        // git config --get exits with 1, saying nothing, when the key is not set.
        return set.isDefined()
                ? Either.right(set)
                : GitProcess.ask(worktree, GitCommand.reading("config", "--get", "core.sshCommand"))
                        .map(answer ->
                                answer.map(output -> output.stdout().strip()).filter(command -> !command.isEmpty()));
    }

    /**
     * {@code ssh}, the configured command, run so that it fails instead of asking anything.
     */
    static String batchMode(Option<String> ssh) {
        return ssh.getOrElse("ssh") + " -o BatchMode=yes";
    }
}
