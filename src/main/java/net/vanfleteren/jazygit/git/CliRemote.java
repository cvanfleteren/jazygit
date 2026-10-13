package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

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
    private static final String REMOTE_KEY = "remote.";
    private static final String SKIP_FETCH_ALL_KEY = ".skipfetchall";

    private CliRemote() {
    }

    /**
     * The remotes {@code git fetch --all} would fetch: all of them but those with {@code skipFetchAll} set.
     */
    public static Either<ProcessError, List<String>> remotes(Path workTree) {
        return GitProcess.read(workTree, git("remote"), raw -> Either.right(names(raw)))
                .flatMap(remotes -> skippedByFetchAll(workTree).map(remotes::removeAll).mapLeft(GitFailure::error));
    }

    private static List<String> names(String raw) {
        return GitOutput.nonBlankLines(raw).map(String::strip);
    }

    /**
     * The remotes with {@code remote.<name>.skipFetchAll} set to true.
     */
    private static Either<GitFailure<ProcessError>, List<String>> skippedByFetchAll(Path workTree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(workTree, git("config", "--bool", "--get-regexp", "^remote\\..*\\.skipfetchall$"))
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
     * Runs {@code git fetch <remote>}. Whether deleted remote branches are pruned is left to git's own
     * {@code fetch.prune} configuration. Git's own prompts are already off; ssh would still ask for a
     * passphrase or about an unknown host on the terminal, so it runs in batch mode.
     *
     * @param environment the value of the environment variable {@code GIT_SSH_COMMAND}, if it is set
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(Path workTree, String remote,
                                                                     Option<String> environment) {
        return configuredSsh(workTree, environment).flatMap(ssh -> GitProcess.log(workTree,
                git("fetch", "--quiet", remote)
                        .withEnvironment(HashMap.of("GIT_SSH_COMMAND", batchMode(ssh)))
                        .withTimeout(FETCH_TIMEOUT)));
    }

    /**
     * The ssh command git would run, or none when it runs plain {@code ssh}: {@code GIT_SSH_COMMAND}, else
     * {@code core.sshCommand}. Only looked up, so not logged.
     */
    private static Either<GitFailure<ProcessError>, Option<String>> configuredSsh(Path workTree,
                                                                                Option<String> environment) {
        Option<String> set = environment.filter(command -> !command.isBlank());
        // git config --get exits with 1, saying nothing, when the key is not set.
        return set.isDefined()
                ? Either.right(set)
                : GitProcess.ask(workTree, git("config", "--get", "core.sshCommand"))
                .map(answer -> answer.map(output -> output.stdout().strip()).filter(command -> !command.isEmpty()));
    }

    /**
     * {@code ssh}, the configured command, run so that it fails instead of asking anything.
     */
    static String batchMode(Option<String> ssh) {
        return ssh.getOrElse("ssh") + " -o BatchMode=yes";
    }
}
