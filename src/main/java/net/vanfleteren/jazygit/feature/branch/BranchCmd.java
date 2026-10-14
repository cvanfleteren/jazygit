package net.vanfleteren.jazygit.feature.branch;

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
    String branch();

    @Override
    default Option<String> subject() {
        return Option.of(branch());
    }

    /**
     * Creates the branch {@code branch} at {@code base} and switches to it.
     */
    record CreateBranch(String branch, String base) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.NEW_BRANCH;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.createBranch(branch, base);
        }
    }

    /**
     * Deletes {@code branch} where {@code scope} says; on its remote, that is {@code remote}.
     */
    record DeleteBranch(String branch, RemoteBranch remote, DeleteScope scope) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.DELETE_BRANCH;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.deleteBranch(branch, remote, scope);
        }
    }

    /**
     * Merges {@code branch} into the checked out branch.
     */
    record Merge(String branch, MergeMode mode) implements BranchCmd {

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
    record Rebase(String branch) implements BranchCmd {

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
    record Checkout(String branch) implements BranchCmd {

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
     * Checks out {@code branch}, taking the uncommitted changes along through a stash.
     */
    record CheckoutWithStash(String branch) implements BranchCmd {

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
    record PushSettingUpstream(String branch, RemoteBranch upstream) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.PUSH;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pushSettingUpstream(branch, upstream);
        }
    }

    /**
     * Pushes {@code branch} to {@code upstream}, the branch it tracks.
     */
    record Push(String branch, RemoteBranch upstream, PushMode mode) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.PUSH;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.push(branch, upstream, mode);
        }
    }

    /**
     * Pulls {@code branch}, the checked out branch, from its upstream.
     */
    record Pull(String branch) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.PULL;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pull();
        }
    }

    /**
     * Fast-forwards {@code branch}, which is not checked out, to {@code upstream}, the branch it tracks.
     */
    record FastForward(String branch, RemoteBranch upstream) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.FAST_FORWARD;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.fastForward(branch, upstream);
        }
    }
}
