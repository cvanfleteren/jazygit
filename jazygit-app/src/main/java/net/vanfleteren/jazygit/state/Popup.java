package net.vanfleteren.jazygit.state;

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
}
