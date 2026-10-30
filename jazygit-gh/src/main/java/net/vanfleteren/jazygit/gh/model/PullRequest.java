package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * An open pull request on GitHub.
 *
 * @param number     its number, e.g. 42 for {@code #42}
 * @param headBranch the branch on the remote it merges from
 * @param draft      whether it is still a draft
 * @param checks     how its CI checks are doing
 * @param review     where its review stands
 * @param stack      where it sits in its stack, if it is stacked
 */
public record PullRequest(
        int number, String headBranch, boolean draft, Checks checks, Review review, Option<PullRequestStack> stack) {

    /**
     * One that is not stacked.
     */
    public PullRequest(int number, String headBranch, boolean draft, Checks checks, Review review) {
        this(number, headBranch, draft, checks, review, Option.none());
    }

    /**
     * The combined state of the CI checks of a pull request.
     */
    public enum Checks {
        /**
         * It has no checks.
         */
        NONE,
        /**
         * Some checks are still running, and none failed.
         */
        PENDING,
        /**
         * All checks passed.
         */
        PASSING,
        /**
         * At least one check failed.
         */
        FAILING;

        /**
         * The combined state of checks that are doing as {@code states} say: failing when one failed, else pending
         * when one is still running, else passing when there are any.
         */
        public static Checks combined(List<Checks> states) {
            List<Checks> some = states.removeAll(NONE);
            return some.contains(FAILING)
                    ? FAILING
                    : some.contains(PENDING) ? PENDING : some.isEmpty() ? NONE : PASSING;
        }
    }

    /**
     * The review decision of a pull request.
     */
    public enum Review {
        /**
         * No review is needed.
         */
        NONE,
        /**
         * A review is needed and has not been given yet.
         */
        REQUIRED,
        APPROVED,
        CHANGES_REQUESTED
    }
}
