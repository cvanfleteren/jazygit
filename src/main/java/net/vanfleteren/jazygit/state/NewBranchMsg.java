package net.vanfleteren.jazygit.state;

public sealed interface NewBranchMsg extends Msg {

    /**
     * The user asked for a new branch starting at {@code base}; the name is still to be entered.
     */
    record Requested(String base) implements NewBranchMsg {
    }

    record Cancelled() implements NewBranchMsg {
    }

    /**
     * The user entered the name of the new branch.
     */
    record Confirmed(String name) implements NewBranchMsg {
    }

    record Created(String name) implements NewBranchMsg {
    }

    record Failed(String name, String message) implements NewBranchMsg {
    }
}
