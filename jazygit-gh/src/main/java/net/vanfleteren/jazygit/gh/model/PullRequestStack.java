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
     * The entry of the pull request it was read for, at {@link #position}.
     */
    public Option<Entry> own() {
        return entries.find(entry -> entry.position() == position);
    }

    /**
     * Whether the pull request it was read for is the top open one of the stack, which more can be put on.
     */
    public boolean isTop() {
        return entries.forAll(entry -> entry.position() <= position);
    }

    /**
     * A pull request of a stack.
     *
     * @param position   where it sits in the stack, 1 being the closest to the base
     * @param number     its number
     * @param state      whether it is open, merged or closed
     * @param headBranch the branch it merges from, empty when not known
     * @param baseBranch the branch it merges into, empty when not known
     * @param headOid    the commit its branch was at last, as GitHub has it: where a merged one was (ADR 74)
     * @param queued     whether it is in a merge queue, where rewriting its branch takes it out (ADR 74)
     * @param fork       whether its branch is in another repository than its base, a fork, which checking out the stack
     *                   leaves out (ADR 99)
     */
    public record Entry(
            int position,
            int number,
            State state,
            String headBranch,
            String baseBranch,
            Option<String> headOid,
            boolean queued,
            boolean fork) {

        /**
         * One whose branch is in the repository of its base.
         */
        public Entry(
                int position,
                int number,
                State state,
                String headBranch,
                String baseBranch,
                Option<String> headOid,
                boolean queued) {
            this(position, number, state, headBranch, baseBranch, headOid, queued, false);
        }

        /**
         * One whose head commit and queue are not known.
         */
        public Entry(int position, int number, State state, String headBranch, String baseBranch) {
            this(position, number, state, headBranch, baseBranch, Option.none(), false, false);
        }

        /**
         * One whose branches are not known.
         */
        public Entry(int position, int number, State state) {
            this(position, number, state, "", "", Option.none(), false, false);
        }

        /**
         * Whether it stays in its stack on GitHub when the stack is taken apart: it is merged, or in a merge queue
         * (ADR 73).
         */
        public boolean staysWhenUnstacked() {
            return state == State.MERGED || queued;
        }
    }
}
