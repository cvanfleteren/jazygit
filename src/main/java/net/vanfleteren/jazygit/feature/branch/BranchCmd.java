package net.vanfleteren.jazygit.feature.branch;

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
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Commands that act on branches. Each names the branch it acts on in its error message.
 */
public sealed interface BranchCmd extends OperationCmd {

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
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
            return git.createBranch(branch, base);
        }
    }

    record DeleteBranch(String branch, DeleteScope scope) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.DELETE_BRANCH;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
            return git.deleteBranch(branch, scope);
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
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
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
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
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
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
            return git.checkout(branch);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.checkout(branch).fold(
                    failure -> failure.error() instanceof GitError.LocalChanges
                            ? new CheckoutMsg.NeedsStash(branch)
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
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
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
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
            return git.pushSettingUpstream(branch, upstream);
        }
    }

    record Push(String branch, PushMode mode) implements BranchCmd {

        @Override
        public Operation operation() {
            return Operation.PUSH;
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Either<? extends GitFailure<?>, List<String>> perform(GitInfoProvider git) {
            return git.push(branch, mode);
        }
    }
}
