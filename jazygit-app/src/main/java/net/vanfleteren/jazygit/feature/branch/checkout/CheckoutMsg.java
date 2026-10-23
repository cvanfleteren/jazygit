package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface CheckoutMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CheckoutUpdate.update(model, this);
    }

    /**
     * The user asked to check out a branch.
     */
    record Requested(BranchName branch) implements CheckoutMsg {}

    /**
     * The user asked to check out the remote branch {@code branch}: the local branch that follows it, or a new
     * one that does.
     */
    record RemoteRequested(RemoteBranch branch) implements CheckoutMsg {}

    /**
     * {@code cmd} ran {@code commands} and left a local branch checked out.
     */
    record CheckedOut(BranchCmd.ChecksOutBranch cmd, List<ExecutedCommand> commands) implements CheckoutMsg {}

    /**
     * Git refused the checkout {@code cmd}, which ran {@code commands}, because uncommitted changes would be
     * overwritten.
     */
    record NeedsStash(CheckoutCmd.Checkout cmd, List<ExecutedCommand> commands) implements CheckoutMsg {}

    /**
     * The user agreed to stash the uncommitted changes, check out and pop them again.
     */
    record StashConfirmed() implements CheckoutMsg {}

    /**
     * The user cancelled the checkout that needed the changes stashed.
     */
    record StashCancelled() implements CheckoutMsg {}

    /**
     * The user agreed to switch to the worktree that has the branch to check out checked out.
     */
    record SwitchWorktreeConfirmed() implements CheckoutMsg {}

    record SwitchWorktreeCancelled() implements CheckoutMsg {}
}
