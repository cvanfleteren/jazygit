package net.vanfleteren.jazygit.core.git.files;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.TreeMap;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.history.CliInProgress;
import net.vanfleteren.jazygit.core.git.history.CliPlan;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitCommand;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.git.process.GitOutput;
import net.vanfleteren.jazygit.core.git.process.GitProcess;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;

/**
 * The {@link Status} of a repository: the working tree status through the {@code git status} command line tool, and
 * the stamps of the files git keeps, through {@link CliMetadata}, which runs no git.
 */
public final class CliStatus implements Status {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String HEAD = "# branch.head ";
    private static final String OID = "# branch.oid ";

    private final Path worktree;
    // Where git keeps the files that tell what the repository is in the middle of, found once.
    private final CliInProgress.Markers markers;
    // Where the files are that tell whether the branches, the stashes or the worktrees changed, found once.
    private final Option<CliMetadata.Locations> metadataAt;

    public CliStatus(Path worktree, CliInProgress.Markers markers, Option<CliMetadata.Locations> metadataAt) {
        this.worktree = worktree;
        this.markers = markers;
        this.metadataAt = metadataAt;
    }

    /**
     * Runs {@code git status} in the worktree and parses its output, and finds out what the repository is in the
     * middle of from the files at the markers.
     */
    @Override
    public Either<GitError, RepoStatus> read() {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("status", "--porcelain=v2", "--branch", "-z", "--untracked-files=all")
                                .withTimeout(TIMEOUT),
                        CliStatus::parse)
                .map(status -> status.withInProgress(CliInProgress.read(markers)))
                .map(status -> status.inProgress()
                        .map(inProgress -> status.withResolved(resolved(status))
                                .withPlan(CliPlan.read(worktree, markers, inProgress, status.headCommit())))
                        .getOrElse(status));
    }

    /**
     * The files of {@code status} whose conflicts were resolved since it stopped: those the index keeps what they
     * were in conflict with for, {@code git ls-files --resolve-undo}, that are not in conflict again (ADR 198). Git
     * drops that when a rebase, cherry-pick or revert moves on to its next commit. None when it cannot be read: it
     * only says how far the user got.
     */
    private List<String> resolved(RepoStatus status) {
        Set<String> conflicted = status.files()
                .filter(file -> file.conflict().isDefined())
                .map(FileEntry::path)
                .toSet();
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("ls-files", "--resolve-undo", "-z").withTimeout(TIMEOUT),
                        raw -> Either.right(resolveUndo(raw)))
                .getOrElse(List.empty())
                .filter(path -> !conflicted.contains(path));
    }

    /**
     * The paths of {@code git ls-files --resolve-undo -z}: {@code <mode> <object> <stage>\t<path>}, one for each
     * stage a file had, each ended by a NUL.
     */
    static List<String> resolveUndo(String raw) {
        return List.of(raw.split("\0"))
                .filter(entry -> entry.contains("\t"))
                .map(entry -> entry.substring(entry.indexOf('\t') + 1))
                .distinct();
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> refreshIndex() {
        return GitProcess.log(
                worktree,
                GitCommand.git("update-index", "-q", "--unmerged", "--refresh").withTimeout(TIMEOUT));
    }

    @Override
    public Option<GitMetadata> metadata() {
        return metadataAt.map(at -> CliMetadata.read(at, Instant.now()));
    }

    @Override
    public Option<MetadataStamp> reflogStamp() {
        return metadataAt.flatMap(at -> CliMetadata.reflog(at, Instant.now()));
    }

    @Override
    public Option<MetadataStamp> diffStamp(List<FileEntry> files) {
        return metadataAt.flatMap(at -> CliMetadata.diff(at, worktree, files, Instant.now()));
    }

    @Override
    public Either<GitError, List<String>> allFiles() {
        return CliWorkingFiles.allFiles(worktree);
    }

    @Override
    public Either<GitError, FileContent> content(String path) {
        return CliWorkingFiles.content(worktree, path, CliDiffs.LIMIT);
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
     * HEAD as described by the {@code branch.head} header, which says {@code (detached)} for a detached HEAD, and
     * the commit of the {@code branch.oid} header, none for a repository without commits.
     */
    private static Head head(String branch, Option<Sha> commit) {
        return commit.filter(c -> branch.equals("(detached)"))
                .<Head>map(Head.Detached::new)
                .getOrElse(() -> new Head.OnBranch(new BranchName(branch), commit));
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
            Option<Sha> oid,
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
            // "(initial)", for a repository without commits, is no sha.
            return new Parsed(head, Sha.parse(commit), byPath, origin);
        }
    }

    /**
     * The two status letters of an entry: what is staged, and what is not.
     */
    private record Xy(char staged, char unstaged) {}
}
