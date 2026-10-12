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
 */
public record FileEntry(String path, ChangeType type, boolean staged, boolean unstaged, Option<Conflict> conflict) {

    public FileEntry(String path, ChangeType type, boolean staged, boolean unstaged) {
        this(path, type, staged, unstaged, Option.none());
    }

    /**
     * A file that is in conflict; it is not staged until the conflict is resolved.
     */
    public static FileEntry conflicted(String path, Conflict conflict) {
        return new FileEntry(path, ChangeType.CONFLICTED, false, true, Option.of(conflict));
    }

    /**
     * The paths of the {@code files} that {@code which} picks.
     */
    public static List<String> paths(List<FileEntry> files, Predicate<? super FileEntry> which) {
        return files.filter(which).map(FileEntry::path);
    }

    /**
     * An entry that is wholly staged when it is {@link ChangeType#ADDED}, and wholly unstaged otherwise.
     */
    public FileEntry(String path, ChangeType type) {
        this(path, type, type == ChangeType.ADDED, type != ChangeType.ADDED);
    }
}
