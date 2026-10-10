package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.GitInfoProvider;
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
        public Msg run(GitInfoProvider git) {
            git.createBranch(name, base);
            return new NewBranchMsg.Created(name);
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
        public Msg run(GitInfoProvider git) {
            git.deleteBranch(branch, scope != DeleteScope.REMOTE, scope != DeleteScope.LOCAL);
            return new DeleteBranchMsg.Deleted(branch);
        }

        @Override
        public Msg failed(String message) {
            return new DeleteBranchMsg.Failed(branch, message);
        }
    }

    record Checkout(String branch) implements BranchCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.checkout(branch);
            return new CheckoutMsg.Done(branch);
        }

        @Override
        public Msg failed(String message) {
            return new CheckoutMsg.Failed(branch, message);
        }
    }

    record Push(String branch, boolean forceWithLease) implements BranchCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.push(branch, forceWithLease);
            return new PushMsg.Done(branch);
        }

        @Override
        public Msg failed(String message) {
            return new PushMsg.Failed(branch, message);
        }
    }
}
