package net.vanfleteren.jazygit.git.model;

/**
 * The kind of change a {@link FileEntry} carries, mirroring the markers used by {@code git status}.
 */
public enum ChangeType {
    MODIFIED("M"),
    ADDED("A"),
    DELETED("D"),
    UNTRACKED("??"),
    /**
     * Both sides of a merge changed the file; see {@link FileEntry#conflict()}.
     */
    CONFLICTED("U");

    private final String marker;

    ChangeType(String marker) {
        this.marker = marker;
    }

    public String marker() {
        return marker;
    }
}
