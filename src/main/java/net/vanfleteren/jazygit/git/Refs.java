package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * The names of refs.
 */
final class Refs {

    static final String HEADS = "refs/heads/";
    static final String REMOTES = "refs/remotes/";

    private Refs() {
    }

    /**
     * The ref of the local branch {@code branch}.
     */
    static String head(Branch.Name branch) {
        return HEADS + branch.value();
    }

    /**
     * The remote-tracking ref of {@code branch} on {@code remote}.
     */
    static String remote(String remote, Branch.Name branch) {
        return REMOTES + remote + "/" + branch.value();
    }

    /**
     * The ref of {@code branch}, local or remote-tracking.
     */
    static String of(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(Branch.Name name) -> head(name);
            case BranchRef.Remote(RemoteBranch remote) -> remote(remote.remote(), remote.branch());
        };
    }
}
