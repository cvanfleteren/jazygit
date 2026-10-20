package net.vanfleteren.jazygit.git;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.GitError.UnknownBranch;
import net.vanfleteren.jazygit.git.model.*;

/**
 * Reads commits through {@code git rev-parse} and {@code git log}.
 */
final class CliLog {

    static final int MAX_COMMITS = GitInfoProvider.MAX_COMMITS;
    // sha, parents, author name, author email, author time, subject, ref names, raw message
    private static final List<String> FIELDS = List.of("%H", "%P", "%an", "%ae", "%at", "%s", "%D", "%B");
    private static final String TAG_PREFIX = "tag: ";
    private static final String FORMAT = FIELDS.mkString(GitOutput.PRETTY_FIELD_SEPARATOR);

    private CliLog() {}

    /**
     * The commit {@code revision} names; none when it names nothing, e.g. {@code HEAD} in a repository
     * without commits.
     */
    static Either<GitFailure<ProcessError>, Option<Sha>> resolve(Path workTree, String revision) {
        // rev-parse --verify --quiet exits with 1, saying nothing, when the revision names nothing.
        return GitProcess.ask(workTree, GitCommand.reading("rev-parse", "--verify", "--quiet", revision + "^{commit}"))
                .flatMap(answer -> answer.fold(
                        () -> Either.right(Option.none()),
                        found -> GitOutput.sha(found.stdout())
                                .map(Option::of)
                                .mapLeft(error -> new GitFailure<ProcessError>(error, found.log()))));
    }

    /**
     * The log of HEAD, newest first; empty in a repository without commits.
     */
    static Either<ProcessError, List<Commit>> commits(Path workTree) {
        return resolve(workTree, "HEAD")
                .mapLeft(GitFailure::error)
                .flatMap(head -> head.fold(() -> Either.right(List.empty()), tip -> log(workTree, tip)));
    }

    /**
     * The commits of the log of HEAD that {@code query} finds, newest first; none in a repository without commits.
     * They are left as git lists them: with commits left out, merges and what they brought in no longer join up.
     */
    static Either<ProcessError, List<Commit>> search(Path workTree, CommitQuery query) {
        return resolve(workTree, "HEAD")
                .mapLeft(GitFailure::error)
                .flatMap(head -> head.fold(
                        () -> Either.right(List.empty()),
                        tip -> read(workTree, filters(query).append(tip.value()), query.paths())));
    }

    /**
     * The options of {@code git log} that keep the commits {@code query} finds, apart from its paths. The texts
     * are found as they are, not as regular expressions. git keeps a commit of any of the authors, whose message has
     * the phrase.
     */
    static List<String> filters(CommitQuery query) {
        return List.of("--fixed-strings")
                .appendAll(Option.when(query.ignoresCase(), "--regexp-ignore-case"))
                .appendAll(query.message().map(message -> "--grep=" + message))
                .appendAll(query.authors().map(author -> "--author=" + author))
                .appendAll(query.pickaxe().map(text -> "-S" + text));
    }

    /**
     * The log of the branch {@code branch}, local or remote-tracking, newest first.
     */
    static Either<GitError, List<Commit>> log(Path workTree, BranchRef branch) {
        return tip(workTree, branch).flatMap(tip -> Either.narrow(log(workTree, tip)));
    }

    /**
     * The newest commits reachable from {@code start}, newest first, laid out by {@link CommitGraph#arrange}.
     */
    static Either<ProcessError, List<Commit>> log(Path workTree, Sha start) {
        return selected(workTree, List.of(start.value()));
    }

    /**
     * The commits only on {@code branch} and the ones only on {@code other}: at most {@link #MAX_COMMITS} of
     * each, and how many there are in all.
     */
    static Either<GitError, Divergence> divergence(Path workTree, BranchRef branch, BranchRef other) {
        return tip(workTree, branch)
                .flatMap(ours -> tip(workTree, other)
                        .flatMap(theirs -> Either.narrow(counts(workTree, ours, theirs)
                                .flatMap(counts -> selected(workTree, List.of(ours.value(), "^" + theirs.value()))
                                        .flatMap(ahead -> selected(
                                                        workTree, List.of(theirs.value(), "^" + ours.value()))
                                                .map(behind ->
                                                        new Divergence(ahead, counts._1, behind, counts._2)))))));
    }

    /**
     * The subject of the oldest commit on {@code branch} that is not on {@code base}; none when there is none.
     */
    static Either<GitError, Option<String>> firstSubject(Path workTree, BranchRef branch, BranchRef base) {
        return tip(workTree, branch)
                .flatMap(ours -> tip(workTree, base)
                        .flatMap(theirs -> Either.narrow(GitProcess.read(
                                workTree,
                                GitCommand.reading(
                                        "log",
                                        "--no-color",
                                        "--reverse",
                                        "--format=%s",
                                        ours.value(),
                                        "^" + theirs.value(),
                                        "--"),
                                raw -> Either.right(GitOutput.nonBlankLines(raw).headOption())))));
    }

