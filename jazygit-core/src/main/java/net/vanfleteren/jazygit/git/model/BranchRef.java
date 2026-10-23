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
     * Its ref, e.g. {@code refs/heads/main} or {@code refs/remotes/origin/main}.
     */
    String ref();

    /**
     * The local branch {@code branch}.
     */
    record Local(BranchName branch) implements BranchRef {

        @Override
        public String name() {
            return branch.value();
        }

        @Override
        public String ref() {
            return "refs/heads/" + branch.value();
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

        @Override
        public String ref() {
            return branch.trackingRef();
        }
    }
}
