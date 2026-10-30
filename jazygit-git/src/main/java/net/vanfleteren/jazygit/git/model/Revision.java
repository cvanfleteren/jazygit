package net.vanfleteren.jazygit.git.model;

/**
 * What git is given to name a commit: a branch, a commit, or {@code HEAD} (ADR 40).
 */
public sealed interface Revision permits BranchRef, Revision.Commit, Revision.Head {

    /**
     * What git is given: a full ref, a full sha or {@code HEAD}, never a bare name, so that a tag of the same name
     * is not taken for it, and nothing that starts with {@code -}, which git would read as an option.
     */
    String toArg();

    /**
     * How it is shown to the user, e.g. {@code main}, {@code origin/main} or {@code abc1234}.
     */
    String label();

    /**
     * The commit {@code sha}.
     */
    record Commit(Sha sha) implements Revision {

        @Override
        public String toArg() {
            return sha.value();
        }

        @Override
        public String label() {
            return sha.shortSha();
        }
    }

    /**
     * What is checked out.
     */
    record Head() implements Revision {

        private static final String HEAD = "HEAD";

        @Override
        public String toArg() {
            return HEAD;
        }

        @Override
        public String label() {
            return HEAD;
        }
    }
}
