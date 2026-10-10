package net.vanfleteren.jazygit.feature.branch;

import java.util.Optional;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.LocalChangesException;
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.newBranch");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new NewBranchMsg.Created(name, git.createBranch(name, base));
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.deleteBranch");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new DeleteBranchMsg.Deleted(branch,
                    git.deleteBranch(branch, scope != DeleteScope.REMOTE, scope != DeleteScope.LOCAL));
        }

        @Override
        public Msg failed(String message) {
            return new DeleteBranchMsg.Failed(branch, message);
        }
    }

    record Checkout(String branch) implements BranchCmd {

        @Override
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.checkout");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            try {
                return new CheckoutMsg.Done(branch, git.checkout(branch));
            } catch (LocalChangesException e) {
                return new CheckoutMsg.NeedsStash(branch);
            }
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.checkout");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new CheckoutMsg.Done(branch, git.checkoutWithStash(branch));
        }

        @Override
        public Msg failed(String message) {
            return new CheckoutMsg.Failed(branch, message);
        }
    }

    record Push(String branch, boolean forceWithLease) implements BranchCmd {

        @Override
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.push");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new PushMsg.Done(branch, git.push(branch, forceWithLease));
        }

        @Override
        public Msg failed(String message) {
            return new PushMsg.Failed(branch, message);
        }
    }
}
