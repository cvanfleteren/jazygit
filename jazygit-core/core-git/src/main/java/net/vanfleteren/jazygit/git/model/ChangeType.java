package net.vanfleteren.jazygit.git.model;

/**
 * The kind of change a {@link FileEntry} carries, mirroring the markers used by {@code git status}.
 */
public enum ChangeType {
    MODIFIED("M"),
    ADDED("A"),
    DELETED("D"),
    /**
     * Moved from {@link FileEntry#origin()}, possibly with changes.
     */
    RENAMED("R"),
    UNTRACKED("??"),
    /**
     * Both sides of a merge changed the file; see {@link FileEntry#conflict()}.
     */
    CONFLICTED("U"),
    /**
     * Not changed at all: a file of the working tree listed with all its files (ADR 108), which has no marker.
     */
    UNCHANGED("");

    private final String marker;

    ChangeType(String marker) {
        this.marker = marker;
    }

    public String marker() {
        return marker;
    }
}
