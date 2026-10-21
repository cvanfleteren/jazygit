package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;

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
    Branch.Name directory();

    /**
     * The branch that is busy while the worktree is added, if any.
     */
    default Option<BranchRef> busyBranch() {
        return Option.none();
    }

    /**
     * {@code branch}, which exists and is checked out nowhere.
     */
    record Existing(Branch.Name branch) implements WorktreeSource {

        @Override
        public String label() {
            return branch.value();
        }

        @Override
        public Branch.Name directory() {
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
    record Created(Branch.Name name, NewBranch from) implements WorktreeSource {

        @Override
        public String label() {
            return name.value();
        }

        @Override
        public Branch.Name directory() {
            return name;
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(name));
        }
    }

    /**
     * {@code start}, a branch, a commit or any other revision, on no branch.
     */
    record Detached(String start) implements WorktreeSource {

        @Override
        public String label() {
            return start;
        }

        @Override
        public Branch.Name directory() {
            return new Branch.Name(start);
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
        public Branch.Name directory() {
            return new Branch.Name(pullRequest.fromFork() ? "pr-" + pullRequest.number() : pullRequest.headBranch());
        }
    }
}
