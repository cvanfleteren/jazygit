package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * A single file entry as it would appear in {@code git status}.
 *
 * @param staged   whether the index holds a change for this file
 * @param unstaged whether the working tree holds a change that is not in the index (untracked files included)
 * @param conflict what each side of the merge did, for a {@link ChangeType#CONFLICTED} file
 * @param origin   the path a {@link ChangeType#RENAMED} file was moved from
 */
public record FileEntry(
        RepoPath path,
        ChangeType type,
        boolean staged,
        boolean unstaged,
        Option<Conflict> conflict,
        Option<RepoPath> origin)
        implements Pathed {

    public FileEntry(RepoPath path, ChangeType type, boolean staged, boolean unstaged) {
        this(path, type, staged, unstaged, Option.none(), Option.none());
    }

    /**
     * A file that is in conflict; it is not staged until the conflict is resolved.
     */
    public static FileEntry conflicted(RepoPath path, Conflict conflict) {
        return new FileEntry(path, ChangeType.CONFLICTED, false, true, Option.of(conflict), Option.none());
    }

    /**
     * A file moved from {@code origin} to {@code path}. The rename itself is staged unless only intent-to-add
     * of {@code path} is.
     */
    public static FileEntry renamed(RepoPath path, RepoPath origin, boolean staged, boolean unstaged) {
        return new FileEntry(path, ChangeType.RENAMED, staged, unstaged, Option.none(), Option.of(origin));
    }

    /**
     * A file of the working tree that has no changes, listed with all its files (ADR 108).
     */
    public static FileEntry unchanged(RepoPath path) {
        return new FileEntry(path, ChangeType.UNCHANGED, false, false);
    }

    /**
     * Whether the file has a change, staged or not, or is untracked: all but a file listed with all files of the
     * working tree that has none (ADR 108).
     */
    public boolean changed() {
        return type != ChangeType.UNCHANGED;
    }

    /**
     * The paths of the {@code files} that {@code which} picks.
     */
    public static List<RepoPath> paths(List<FileEntry> files, Predicate<? super FileEntry> which) {
        return files.filter(which).map(FileEntry::path);
    }

    /**
     * The paths that the staged change ({@code stagedSide}) or the unstaged change of this file touches:
     * a rename also touches its origin, on the side that holds the rename.
     */
    public List<RepoPath> paths(boolean stagedSide) {
        return origin.filter(o -> stagedSide == staged).toList().append(path);
    }

    /**
     * Whether the file is not in HEAD at its path: it was added, or renamed to it.
     */
    public boolean isNew() {
        return type == ChangeType.ADDED || type == ChangeType.RENAMED;
    }
}
