package net.vanfleteren.jazygit.state.popup;

import net.vanfleteren.jazygit.state.Model;

/**
 * A popup that is open and has the focus, with what it needs to know. At most one is open at a
 * time: it lives in {@link Model#popup()}. Each feature defines the popups it opens, as records
 * implementing this interface.
 */
public interface Popup {

    /**
     * Whether the user typed something into this popup that closing the app would lose.
     */
    default boolean holdsInput() {
        return false;
    }

    /**
     * This popup once what was learned of how long checks take changes from what {@code before} knows; as it is,
     * unless it shows something it learned from them.
     */
    default Popup checkTimesChanging(Model before) {
        return this;
    }
}
