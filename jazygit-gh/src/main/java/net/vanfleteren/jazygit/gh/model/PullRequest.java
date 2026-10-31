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
 * @param commits    the commits its changes are diffed between, when GitHub said (ADR 61)
 */
public record PullRequest(
        int number,
        String headBranch,
        boolean draft,
        Checks checks,
        Review review,
        Option<PullRequestStack> stack,
        Option<Commits> commits) {

    /**
     * One whose commits are not known.
     */
    public PullRequest(
            int number,
            String headBranch,
            boolean draft,
            Checks checks,
            Review review,
            Option<PullRequestStack> stack) {
        this(number, headBranch, draft, checks, review, stack, Option.none());
    }

    /**
     * One that is not stacked.
     */
    public PullRequest(int number, String headBranch, boolean draft, Checks checks, Review review) {
        this(number, headBranch, draft, checks, review, Option.none());
    }

    /**
     * The commits a pull request is diffed between, as GitHub shows its changes (ADR 55, 61).
     *
     * @param repository the repository it is of, as {@code owner/name}
     * @param url        its URL, which names the host its repository is on
     * @param base       the commit its base branch is at
     * @param head       the commit its branch is at
     */
    public record Commits(String repository, String url, GhCommit base, GhCommit head) {

        /**
         * Its commits, when GitHub said both.
         */
        public static Option<Commits> of(String repository, String url, Option<GhCommit> base, Option<GhCommit> head) {
            return base.flatMap(at -> head.map(tip -> new Commits(repository, url, at, tip)));
        }

        /**
         * The host its repository is on, like {@code github.com}; empty when its URL does not say.
         */
        public String host() {
            return PullRequestDetails.hostOf(url);
        }
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
