package net.vanfleteren.jazygit.feature.branch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that act on branches.
 */
public sealed interface BranchCmd extends Cmd {

    /**
     * Creates the branch {@code name} at {@code base} and switches to it.
     */
    record CreateBranch(String name, String base) implements BranchCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.newBranch");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.createBranch(name, base).fold(this::failedWith, executed -> new NewBranchMsg.Created(name, executed));
        }

        @Override
        public Msg failed(String message) {
            return new NewBranchMsg.Failed(name, message);
        }
    }

    record DeleteBranch(String branch, DeleteScope scope) implements BranchCmd {
        /**
         * Where a branch is deleted.
         */
        public enum DeleteScope {
            LOCAL, REMOTE, BOTH
        }

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.deleteBranch");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.deleteBranch(branch, scope != DeleteScope.REMOTE, scope != DeleteScope.LOCAL)
                    .fold(this::failedWith, executed -> new DeleteBranchMsg.Deleted(branch, executed));
        }

        @Override
        public Msg failed(String message) {
            return new DeleteBranchMsg.Failed(branch, message);
        }
    }

    /**
     * Rebases the checked out branch onto {@code onto}.
     */
    record Rebase(String onto) implements BranchCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.rebase");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.rebase(onto).fold(this::failedWith, executed -> new RebaseMsg.Done(onto, executed));
        }

        @Override
        public Msg failed(String message) {
            return new RebaseMsg.Failed(onto, message);
        }
    }

    record Checkout(String branch) implements BranchCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.checkout");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.checkout(branch).fold(
                    failure -> failure.error() instanceof GitError.LocalChanges
                            ? new CheckoutMsg.NeedsStash(branch)
                            : failedWith(failure),
                    executed -> new CheckoutMsg.Done(branch, executed));
        }

        @Override
        public Msg failed(String message) {
            return new CheckoutMsg.Failed(branch, message);
        }
    }

    /**
     * Checks out {@code branch}, taking the uncommitted changes along through a stash.
     */
    record CheckoutWithStash(String branch) implements BranchCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.checkout");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.checkoutWithStash(branch).fold(this::failedWith, executed -> new CheckoutMsg.Done(branch, executed));
        }

        @Override
        public Msg failed(String message) {
            return new CheckoutMsg.Failed(branch, message);
        }
    }

    record Push(String branch, boolean forceWithLease) implements BranchCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.push");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.push(branch, forceWithLease).fold(this::failedWith, executed -> new PushMsg.Done(branch, executed));
        }

        @Override
        public Msg failed(String message) {
            return new PushMsg.Failed(branch, message);
        }
    }
}
