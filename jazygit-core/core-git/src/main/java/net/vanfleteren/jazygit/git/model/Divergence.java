package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * How a branch and another one went apart: the commits each has that the other has not.
 *
 * @param ahead  the commits only on the branch
 * @param behind the commits only on the other branch
 */
public record Divergence(Side ahead, Side behind) {

    /**
     * Whether neither branch has commits the other has not.
     */
    public boolean isEmpty() {
        return ahead.commits().isEmpty() && behind.commits().isEmpty();
    }

    /**
     * The commits only on one of the branches.
     *
     * @param commits the newest of them, newest first, at most a few hundred
     * @param count   how many there are, also those not in {@code commits}
     */
    public record Side(List<Commit> commits, int count) {

        /**
         * How many of them {@code commits} leaves out.
         */
        public int hidden() {
            return count - commits.size();
        }
    }
}
