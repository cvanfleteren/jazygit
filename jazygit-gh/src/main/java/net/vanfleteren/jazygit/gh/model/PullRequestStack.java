package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
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
     * The branch the closest open pull request below this one merges from, which this one is built on; none for the
     * bottom one, when the ones below are merged or closed, or when that one cannot be read. A stack keeps its merged
     * pull requests, but GitHub moves the base of the one above onto what they merged into.
     */
    public Option<String> branchBelow() {
        return entries.filter(entry -> entry.position() < position && entry.state() == State.OPEN)
                .maxBy(Entry::position)
                .map(Entry::headBranch)
                .filter(branch -> !branch.isEmpty());
    }

    /**
     * The branch the bottom pull request of the stack merges into, its base; none when it cannot be read.
     */
    public Option<String> base() {
        return entries.minBy(Entry::position).map(Entry::baseBranch).filter(branch -> !branch.isEmpty());
    }

    /**
     * A pull request of a stack.
     *
     * @param position   where it sits in the stack, 1 being the closest to the base
     * @param number     its number
     * @param state      whether it is open, merged or closed
     * @param headBranch the branch it merges from, empty when not known
     * @param baseBranch the branch it merges into, empty when not known
     */
    public record Entry(int position, int number, State state, String headBranch, String baseBranch) {

        /**
         * One whose branches are not known.
         */
        public Entry(int position, int number, State state) {
            this(position, number, state, "", "");
        }
    }
}
