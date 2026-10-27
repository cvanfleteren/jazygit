package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.AppError;

/**
 * What the GitHub features refuse up front.
 */
public sealed interface GitHubRefusal extends AppError.Refusal {

    /**
     * A pull request was asked for, but the branch has diverged from {@code upstream}, which only a force push
     * gets its commits onto.
     */
    record Diverged(BranchName branch, RemoteBranch upstream) implements GitHubRefusal {
        @Override
        public String key() {
            return "error.pullRequest.diverged";
        }

        @Override
        public List<Object> args() {
            return List.of(branch, upstream);
        }
    }

    /**
     * A pull request was asked for, but no other branch is on the remote for it to merge into.
     */
    record NoBase(BranchName branch) implements GitHubRefusal {
        @Override
        public String key() {
            return "error.pullRequest.noBase";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, cannot be checked out, as it is of
     * another repository.
     */
    record OtherRepository(String pullRequest) implements GitHubRefusal {
        @Override
        public String key() {
            return "error.checkoutPr.otherRepository";
        }

        @Override
        public List<Object> args() {
            return List.of(pullRequest);
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, is not checked out while the working tree
     * has changes, which would come along.
     */
    record ChangesBeforeCheckout(String pullRequest) implements GitHubRefusal {
        @Override
        public String key() {
            return "error.checkoutPr.changes";
        }

        @Override
        public List<Object> args() {
            return List.of(pullRequest);
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, gets no worktree of its own, as its branch
     * is checked out here, which git checks out in one worktree at a time.
     */
    record PullRequestCheckedOutHere(String pullRequest) implements GitHubRefusal {
        @Override
        public String key() {
            return "error.checkoutPr.here";
        }

        @Override
        public List<Object> args() {
            return List.of(pullRequest);
        }
    }

    /**
     * Opening a branch on GitHub was asked for, but the branch is on no remote, or its upstream is gone.
     */
    record NotOnRemote(BranchName branch) implements GitHubRefusal {
        @Override
        public String key() {
            return "error.browse.notOnRemote";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }
}
