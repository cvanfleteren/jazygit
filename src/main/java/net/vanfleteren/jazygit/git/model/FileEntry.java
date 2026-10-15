package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Predicate;

/**
 * A single file entry as it would appear in {@code git status}.
 *
 * @param staged   whether the index holds a change for this file
 * @param unstaged whether the working tree holds a change that is not in the index (untracked files included)
 * @param conflict what each side of the merge did, for a {@link ChangeType#CONFLICTED} file
 * @param origin   the path a {@link ChangeType#RENAMED} file was moved from
 */
public record FileEntry(
        String path,
        ChangeType type,
        boolean staged,
        boolean unstaged,
        Option<Conflict> conflict,
        Option<String> origin) {

    public FileEntry(String path, ChangeType type, boolean staged, boolean unstaged) {
        this(path, type, staged, unstaged, Option.none(), Option.none());
    }

    /**
     * An entry that is wholly staged when it is {@link ChangeType#ADDED}, and wholly unstaged otherwise.
     */
    public FileEntry(String path, ChangeType type) {
        this(path, type, type == ChangeType.ADDED, type != ChangeType.ADDED);
    }

    /**
     * A file that is in conflict; it is not staged until the conflict is resolved.
     */
    public static FileEntry conflicted(String path, Conflict conflict) {
        return new FileEntry(path, ChangeType.CONFLICTED, false, true, Option.of(conflict), Option.none());
    }

    /**
     * A file moved from {@code origin} to {@code path}. The rename itself is staged unless only intent-to-add
     * of {@code path} is.
     */
    public static FileEntry renamed(String path, String origin, boolean staged, boolean unstaged) {
        return new FileEntry(path, ChangeType.RENAMED, staged, unstaged, Option.none(), Option.of(origin));
    }

    /**
     * The paths of the {@code files} that {@code which} picks.
     */
    public static List<String> paths(List<FileEntry> files, Predicate<? super FileEntry> which) {
        return files.filter(which).map(FileEntry::path);
    }

    /**
     * The paths that the staged change ({@code stagedSide}) or the unstaged change of this file touches:
     * a rename also touches its origin, on the side that holds the rename.
     */
    public List<String> paths(boolean stagedSide) {
        return origin.filter(o -> stagedSide == staged).toList().append(path);
    }

    /**
     * Whether the file is not in HEAD at its path: it was added, or renamed to it.
     */
    public boolean isNew() {
        return type == ChangeType.ADDED || type == ChangeType.RENAMED;
    }
}
