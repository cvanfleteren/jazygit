package net.vanfleteren.jazygit.state;

public sealed interface CheckoutMsg extends Msg {

    /**
     * The user asked to check out a branch.
     */
    record Requested(String branch) implements CheckoutMsg {
    }

    record Done(String branch) implements CheckoutMsg {
    }

    record Failed(String branch, String message) implements CheckoutMsg {
    }
}
