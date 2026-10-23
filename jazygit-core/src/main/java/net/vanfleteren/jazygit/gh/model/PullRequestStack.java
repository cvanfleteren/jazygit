package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;

/**
 * Where a pull request sits in a stack of them: each one targets the branch of the one below it, and the bottom one
 * the base of the stack.
 *
 * @param number   the number of the stack in its repository
 * @param position where the pull request sits in it, 1 being the closest to the base
 * @param size     how many pull requests the stack holds, merged and closed ones too
 * @param entries  the pull requests of the stack, the bottom one first
 */
public record PullRequestStack(int number, int position, int size, List<Entry> entries) {

    /**
     * How many open pull requests of the stack are below this one, which merging it merges too: the ones below that
     * were merged or closed already are no longer merged.
     */
    public int below() {
        return entries.count(entry -> entry.position() < position && entry.state() == State.OPEN);
    }

    /**
     * A pull request of a stack.
     *
     * @param position where it sits in the stack, 1 being the closest to the base
     * @param number   its number
     * @param state    whether it is open, merged or closed
     */
    public record Entry(int position, int number, State state) {}
}
