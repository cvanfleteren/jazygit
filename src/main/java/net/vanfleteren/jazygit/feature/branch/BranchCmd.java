package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.GitOperationCmd;

/**
 * Commands that act on branches. Each names the branch it acts on in its error message.
 */
public sealed interface BranchCmd extends GitOperationCmd {

    /**
     * The branch the command acts on.
     */
    Branch.Name branch();

    @Override
    default Option<String> subject() {
        return Option.of(branch().value());
    }

    /**
     * A command that keeps its branch busy while it runs, as it talks to the remote or deletes it.
     */
    sealed interface KeepsBranchBusy extends BranchCmd {

        @Override
        default Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch()));
        }
    }

    /**
     * A command that leaves a local branch checked out, new or not. Once it succeeded, the branches pane shows the
     * local branches, where that branch is then highlighted, also when it was started from the remote ones.
     */
    sealed interface ChecksOutBranch extends BranchCmd {

        @Override
        default Msg succeeded(List<ExecutedCommand> commands) {
            return new CheckoutMsg.CheckedOut(this, commands);
        }
    }

    /**
     * Deletes {@code remote} on its remote; a local branch that follows it is left alone.
     */
    record DeleteRemoteBranch(RemoteBranch remote) implements BranchCmd {

        @Override
        public Branch.Name branch() {
            return remote.branch();
        }

        @Override
        public Option<String> subject() {
            return Option.of(remote.shortName());
        }

        @Override
        public Operation operation() {
            return Operation.DELETE_BRANCH;
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Remote(remote));
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.deleteRemoteBranch(remote);
        }
    }

    /**
     * Creates the branch {@code branch} at {@code base} and switches to it.
     */
    record CreateBranch(Branch.Name branch, String base) implements ChecksOutBranch {

        @Override
        public Operation operation() {
            return Operation.NEW_BRANCH;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.checkoutNewBranch(branch, base);
        }
    }

    /**
     * Deletes {@code branch} where {@code scope} says; on its remote, that is {@code remote}.
     */
    record DeleteBranch(Branch.Name branch, RemoteBranch remote, DeleteScope scope) implements KeepsBranchBusy {

        @Override
        public Operation operation() {
            return Operation.DELETE_BRANCH;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.deleteBranch(branch, remote, scope);
        }
    }

    /**
     * Merges {@code branch} into the checked out branch.
     */
    record Merge(Branch.Name branch, MergeMode mode) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.MERGE;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.merge(branch, mode);
        }
    }

    /**
     * Rebases the checked out branch onto {@code branch}.
     */
    record Rebase(Branch.Name branch) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.REBASE;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.rebase(branch);
        }
    }

    /**
     * Checks out {@code branch}. When uncommitted changes are in the way, the user is offered to take them
     * along instead of being told it failed.
     */
    record Checkout(Branch.Name branch) implements ChecksOutBranch {

        @Override
        public Operation operation() {
            return Operation.CHECKOUT;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.checkout(branch);
        }

        @Override
        public Msg run(Env env) {
            return env.git().checkout(branch).fold(
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
    record CheckoutRemote(Branch.Name branch, RemoteBranch upstream) implements ChecksOutBranch {

        @Override
        public Operation operation() {
            return Operation.CHECKOUT;
        }

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
    record CheckoutWithStash(Branch.Name branch) implements ChecksOutBranch {

        @Override
        public Operation operation() {
            return Operation.CHECKOUT;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.checkoutWithStash(branch);
        }
    }

    /**
     * Pushes {@code branch} to {@code upstream}, which it then tracks.
     */
    record PushSettingUpstream(Branch.Name branch, RemoteBranch upstream) implements KeepsBranchBusy {

        @Override
        public boolean changesGitHub() {
            return true;
        }

        @Override
        public Operation operation() {
            return Operation.PUSH;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pushSettingUpstream(branch, upstream);
        }
    }

    /**
     * Pushes {@code branch} to {@code upstream}, the branch it tracks.
     */
    record Push(Branch.Name branch, RemoteBranch upstream, PushMode mode) implements KeepsBranchBusy {

        @Override
        public boolean changesGitHub() {
            return true;
        }

        @Override
        public Operation operation() {
            return Operation.PUSH;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.push(branch, upstream, mode);
        }
    }

    /**
     * Pulls {@code branch}, the checked out branch, from its upstream.
     */
    record Pull(Branch.Name branch) implements KeepsBranchBusy {

        @Override
        public Operation operation() {
            return Operation.PULL;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pull();
        }
    }

    /**
     * Fast-forwards {@code branch}, which is not checked out, to {@code upstream}, the branch it tracks.
     */
    record FastForward(Branch.Name branch, RemoteBranch upstream) implements KeepsBranchBusy {

        @Override
        public Operation operation() {
            return Operation.FAST_FORWARD;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.fastForward(branch, upstream);
        }
    }
}
