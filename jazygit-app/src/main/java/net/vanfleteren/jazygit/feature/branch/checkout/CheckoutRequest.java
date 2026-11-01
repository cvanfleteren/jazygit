package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeUpdate;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * A checkout the user asked for, which goes in the worktree shown, in the main worktree or in a new one: in a linked
 * worktree, the user is asked which (ADR 68). Checking out a pull request is one too, of its own feature.
 */
public interface CheckoutRequest {

    /**
     * What is checked out, as the popups name it.
     */
    String label();

    /**
     * Checks it out in the worktree shown.
     */
    Next here(Model model);

    /**
     * Asks where the new worktree that checks it out goes, as adding a worktree for it asks, with the same checks;
     * once added, the app switches to it without asking.
     */
    Next inNewWorktree(Model model);

    /**
     * The message that asks for it anew: the one the user started with, where there is one. It is handled in the main
     * worktree, once that is shown, where nothing asks where it goes, so it is checked out there.
     */
    Msg again();

    /**
     * The local branch {@code branch}, which exists and is checked out nowhere.
     */
    record Local(BranchName branch) implements CheckoutRequest {

        @Override
        public String label() {
            return branch.value();
        }

        @Override
        public Next here(Model model) {
            return Update.start(model, new Checkout(branch));
        }

        @Override
        public Next inNewWorktree(Model model) {
            return AddWorktreeUpdate.forBranch(model, branch, true);
        }

        @Override
        public Msg again() {
            return new CheckoutMsg.Requested(branch);
        }
    }

    /**
     * The new local branch {@code branch}, following the remote branch {@code upstream}.
     */
    record Remote(BranchName branch, RemoteBranch upstream) implements CheckoutRequest {

        @Override
        public String label() {
            return branch.value();
        }

        @Override
        public Next here(Model model) {
            return Update.start(model, new CheckoutRemote(branch, upstream));
        }

        @Override
        public Next inNewWorktree(Model model) {
            return AddWorktreeUpdate.askSwitching(
                    model, new WorktreeSource.Created(branch, new NewBranchStart.Following(upstream)));
        }

        /**
         * Checking out the remote branch, when the new branch is named as it is there; another name was typed, so it is
         * that one that is checked out.
         */
        @Override
        public Msg again() {
            return branch.equals(upstream.branch())
                    ? new CheckoutMsg.RemoteRequested(upstream)
                    : new CheckoutMsg.Placed(this);
        }
    }

    /**
     * The new branch {@code branch} at {@code base}, recording {@code parent} as the branch it was made from, if any.
     */
    record NewBranch(BranchName branch, Revision base, Option<BranchName> parent) implements CheckoutRequest {

        @Override
        public String label() {
            return branch.value();
        }

        @Override
        public Next here(Model model) {
            return Update.start(model, new NewBranchCmd(branch, base, parent));
        }

        @Override
        public Next inNewWorktree(Model model) {
            return AddWorktreeUpdate.askSwitching(
                    model, new WorktreeSource.Created(branch, new NewBranchStart.From(base, parent)));
        }

        @Override
        public Msg again() {
            return new CheckoutMsg.Placed(this);
        }
    }
}
