package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The steps of an operation that stopped, as git keeps them (ADR 199): a rebase, or a cherry-pick or revert, of one
 * commit or several.
 *
 * @param base    where it started: what a rebase rebases onto, or HEAD before the first commit was picked; the commits
 *                made since are those of the log above it
 * @param stopped the step it stopped at
 * @param todo    the steps still to come, in the order git takes them
 * @param step    which step it stopped at, counted from 1
 * @param total   how many steps there are
 * @param commits the commits of its steps, as git reads them, which the content panel shows when one is highlighted
 */
public record Plan(Sha base, Step stopped, List<Step> todo, int step, int total, List<Commit> commits) {

    /**
     * The steps, whose commits are not read.
     */
    public Plan(Sha base, Step stopped, List<Step> todo, int step, int total) {
        this(base, stopped, todo, step, total, List.empty());
    }

    /**
     * This plan, without the commits of its steps: what it says of them.
     */
    public Plan steps() {
        return new Plan(base, stopped, todo, step, total);
    }

    /**
     * The commit {@code sha} of one of its steps, if it was read.
     */
    public Option<Commit> commit(Sha sha) {
        return commits.find(commit -> commit.sha().equals(sha));
    }

    /**
     * One step: a line of git's list of them.
     *
     * @param command what it does, as git names it in full: {@code pick}, {@code fixup}, {@code exec}, …
     * @param commit  the commit it takes, for those that take one
     * @param text    the subject of that commit; or, for a step that takes none, the rest of its line, as what
     *                {@code exec} runs
     */
    public record Step(String command, Option<Sha> commit, String text) {

        /**
         * A step that picks {@code commit}, whose subject is {@code subject}.
         */
        public static Step pick(Sha commit, String subject) {
            return new Step("pick", Option.of(commit), subject);
        }
    }
}
