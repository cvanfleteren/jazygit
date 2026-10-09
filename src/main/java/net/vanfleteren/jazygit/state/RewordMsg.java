package net.vanfleteren.jazygit.state;

public sealed interface RewordMsg extends Msg {

    /**
     * The user asked to reword the commit at {@code index} in the commit log. Only the last commit
     * (index 0) can be reworded.
     */
    record Requested(int index) implements RewordMsg {
    }

    record Cancelled() implements RewordMsg {
    }

    record Confirmed(String summary, String description) implements RewordMsg {
    }

    record Done() implements RewordMsg {
    }

    record Failed(String message) implements RewordMsg {
    }
}
