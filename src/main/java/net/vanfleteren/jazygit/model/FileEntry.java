package net.vanfleteren.jazygit.model;

/**
 * A single file entry as it would appear in {@code git status}.
 *
 * @param staged   whether the index holds a change for this file
 * @param unstaged whether the working tree holds a change that is not in the index (untracked files included)
 */
public record FileEntry(String path, ChangeType type, boolean staged, boolean unstaged) {

    /**
     * An entry that is wholly staged when it is {@link ChangeType#ADDED}, and wholly unstaged otherwise.
     */
    public FileEntry(String path, ChangeType type) {
        this(path, type, type == ChangeType.ADDED, type != ChangeType.ADDED);
    }
}
