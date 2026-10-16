package net.vanfleteren.jazygit.git.model;

/**
 * Where a reset moves the checked out branch to.
 */
public sealed interface ResetTarget {

    /**
     * What git is given to reset to.
     */
    String revision();

    /**
     * How the target is shown to the user, e.g. {@code abc1234} or {@code origin/main}.
     */
    String label();

    /**
     * A commit.
     */
    record ToCommit(Sha sha) implements ResetTarget {

        @Override
        public String revision() {
            return sha.value();
        }

        @Override
        public String label() {
            return sha.shortSha();
        }
    }

    /**
     * The commit a branch the checked out one follows points to, as this repository knows it: nothing is
     * fetched first.
     */
    record ToUpstream(RemoteBranch branch) implements ResetTarget {

        @Override
        public String revision() {
            return branch.ref();
        }

        @Override
        public String label() {
            return branch.shortName();
        }
    }
}
