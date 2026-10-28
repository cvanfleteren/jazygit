package net.vanfleteren.jazygit.git.model;

/**
 * A branch whose log can be shown: a local one, or a remote-tracking one. It is the one place a branch's ref is
 * spelled.
 */
public sealed interface BranchRef extends Revision {

    /**
     * Where the refs of the local branches are.
     */
    String HEADS = "refs/heads/";

    /**
     * Where the refs of the remote-tracking branches are.
     */
    String REMOTES = "refs/remotes/";

    /**
     * The name git shows for it, e.g. {@code main} or {@code origin/main}.
     */
    String name();

    /**
     * Its ref, e.g. {@code refs/heads/main} or {@code refs/remotes/origin/main}.
     */
    @Override
    String toArg();

    @Override
    default String label() {
        return name();
    }

    /**
     * The local branch {@code branch}.
     */
    record Local(BranchName branch) implements BranchRef {

        @Override
        public String name() {
            return branch.value();
        }

        @Override
        public String toArg() {
            return HEADS + branch.value();
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
        public String toArg() {
            return REMOTES + branch.shortName();
        }
    }
}
