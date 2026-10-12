package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Path;
import java.time.Duration;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Reads the working tree status through the {@code git status} command line tool.
 */
public final class CliStatus {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String HEAD = "# branch.head ";
    private static final String OID = "# branch.oid ";

    private CliStatus() {
    }

    /**
     * Runs {@code git status} in {@code workTree} and parses its output.
     */
    public static Either<ProcessError, RepoStatus> read(Path workTree) {
        return GitProcess.run(workTree, git("--no-optional-locks", "status",
                        "--porcelain=v2", "--branch", "-z", "--untracked-files=all").withTimeout(TIMEOUT))
                .map(output -> parse(output.stdout()))
                .mapLeft(GitFailure::error);
    }

    /**
     * Parses the output of {@code git status --porcelain=v2 --branch -z}.
     */
    static RepoStatus parse(String raw) {
        Parsed parsed = List.of(raw.split("\0")).foldLeft(Parsed.NOTHING, CliStatus::next);
        return new RepoStatus(head(parsed.head(), parsed.oid()), parsed.byPath().values().toList());
    }

    /**
     * HEAD as described by the {@code branch.head} and {@code branch.oid} headers, which say
     * {@code (detached)} and {@code (initial)} for a detached HEAD and a repository without commits.
     */
    private static Head head(String branch, String oid) {
        Option<Sha> commit = Option.of(oid).filter(o -> !o.isEmpty() && !o.equals("(initial)")).map(Sha::new);
        return branch.equals("(detached)") && commit.isDefined()
                ? new Head.Detached(commit.get())
                : new Head.OnBranch(branch, commit);
    }

    /**
     * What was parsed so far.
     *
     * @param origin for the rename just parsed, the staged and unstaged flags of its original path,
     *               which is the next field
     */
    private record Parsed(String head, String oid, Map<String, FileEntry> byPath, Option<FileEntry> origin) {

        static final Parsed NOTHING = new Parsed("", "", TreeMap.empty(), Option.none());

        Parsed with(FileEntry entry) {
            return new Parsed(head, oid, byPath.put(entry.path(), entry), Option.none());
        }

        Parsed expectingOrigin(FileEntry flags) {
            return new Parsed(head, oid, byPath, Option.of(flags));
        }
    }

    private static Parsed next(Parsed parsed, String field) {
        return parsed.origin()
                .map(flags -> parsed.with(new FileEntry(field, ChangeType.DELETED, flags.staged(), flags.unstaged())))
                .getOrElse(() -> field.isEmpty() ? parsed : entry(parsed, field));
    }

    private static Parsed entry(Parsed parsed, String field) {
        return switch (field.charAt(0)) {
            case '#' -> header(parsed, field);
            case '?' -> parsed.with(new FileEntry(field.substring(2), ChangeType.UNTRACKED, false, true));
            // 1 XY sub mH mI mW hH hI path
            case '1' -> parsed.with(changed(pathAfter(field, 8), ordinaryChange(xy(field)), xy(field)));
            // 2 XY sub mH mI mW hH hI Xscore path, followed by the original path as its own field
            case '2' -> {
                FileEntry renamed = changed(pathAfter(field, 9), ChangeType.ADDED, xy(field));
                yield parsed.with(renamed).expectingOrigin(renamed);
            }
            // u XY sub m1 m2 m3 mW h1 h2 h3 path
            case 'u' -> parsed.with(FileEntry.conflicted(pathAfter(field, 10), Conflict.of(xy(field))));
            // Ignored entries ('!') and anything unknown are not shown.
            default -> parsed;
        };
    }

    private static Parsed header(Parsed parsed, String field) {
        return field.startsWith(HEAD) ? new Parsed(field.substring(HEAD.length()), parsed.oid(), parsed.byPath(), parsed.origin())
                : field.startsWith(OID) ? new Parsed(parsed.head(), field.substring(OID.length()), parsed.byPath(), parsed.origin())
                : parsed;
    }

    private static String xy(String field) {
        return field.substring(2, 4);
    }

    private static FileEntry changed(String path, ChangeType type, String xy) {
        return new FileEntry(path, type, xy.charAt(0) != '.', xy.charAt(1) != '.');
    }

    private static ChangeType ordinaryChange(String xy) {
        return xy.indexOf('D') >= 0 ? ChangeType.DELETED
                : xy.charAt(0) == 'A' ? ChangeType.ADDED
                : ChangeType.MODIFIED;
    }

    /**
     * Everything after the first {@code fields} space-separated fields; paths may contain spaces.
     */
    private static String pathAfter(String field, int fields) {
        return field.split(" ", fields + 1)[fields];
    }
}
