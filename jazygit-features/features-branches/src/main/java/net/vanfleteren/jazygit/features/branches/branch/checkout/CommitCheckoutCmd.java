package net.vanfleteren.jazygit.features.branches.branch.checkout;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Checking out a commit, with HEAD detached at it (ADR 137). Unlike a checkout of a branch, it leaves the branches
 * pane on the tab it is on.
 */
public sealed interface CommitCheckoutCmd extends GitOperationCmd {

    /**
     * The commit checked out.
     */
    Sha sha();

    @Override
    default Operation operation() {
        return CheckoutCmd.CHECKOUT;
    }

    @Override
    default Option<String> subject() {
        return Option.of(sha().shortSha());
    }

    /**
     * Checks out {@code sha}.
     */
    record CheckoutCommit(Sha sha) implements CommitCheckoutCmd, StashableCheckout {

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.checkout().commit(sha);
        }

        @Override
        public String target() {
            return sha.shortSha();
        }

        @Override
        public CheckoutCommitWithStash withStash() {
            return new CheckoutCommitWithStash(sha);
        }
    }

    /**
     * Checks out {@code sha}, taking the uncommitted changes along through a stash.
     */
    record CheckoutCommitWithStash(Sha sha) implements CommitCheckoutCmd {

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.checkout().commitWithStash(sha);
        }
    }
}
