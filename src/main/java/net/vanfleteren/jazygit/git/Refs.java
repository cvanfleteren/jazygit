package net.vanfleteren.jazygit.git;

/**
 * The names of refs.
 */
final class Refs {

    static final String HEADS = "refs/heads/";
    static final String REMOTES = "refs/remotes/";
    /**
     * The remote a branch without an upstream is pushed to.
     */
    static final String DEFAULT_REMOTE = "origin";

    private Refs() {
    }

    /**
     * The ref of the local branch {@code branch}.
     */
    static String head(String branch) {
        return HEADS + branch;
    }

    /**
     * The remote-tracking ref of {@code branch} on {@code remote}.
     */
    static String remote(String remote, String branch) {
        return REMOTES + remote + "/" + branch;
    }
}
