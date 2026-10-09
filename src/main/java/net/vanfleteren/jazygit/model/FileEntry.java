package net.vanfleteren.jazygit.model;

/**
 * A single file entry as it would appear in {@code git status}.
 */
public record FileEntry(String path, ChangeType type) {
}
