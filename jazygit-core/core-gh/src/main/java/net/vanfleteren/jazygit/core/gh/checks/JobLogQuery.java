package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.repo.RepoSlug;

/**
 * The log of a job of GitHub Actions, over REST, as plain text, and where it is kept (ADR 160).
 */
final class JobLogQuery {

    // What GitHub marks the line of an error with, after the timestamp.
    static final String ERROR = "##[error]";

    // Since 2.104, gh prints what has terminal escape sequences in it, as the colours of a log, only when asked to.
    static final String ESCAPES = "--allow-escape-sequences";

    // The letters, digits, '.', '-' and '_' GitHub allows in an owner and a repository name.
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private JobLogQuery() {}

    /**
     * The command that prints the log of the job {@code jobId} of {@code repository}, as {@code owner/name}, with its
     * escape sequences.
     */
    static GhCommand command(RepoSlug repository, long jobId) {
        return gh("api", ESCAPES, endpoint(repository, jobId));
    }

    /**
     * The command for a gh older than 2.104, which does not know {@link #ESCAPES}, and prints them anyway.
     */
    static GhCommand older(RepoSlug repository, long jobId) {
        return gh("api", endpoint(repository, jobId));
    }

    /**
     * Whether gh failed as {@code error} says as it does not know {@link #ESCAPES}.
     */
    static boolean tooOld(GhError error) {
        return error instanceof GhError.CommandFailed(var explanation, int exitCode)
                && explanation.exists(line -> line.contains("unknown flag: " + ESCAPES));
    }

    private static String endpoint(RepoSlug repository, long jobId) {
        return "repos/" + repository.value() + "/actions/jobs/" + jobId + "/logs";
    }

    /**
     * Where the log of the job {@code jobId}, of the attempt {@code attempt} of the workflow run {@code runId} of
     * {@code repository}, is kept in {@code cache}: {@code logs/<owner>/<name>/<run>-<attempt>-<job>.log}. Unexpected
     * output for what is no owner and name GitHub allows, so that no file is ever outside it.
     */
    static Either<GhError, Path> file(Path cache, RepoSlug repository, long runId, int attempt, long jobId) {
        List<String> parts = List.of(repository.value().split("/", -1));
        return parts.size() == 2
                        && parts.forAll(part -> NAME.matcher(part).matches() && !part.equals(".") && !part.equals(".."))
                ? Either.right(cache.resolve("logs")
                        .resolve(parts.get(0))
                        .resolve(parts.get(1))
                        .resolve(runId + "-" + attempt + "-" + jobId + ".log"))
                : Either.left(new GhError.UnexpectedOutput("No owner/name: " + repository.value()));
    }

    /**
     * The line to open the log {@code text} at, counted from 1: the first that says {@code ##[error]}, else the last.
     */
    static int line(String text) {
        List<String> lines = List.ofAll(text.lines());
        int error = lines.indexWhere(line -> line.contains(ERROR));
        return error >= 0 ? error + 1 : Math.max(1, lines.size());
    }
}
