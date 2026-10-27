package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;

/**
 * What the branch features refuse up front.
 */
public sealed interface BranchRefusal extends AppError.Refusal {

    /**
     * An operation on the checked out branch was asked for, but HEAD is detached.
     */
    record NoBranchCheckedOut() implements BranchRefusal {
        @Override
        public String key() {
            return "error.detached";
        }
    }

    /**
     * Comparing a branch with the main branch was asked for, but it is the main branch.
     */
    record IsBaseBranch(BranchName branch) implements BranchRefusal {
        @Override
        public String key() {
            return "error.divergence.isBase";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * The upstream entered for a push is not {@code <remote> <branch>}.
     */
    record InvalidUpstream(String text) implements BranchRefusal {
        @Override
        public String key() {
            return "error.upstream.invalid";
        }

        @Override
        public List<Object> args() {
            return List.of(text.strip());
        }
    }

    /**
     * A push was asked for, but the branch is only behind its upstream: there is nothing to push, and a force push
     * would drop the commits of others.
     */
    record BehindUpstream(BranchName branch) implements BranchRefusal {
        @Override
        public String key() {
            return "error.push.behind";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * A pull of a branch that is not checked out was asked for, but its commits not yet pushed keep it from
     * being fast-forwarded.
     */
    record UnpushedCommits(BranchName branch) implements BranchRefusal {
        @Override
        public String key() {
            return "error.fastForward.unpushed";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * Deleting a remote branch was asked for, but it is the default branch of its remote.
     *
     * @param branch the short name of the remote branch, e.g. {@code origin/main}
     */
    record DefaultBranch(String branch) implements BranchRefusal {
        @Override
        public String key() {
            return "error.deleteBranch.default";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * Commits are not copied onto {@code target}, a branch, while the working tree has changes, which git would
     * refuse to cherry-pick onto.
     */
    record ChangesBeforeMove(String target) implements BranchRefusal {
        @Override
        public String key() {
            return "error.moveCommits.changes";
        }

        @Override
        public List<Object> args() {
            return List.of(target);
        }
    }

    /**
     * The commits of {@code branch} from {@code oldest} up are not moved, as {@code oldest} came in through
     * {@code merge} and so does its parent, {@code parent}: {@code branch} would be left on a commit of the merged
     * branch instead of one on its own line.
     */
    record MovedThroughMerge(Sha oldest, BranchName branch, Sha merge, Sha parent) implements BranchRefusal {
        @Override
        public String key() {
            return "error.moveCommits.throughMerge";
        }

        @Override
        public List<Object> args() {
            return List.of(oldest, branch, merge, parent);
        }
    }
}
