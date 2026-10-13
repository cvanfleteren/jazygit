package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.GitError.UnknownBranch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Reads commits through {@code git rev-parse} and {@code git log}.
 */
public final class CliLog {

    static final int MAX_COMMITS = 300;
    // sha, parents, author name, author email, author time, subject, ref names, raw message
    private static final List<String> FIELDS = List.of("%H", "%P", "%an", "%ae", "%at", "%s", "%D", "%B");
    private static final String TAG_PREFIX = "tag: ";
    private static final String FORMAT = FIELDS.mkString(GitOutput.PRETTY_FIELD_SEPARATOR);

    private CliLog() {
    }

    /**
     * The commit {@code revision} names; none when it names nothing, e.g. {@code HEAD} in a repository
     * without commits.
     */
    public static Either<GitFailure<ProcessError>, Option<Sha>> resolve(Path workTree, String revision) {
        // rev-parse --verify --quiet exits with 1, saying nothing, when the revision names nothing.
        return GitProcess.ask(workTree, git("rev-parse", "--verify", "--quiet", revision + "^{commit}"))
                .flatMap(answer -> answer.fold(
                        () -> Either.right(Option.none()),
                        found -> GitOutput.sha(found.stdout())
                                .map(Option::of)
                                .mapLeft(error -> new GitFailure<ProcessError>(error, found.log()))));
    }

    /**
     * The log of HEAD, newest first; empty in a repository without commits.
     */
    public static Either<ProcessError, List<Commit>> commits(Path workTree) {
        return resolve(workTree, "HEAD").mapLeft(GitFailure::error)
                .flatMap(head -> head.fold(() -> Either.right(List.empty()), tip -> log(workTree, tip)));
    }

    /**
     * The log of the local branch {@code branch}, newest first.
     */
    public static Either<LogError, List<Commit>> log(Path workTree, String branch) {
        return Either.<LogError, Option<Sha>>narrow(resolve(workTree, Refs.head(branch)).mapLeft(GitFailure::error))
                .flatMap(tip -> tip.<LogError>toEither(() -> new UnknownBranch(branch)))
                .flatMap(tip -> Either.narrow(log(workTree, tip)));
    }

    /**
     * The newest commits reachable from {@code start}, newest first, laid out by {@link CommitGraph#arrange}.
     */
    public static Either<ProcessError, List<Commit>> log(Path workTree, Sha start) {
        return GitProcess.read(workTree, GitCommand.reading("log", "--no-color", "--no-show-signature", "-z",
                        "--max-count=" + MAX_COMMITS, "--format=" + FORMAT, start.value(), "--"),
                raw -> parse(raw).map(CommitGraph::arrange));
    }

    /**
     * Parses {@code git log -z} output in the format of {@link #FIELDS}.
     */
    static Either<UnexpectedOutput, List<Commit>> parse(String raw) {
        return Either.sequenceRight(GitOutput.records(raw).map(CliLog::commit)).map(Seq::toList);
    }

    private static Either<UnexpectedOutput, Commit> commit(String record) {
        return GitOutput.fields(record, FIELDS.size()).flatMap(f -> GitOutput.sha(f.get(0)).flatMap(sha ->
                GitOutput.epochSeconds(f.get(4)).map(time -> new Commit(sha, f.get(2), f.get(3), time, f.get(5),
                        extendedMessage(f.get(7)),
                        List.of(f.get(1).split(" ")).flatMap(Sha::parse), tags(f.get(6))))));
    }

    /**
     * The tag names among the ref names of a commit, as {@code %D} prints them: {@code HEAD -> main, tag: v1}.
     */
    static List<String> tags(String refNames) {
        return List.of(refNames.split(",")).map(String::strip)
                .filter(name -> name.startsWith(TAG_PREFIX))
                .map(name -> name.substring(TAG_PREFIX.length()));
    }

    /**
     * The commit message after the subject paragraph, or an empty string if there is none.
     */
    static String extendedMessage(String fullMessage) {
        return List.of(fullMessage.split("\\R\\s*\\R", 2)).drop(1).headOption().map(String::strip).getOrElse("");
    }
}
