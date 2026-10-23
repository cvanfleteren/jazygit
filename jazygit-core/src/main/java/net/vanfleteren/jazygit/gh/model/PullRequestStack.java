package net.vanfleteren.jazygit.gh.model;

/**
 * Where a pull request sits in a stack of them: each one targets the branch of the one below it, and the bottom one
 * the base of the stack.
 *
 * @param number   the number of the stack in its repository
 * @param position where the pull request sits in it, 1 being the closest to the base
 * @param size     how many pull requests the stack holds
 */
public record PullRequestStack(int number, int position, int size) {

    /**
     * How many pull requests of the stack are below this one, which merging it merges too.
     */
    public int below() {
        return position - 1;
    }
}
