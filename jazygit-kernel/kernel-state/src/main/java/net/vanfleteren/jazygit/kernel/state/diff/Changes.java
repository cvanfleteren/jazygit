package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * Whose changes the files are.
 */
public sealed interface Changes {

    Sha sha();

    /**
     * Whether {@code other} are the changes of the same commit, stash or pull request: a pull request stays the
     * same when its head or its base moved on, as after a force-push (ADR 60).
     */
    default boolean sameAs(Changes other) {
        return this instanceof OfPullRequest(PullRequestCommits commits, var _)
                ? other instanceof OfPullRequest(PullRequestCommits those, var _)
                        && those.number() == commits.number()
                        && those.repository().equals(commits.repository())
                        && those.host().equals(commits.host())
                : equals(other);
    }

    /**
     * The changes of a commit, of a merge against its first parent.
     */
    record OfCommit(Sha sha) implements Changes {}

    /**
     * The changes of a stash, its untracked files included.
     */
    record OfStash(Sha sha) implements Changes {}

    /**
     * The changes of a branch against its base, as a pull request of it would show them: its tip against the merge
     * base of that and the tip of its base (ADR 61).
     *
     * @param branch the branch
     * @param base   the base of ADR 20 it is compared with, and the commit that was at
     * @param sha    the commit the branch was at
     */
    record OfBranch(BranchName branch, BaseTip base, Sha sha) implements Changes {}

    /**
     * The main branch as committed, which has no base (ADR 128): what its tip commit changed, of a merge against its
     * first parent; its files are listed with all files of that commit.
     *
     * @param branch the main branch
     * @param sha    the commit it was at
     */
    record OfTip(BranchName branch, Sha sha) implements Changes {}

    /**
     * The changes of a pull request on GitHub, as GitHub shows them: its head against the merge base of its head
     * and its base (ADR 55).
     *
     * @param commits the commits it is diffed between
     * @param branch  the local branch it was opened from, whose working tree its files are edited in (ADR 192); none
     *                when not known
     */
    record OfPullRequest(PullRequestCommits commits, Option<BranchName> branch) implements Changes {

        /**
         * The changes of the pull request at {@code commits}, of no local branch known.
         */
        public OfPullRequest(PullRequestCommits commits) {
            this(commits, Option.none());
        }

        /**
         * Its head commit.
         */
        @Override
        public Sha sha() {
            return commits.head();
        }

        /**
         * The same pull request, of the same local branch, at the commits it moved on to.
         */
        public OfPullRequest at(PullRequestCommits moved) {
            return new OfPullRequest(moved, branch);
        }
    }
}
