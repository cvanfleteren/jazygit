package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * The names of refs.
 */
final class Refs {

    static final String HEADS = "refs/heads/";
    static final String REMOTES = "refs/remotes/";
    /**
     * The remote git names for this repository itself, as for a branch created with {@code --track} from a local one.
     */
    static final String THIS_REPOSITORY = ".";

    private Refs() {}

    /**
     * The ref of the local branch {@code branch}.
     */
    static String head(BranchName branch) {
        return HEADS + branch.value();
    }

    /**
     * The remote-tracking ref of {@code branch} on {@code remote}.
     */
    static String remote(String remote, BranchName branch) {
        return REMOTES + remote + "/" + branch.value();
    }
}
