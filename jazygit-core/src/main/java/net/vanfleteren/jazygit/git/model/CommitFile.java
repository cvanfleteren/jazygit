package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A file a commit or a stash changed, as {@code git show --name-status} lists it.
 *
 * @param type   what happened to it: {@link ChangeType#ADDED}, {@link ChangeType#MODIFIED},
 *               {@link ChangeType#DELETED} or {@link ChangeType#RENAMED}
 * @param origin the path a {@link ChangeType#RENAMED} file was moved from
 */
public record CommitFile(String path, ChangeType type, Option<String> origin) implements Pathed {

    public CommitFile(String path, ChangeType type) {
        this(path, type, Option.none());
    }

    /**
     * The paths the change of the file touches: a rename also touches its origin, which its diff needs to show as
     * one.
     */
    public List<String> paths() {
        return origin.toList().append(path);
    }
}
