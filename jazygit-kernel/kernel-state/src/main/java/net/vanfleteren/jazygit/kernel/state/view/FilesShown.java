package net.vanfleteren.jazygit.kernel.state.view;

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
     * Whether all files are listed.
     */
    public boolean all() {
        return this == ALL;
    }

    /**
     * The other choice.
     */
    public FilesShown toggled() {
        return this == CHANGED ? ALL : CHANGED;
    }
}
