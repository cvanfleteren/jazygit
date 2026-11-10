package net.vanfleteren.jazygit.state;

/**
 * What became of writing the index anew (ADR 93, ADR 97).
 */
public sealed interface IndexMsg extends Msg {

    /**
     * The index was written anew where it no longer matched the working tree, or could not be ({@code written} false):
     * either way, nothing shown changes.
     */
    record Refreshed(boolean written) implements IndexMsg {

        @Override
        public Update.Next apply(Model model) {
            return IndexUpdate.refreshed(model, written);
        }
    }
}
