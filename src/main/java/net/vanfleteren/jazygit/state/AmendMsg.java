package net.vanfleteren.jazygit.state;

public sealed interface AmendMsg extends Msg {

    /**
     * The user asked to amend the last commit with the staged files, or with all files if none is
     * staged; confirmation is still to be given.
     */
    record Requested() implements AmendMsg {
    }

    record Cancelled() implements AmendMsg {
    }

    record Confirmed() implements AmendMsg {
    }

    record Done() implements AmendMsg {
    }

    record Failed(String message) implements AmendMsg {
    }
}
