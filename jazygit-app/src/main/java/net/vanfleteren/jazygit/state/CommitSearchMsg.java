package net.vanfleteren.jazygit.state;

public sealed interface CommitSearchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommitSearchUpdate.update(model, this);
    }

    /**
     * The user opened the line above the commits pane to type a search in.
     */
    record Opened() implements CommitSearchMsg {}

    /**
     * The user changed the text of the open line to {@code text}.
     */
    record Edited(String text) implements CommitSearchMsg {}

    /**
     * The user closed the line without running what it holds.
     */
    record Cancelled() implements CommitSearchMsg {}

    /**
     * The user ran the search the open line holds.
     */
    record Submitted() implements CommitSearchMsg {}

    /**
     * The user cleared the search, to see the log again.
     */
    record Cleared() implements CommitSearchMsg {}
}
