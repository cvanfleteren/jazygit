package net.vanfleteren.jazygit.state.diff;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;

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
        return this instanceof OfPullRequest(PullRequestCommits commits)
                ? other instanceof OfPullRequest(PullRequestCommits those)
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
     * The changes of a pull request on GitHub, as GitHub shows them: its head against the merge base of its head
     * and its base (ADR 55).
     */
    record OfPullRequest(PullRequestCommits commits) implements Changes {

        /**
         * Its head commit.
         */
        @Override
        public Sha sha() {
            return commits.head();
        }
    }
}
