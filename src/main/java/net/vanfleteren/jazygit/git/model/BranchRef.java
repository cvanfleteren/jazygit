package net.vanfleteren.jazygit.git.model;

/**
 * A branch whose log can be shown: a local one, or a remote-tracking one.
 */
public sealed interface BranchRef {

    /**
     * The name git shows for it, e.g. {@code main} or {@code origin/main}.
     */
    String name();

    /**
     * The local branch {@code name}.
     */
    record Local(String name) implements BranchRef {
    }

    /**
     * The remote-tracking branch of {@code branch}.
     */
    record Remote(RemoteBranch branch) implements BranchRef {

        @Override
        public String name() {
            return branch.shortName();
        }
    }
}
