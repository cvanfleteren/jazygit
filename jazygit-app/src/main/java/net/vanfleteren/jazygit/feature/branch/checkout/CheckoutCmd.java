package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Checking out a branch: a local one, one of the remote ones, or one with the uncommitted changes taken along.
 */
public sealed interface CheckoutCmd extends BranchCmd.ChecksOutBranch {

    Operation CHECKOUT =
            Operation.of("log.title.checkout", "error.checkout.failed").waitingForUnfinished();

    @Override
    default Operation operation() {
        return CHECKOUT;
    }

    /**
     * Checks out {@code branch}. When uncommitted changes are in the way, the user is offered to take them
     * along instead of being told it failed.
     */
    record Checkout(Branch.Name branch) implements CheckoutCmd {

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.checkout(branch);
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .checkout(branch)
                    .fold(
                            failure -> failure.error() instanceof GitError.LocalChanges
                                    ? new CheckoutMsg.NeedsStash(this, failure.commands())
                                    : failedWith(failure),
                            this::succeeded);
        }
    }

    /**
     * Creates the local branch {@code branch} at the remote branch {@code upstream}, following it, and checks it
     * out. The remote branch shows as busy meanwhile, as the local one does not exist yet.
     */
    record CheckoutRemote(Branch.Name branch, RemoteBranch upstream) implements CheckoutCmd {

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Remote(upstream));
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.checkoutNewTrackingBranch(branch, upstream);
        }
    }

    /**
     * Checks out {@code branch}, taking the uncommitted changes along through a stash.
     */
    record CheckoutWithStash(Branch.Name branch) implements CheckoutCmd {

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.checkoutWithStash(branch);
        }
    }
}
