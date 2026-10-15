package net.vanfleteren.jazygit.git.model;

/**
 * Where a branch is deleted.
 */
public enum DeleteScope {
    LOCAL,
    REMOTE,
    BOTH;

    public boolean local() {
        return this != REMOTE;
    }

    public boolean remote() {
        return this != LOCAL;
    }
}
