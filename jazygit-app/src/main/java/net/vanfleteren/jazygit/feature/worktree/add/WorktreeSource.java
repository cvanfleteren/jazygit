package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.Revision;

/**
 * What a new worktree checks out.
 */
public sealed interface WorktreeSource {

    /**
     * How it is named to the user, in the titles of the popups.
     */
    String label();

    /**
     * What the directory of the new worktree is named after, next to the main worktree.
     */
    BranchName directory();

    /**
     * The branch that is busy while the worktree is added, if any.
     */
    default Option<BranchRef> busyBranch() {
        return Option.none();
    }

    /**
     * {@code branch}, which exists and is checked out nowhere.
     */
    record Existing(BranchName branch) implements WorktreeSource {

        @Override
        public String label() {
            return branch.value();
        }

        @Override
        public BranchName directory() {
            return branch;
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch));
        }
    }

    /**
     * The new branch {@code name}, created as {@code from} says.
     */
    record Created(BranchName name, NewBranchStart from) implements WorktreeSource {

        @Override
        public String label() {
            return name.value();
        }

        @Override
        public BranchName directory() {
            return name;
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(name));
        }
    }

    /**
     * {@code start}, a branch or a commit, on no branch.
     *
     * @param label how the user typed it, e.g. a tag, which the directory is named after
     */
    record Detached(Revision start, String label) implements WorktreeSource {

        @Override
        public BranchName directory() {
            return new BranchName(label);
        }
    }

    /**
     * {@code pullRequest}, of the repository, on the branch gh makes for it.
     */
    record PullRequest(PullRequestSummary pullRequest) implements WorktreeSource {

        @Override
        public String label() {
            return "#" + pullRequest.number();
        }

        /**
         * Its branch; for one from a fork, whose branch gh may name otherwise, its number.
         */
        @Override
        public BranchName directory() {
            return new BranchName(pullRequest.fromFork() ? "pr-" + pullRequest.number() : pullRequest.headBranch());
        }

        /**
         * Its branch, which gh checks out, unless it is from a fork, whose branch gh may name otherwise.
         */
        @Override
        public Option<BranchRef> busyBranch() {
            return Option.when(
                    !pullRequest.fromFork(), () -> new BranchRef.Local(new BranchName(pullRequest.headBranch())));
        }
    }
}
