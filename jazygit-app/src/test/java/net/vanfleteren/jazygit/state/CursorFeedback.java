package net.vanfleteren.jazygit.state;

import java.util.function.Consumer;

/**
 * Stands in for the app around the list of a popup in a test: it keeps where the user moved to in the list, which
 * {@link #on} puts in the model the popup is rendered from, and hands the other messages on.
 */
public final class CursorFeedback implements Consumer<Msg> {

    private final Consumer<Msg> others;
    private ListCursor cursor = ListCursor.START;

    /**
     * @param others receives the messages other than moving in the list
     */
    public CursorFeedback(Consumer<Msg> others) {
        this.others = others;
    }

    @Override
    public void accept(Msg msg) {
        if (msg instanceof Msg.ListCursorChanged(ListCursor moved)) {
            cursor = moved;
        } else {
            others.accept(msg);
        }
    }

    /**
     * {@code model} with the list of its popup where the user moved to.
     */
    public Model on(Model model) {
        return model.popup().isDefined() ? model.withListCursor(cursor) : model;
    }

    /**
     * Forgets where the user moved to, as when the popup opens again.
     */
    public void reset() {
        cursor = ListCursor.START;
    }
}
