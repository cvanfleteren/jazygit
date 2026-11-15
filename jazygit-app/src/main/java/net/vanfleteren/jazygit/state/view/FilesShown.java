package net.vanfleteren.jazygit.state.view;

/**
 * Which files the files pane lists (ADR 108).
 */
public enum FilesShown {
    /**
     * Only the files with changes.
     */
    CHANGED,
    /**
     * All files of the working tree.
     */
    ALL;

    /**
     * {@code ALL} if {@code all}, else {@code CHANGED}.
     */
    public static FilesShown of(boolean all) {
        return all ? ALL : CHANGED;
    }

    /**
     * Whether all files are listed.
     */
    public boolean all() {
        return this == ALL;
    }
}
