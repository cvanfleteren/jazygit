package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.Head;

/**
 * Reads the working tree status through the {@code git status} command line tool.
 */
final class CliStatus {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String HEAD = "# branch.head ";
    private static final String OID = "# branch.oid ";

    private CliStatus() {}

    /**
     * Runs {@code git status} in {@code worktree} and parses its output, and finds out what the repository is in
     * the middle of from the files at {@code markers}.
     */
    static Either<ProcessError, RepoStatus> read(Path worktree, CliInProgress.Markers markers) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("status", "--porcelain=v2", "--branch", "-z", "--untracked-files=all")
                                .withTimeout(TIMEOUT),
                        CliStatus::parse)
                .map(status -> status.withInProgress(CliInProgress.read(markers)));
    }

    /**
     * Parses the output of {@code git status --porcelain=v2 --branch -z}.
     */
    static Either<UnexpectedOutput, RepoStatus> parse(String raw) {
        return List.of(raw.split("\0"))
                .foldLeft(
                        Either.<UnexpectedOutput, Parsed>right(Parsed.NOTHING),
                        (parsed, field) -> parsed.flatMap(p -> next(p, field)))
                .flatMap(parsed -> parsed.head()
                        .toEither(() -> new UnexpectedOutput(raw))
                        .map(branch -> new RepoStatus(
                                head(branch, parsed.oid()),
                                parsed.byPath().values().toList())));
    }

    /**
     * HEAD as described by the {@code branch.head} and {@code branch.oid} headers, which say
     * {@code (detached)} and {@code (initial)} for a detached HEAD and a repository without commits.
     */
    private static Head head(String branch, Option<String> oid) {
        Option<Sha> commit = oid.filter(o -> !o.equals("(initial)")).flatMap(Sha::parse);
        return commit.filter(c -> branch.equals("(detached)"))
                .<Head>map(Head.Detached::new)
                .getOrElse(() -> new Head.OnBranch(new Branch.Name(branch), commit));
    }

    private static Either<UnexpectedOutput, Parsed> next(Parsed parsed, String field) {
        return parsed.origin()
                .map(entry -> Either.<UnexpectedOutput, Parsed>right(parsed.with(entry.apply(field))))
                .getOrElse(() -> field.isEmpty() ? Either.right(parsed) : entry(parsed, field));
    }

    private static Either<UnexpectedOutput, Parsed> entry(Parsed parsed, String field) {
        return switch (field.charAt(0)) {
            case '#' -> Either.right(header(parsed, field));
            case '?' ->
                GitOutput.after(field, "? ")
                        .toEither(() -> new UnexpectedOutput(field))
                        .map(path -> parsed.with(new FileEntry(path, ChangeType.UNTRACKED, false, true)));
            // 1 XY sub mH mI mW hH hI path
            case '1' ->
                xy(field)
                        .flatMap(xy ->
                                pathAfter(field, 8).map(path -> parsed.with(changed(path, ordinaryChange(xy), xy))));
            // 2 XY sub mH mI mW hH hI Xscore path, followed by the original path as its own field. A copy
            // leaves its original path as it was, so it shows as added.
            case '2' ->
                xy(field)
                        .flatMap(xy -> pathAfter(field, 9)
                                .map(path -> parsed.expectingOrigin(
                                        xy.staged() == 'R' || xy.unstaged() == 'R'
                                                ? origin -> FileEntry.renamed(
                                                        path, origin, xy.staged() != '.', xy.unstaged() != '.')
                                                : origin -> changed(path, ChangeType.ADDED, xy))));
            // u XY sub m1 m2 m3 mW h1 h2 h3 path
            case 'u' ->
                xy(field)
                        .flatMap(xy -> pathAfter(field, 10)
                                .map(path -> parsed.with(
                                        FileEntry.conflicted(path, Conflict.of(xy.staged(), xy.unstaged())))));
            // Ignored entries ('!') and anything unknown are not shown.
            default -> Either.right(parsed);
        };
    }

    private static Parsed header(Parsed parsed, String field) {
        return GitOutput.after(field, HEAD)
                .map(parsed::withHead)
                .orElse(() -> GitOutput.after(field, OID).map(parsed::withOid))
                .getOrElse(parsed);
    }

    private static Either<UnexpectedOutput, Xy> xy(String field) {
        return field.length() >= 4 && field.charAt(1) == ' '
                ? Either.right(new Xy(field.charAt(2), field.charAt(3)))
                : Either.left(new UnexpectedOutput(field));
    }

    private static FileEntry changed(String path, ChangeType type, Xy xy) {
        return new FileEntry(path, type, xy.staged() != '.', xy.unstaged() != '.');
    }

    private static ChangeType ordinaryChange(Xy xy) {
        return xy.staged() == 'D' || xy.unstaged() == 'D'
                ? ChangeType.DELETED
                : xy.staged() == 'A' ? ChangeType.ADDED : ChangeType.MODIFIED;
    }

    /**
     * Everything after the first {@code fields} space-separated fields; paths may contain spaces.
     */
    private static Either<UnexpectedOutput, String> pathAfter(String field, int fields) {
        return GitOutput.fields(field, " ", fields + 1).map(List::last);
    }

    /**
     * What was parsed so far.
     *
     * @param origin for the rename or copy just parsed, its entry given its original path, which is the
     *               next field
     */
    private record Parsed(
            Option<String> head,
            Option<String> oid,
            Map<String, FileEntry> byPath,
            Option<Function<String, FileEntry>> origin) {

        static final Parsed NOTHING = new Parsed(Option.none(), Option.none(), TreeMap.empty(), Option.none());

        Parsed with(FileEntry entry) {
            return new Parsed(head, oid, byPath.put(entry.path(), entry), Option.none());
        }

        Parsed expectingOrigin(Function<String, FileEntry> entry) {
            return new Parsed(head, oid, byPath, Option.of(entry));
        }

        Parsed withHead(String branch) {
            return new Parsed(Option.of(branch), oid, byPath, origin);
        }

        Parsed withOid(String commit) {
            return new Parsed(head, Option.of(commit), byPath, origin);
        }
    }

    /**
     * The two status letters of an entry: what is staged, and what is not.
     */
    private record Xy(char staged, char unstaged) {}
}
