package net.vanfleteren.jazygit.features.branches.branch.checkout;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Checking out a branch: a local one, one of the remote ones, or one with the uncommitted changes taken along.
 */
public sealed interface CheckoutCmd extends BranchCmd.ChecksOutBranch {

    Operation CHECKOUT = Operation.of("branches.log.title.checkout", "branches.error.checkout.failed")
            .waitingForUnfinished();

    @Override
    default Operation operation() {
        return CHECKOUT;
    }

    /**
     * Checks out {@code branch}. When uncommitted changes are in the way, the user is offered to take them
     * along instead of being told it failed.
     */
    record Checkout(BranchName branch) implements CheckoutCmd, StashableCheckout {

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.checkout().branch(branch);
        }

        @Override
        public String target() {
            return branch.value();
        }

        @Override
        public CheckoutWithStash withStash() {
            return new CheckoutWithStash(branch);
        }
    }

    /**
     * Creates the local branch {@code branch} at the remote branch {@code upstream}, following it, and checks it
     * out. The remote branch shows as busy meanwhile, as the local one does not exist yet.
     */
    record CheckoutRemote(BranchName branch, RemoteBranch upstream) implements CheckoutCmd {

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Remote(upstream));
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.checkout().newTrackingBranch(branch, upstream);
        }
    }

    /**
     * Checks out {@code branch}, taking the uncommitted changes along through a stash.
     */
    record CheckoutWithStash(BranchName branch) implements CheckoutCmd {

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.checkout().branchWithStash(branch);
        }
    }
}
