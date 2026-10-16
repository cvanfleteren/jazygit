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
     * The branch {@code upstream} is read from: the remote-tracking one, or the local branch for an upstream in
     * this repository itself.
     */
    static BranchRef of(RemoteBranch upstream) {
        return upstream.local() ? new Local(upstream.branch()) : new Remote(upstream);
    }

    /**
     * The local branch {@code branch}.
     */
    record Local(Branch.Name branch) implements BranchRef {

        @Override
        public String name() {
            return branch.value();
        }
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
