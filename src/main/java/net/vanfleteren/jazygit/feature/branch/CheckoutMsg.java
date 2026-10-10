package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;

public sealed interface CheckoutMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CheckoutUpdate.update(model, this);
    }

    /**
     * The user asked to check out a branch.
     */
    record Requested(String branch) implements CheckoutMsg {
    }

    /**
     * Git refused to check out {@code branch} because uncommitted changes would be overwritten.
     */
    record NeedsStash(String branch) implements CheckoutMsg {
    }

    /**
     * The user agreed to stash the uncommitted changes, check out and pop them again.
     */
    record StashConfirmed() implements CheckoutMsg {
    }

    /**
     * The user cancelled the checkout that needed the changes stashed.
     */
    record StashCancelled() implements CheckoutMsg {
    }

    record Done(String branch) implements CheckoutMsg {
    }

    record Failed(String branch, String message) implements CheckoutMsg {
    }
}