    /**
     * The commits of {@code branch} from {@code oldest} up to its tip, read with {@code git rev-list --parents},
     * which lists each with its parents; and how many of them are on {@code upstream}, from how many are not.
     */
    static Either<GitError, CommitRange> range(
            Path workTree, Branch.Name branch, Sha oldest, Option<RemoteBranch> upstream) {
        List<String> revisions = List.of(Refs.head(branch), "^" + oldest.value() + "^");
        return Either.narrow(GitProcess.read(
                        workTree,
                        GitCommand.reading(List.of("rev-list", "--parents")
                                .appendAll(revisions)
                                .append("--")),
                        raw -> Either.right(GitOutput.nonBlankLines(raw)))
                .flatMap(lines -> upstream.map(remote ->
                                count(workTree, revisions.append("^" + Refs.remote(remote.remote(), remote.branch()))))
                        .getOrElse(() -> Either.right(lines.size()))
                        .map(unpushed -> new CommitRange(
                                lines.size(),
                                lines.count(line -> line.split(" ").length > 2),
                                lines.size() - unpushed))));
    }

    /**
     * How many commits are on {@code branch} and not on {@code base}, counting no further than {@code max}.
     */
    static Either<ProcessError, Integer> countNotOn(Path workTree, BranchRef branch, BranchRef base, int max) {
        return count(workTree, List.of("--max-count=" + max, Refs.of(branch), "^" + Refs.of(base)));
    }

    /**
     * How many commits {@code revisions} select.
     */
    private static Either<ProcessError, Integer> count(Path workTree, List<String> revisions) {
        return GitProcess.read(
                workTree,
                GitCommand.reading(
                        List.of("rev-list", "--count").appendAll(revisions).append("--")),
                raw -> GitOutput.number(raw.strip()).map(Long::intValue));
    }

    private static Either<GitError, Sha> tip(Path workTree, BranchRef branch) {
        return Either.<GitError, Option<Sha>>narrow(
                        resolve(workTree, Refs.of(branch)).mapLeft(GitFailure::error))
                .flatMap(tip -> tip.toEither(() -> new UnknownBranch(branch)));
    }

    /**
     * How many commits are only on {@code ours}, and how many only on {@code theirs}.
     */
    private static Either<ProcessError, Tuple2<Integer, Integer>> counts(Path workTree, Sha ours, Sha theirs) {
        return GitProcess.read(
                workTree,
                GitCommand.reading("rev-list", "--left-right", "--count", ours.value() + "..." + theirs.value(), "--"),
                CliLog::counts);
    }

    /**
     * The output of {@code rev-list --left-right --count}: the two counts, separated by a tab.
     */
    static Either<UnexpectedOutput, Tuple2<Integer, Integer>> counts(String raw) {
        return GitOutput.fields(raw.strip(), "\t", 2)
                .flatMap(counts -> GitOutput.number(counts.get(0))
                        .flatMap(left -> GitOutput.number(counts.get(1))
                                .map(right -> Tuple.of(left.intValue(), right.intValue()))));
    }

    /**
     * The newest commits {@code revisions} select, newest first, laid out by {@link CommitGraph#arrange}.
     */
    private static Either<ProcessError, List<Commit>> selected(Path workTree, List<String> revisions) {
        return read(workTree, revisions, List.empty()).map(CommitGraph::arrange);
    }

    /**
     * The newest commits {@code arguments} select that change one of {@code paths}, or any commit when there are
     * none, as {@code git log} lists them.
     */
    private static Either<ProcessError, List<Commit>> read(Path workTree, List<String> arguments, List<String> paths) {
        return GitProcess.read(
                workTree,
                GitCommand.reading(
                        List.of("log", "--no-color", "--no-show-signature", "-z", "--max-count=" + MAX_COMMITS)
                                .append("--format=" + FORMAT)
                                .appendAll(arguments)
                                .append("--")
                                .appendAll(paths)),
                CliLog::parse);
    }

    /**
     * Parses {@code git log -z} output in the format of {@link #FIELDS}.
     */
    static Either<UnexpectedOutput, List<Commit>> parse(String raw) {
        return Either.sequenceRight(GitOutput.records(raw).map(CliLog::commit)).map(Seq::toList);
    }

    private static Either<UnexpectedOutput, Commit> commit(String record) {
        return GitOutput.fields(record, FIELDS.size())
                .flatMap(f -> GitOutput.sha(f.get(0))
                        .flatMap(sha -> GitOutput.epochSeconds(f.get(4))
                                .map(time -> new Commit(
                                        sha,
                                        f.get(2),
                                        f.get(3),
                                        time,
                                        f.get(5),
                                        extendedMessage(f.get(7)),
                                        List.of(f.get(1).split(" ")).flatMap(Sha::parse),
                                        tags(f.get(6))))));
    }

    /**
     * The tags among the ref names of a commit, as {@code %D} prints them: {@code HEAD -> main, tag: v1}.
     */
    static List<Tag> tags(String refNames) {
        return List.of(refNames.split(","))
                .map(String::strip)
                .filter(name -> name.startsWith(TAG_PREFIX))
                .map(name -> new Tag(name.substring(TAG_PREFIX.length())));
    }

    /**
     * The commit message after the subject paragraph, or an empty string if there is none.
     */
    static String extendedMessage(String fullMessage) {
        return List.of(fullMessage.split("\\R\\s*\\R", 2))
                .drop(1)
                .headOption()
                .map(String::strip)
                .getOrElse("");
    }
}
