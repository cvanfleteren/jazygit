package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Path;
import java.time.Instant;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Reads commits through {@code git rev-parse} and {@code git log}.
 */
public final class CliLog {

    static final int MAX_COMMITS = 300;
    private static final String SEPARATOR = "\u001f";
    // sha, parents, author name, author email, author time, subject, raw message
    private static final String FORMAT = "%H%x1f%P%x1f%an%x1f%ae%x1f%at%x1f%s%x1f%B";

    private CliLog() {
    }

    /**
     * The commit {@code revision} names; none when it names nothing, e.g. {@code HEAD} in a repository
     * without commits.
     */
    public static Either<ProcessError, Option<Sha>> resolve(Path workTree, String revision) {
        return GitProcess.run(workTree, git("rev-parse", "--verify", "--quiet", revision + "^{commit}"))
                .fold(failure -> unknown(failure.error()) ? Either.right(Option.none()) : Either.left(failure.error()),
                        output -> Either.right(Option.of(new Sha(output.stdout().strip()))));
    }

    /**
     * Whether {@code rev-parse --verify --quiet} failed because the revision names nothing: it then exits
     * with 1, saying nothing.
     */
    private static boolean unknown(ProcessError error) {
        return error instanceof CommandFailed(String explanation, int exitCode) && exitCode == 1 && explanation.isEmpty();
    }

    /**
     * The newest commits reachable from {@code start}, newest first, laid out by {@link CommitGraph#arrange}.
     */
    public static Either<ProcessError, List<Commit>> log(Path workTree, Sha start) {
        return GitProcess.run(workTree, git("--no-optional-locks", "log", "--no-color", "--no-show-signature", "-z",
                        "--max-count=" + MAX_COMMITS, "--format=" + FORMAT, start.value(), "--"))
                .map(output -> CommitGraph.arrange(parse(output.stdout())))
                .mapLeft(GitFailure::error);
    }

    /**
     * Parses {@code git log -z --format=}{@value #FORMAT} output.
     */
    static List<Commit> parse(String raw) {
        return List.of(raw.split("\0")).filter(record -> !record.isBlank()).map(CliLog::commit);
    }

    private static Commit commit(String record) {
        String[] f = record.split(SEPARATOR, 7);
        return new Commit(new Sha(f[0]), f[2], f[3], Instant.ofEpochSecond(Long.parseLong(f[4])), f[5],
                extendedMessage(f[6]),
                List.of(f[1].split(" ")).filter(parent -> !parent.isEmpty()).map(Sha::new));
    }

    /**
     * The commit message after the subject paragraph, or an empty string if there is none.
     */
    static String extendedMessage(String fullMessage) {
        String[] parts = fullMessage.split("\\R\\s*\\R", 2);
        return parts.length < 2 ? "" : parts[1].strip();
    }
}
