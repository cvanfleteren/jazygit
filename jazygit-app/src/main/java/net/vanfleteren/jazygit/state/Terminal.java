package net.vanfleteren.jazygit.state;

import java.util.function.Supplier;

/**
 * The terminal the app is shown on, which it hands over to a program the user works with, like an editor. The app
 * does that, as it owns the terminal; a {@link Cmd} only asks.
 */
public interface Terminal {

    /**
     * Owns no terminal: runs the program as it is.
     */
    Terminal NONE = Supplier::get;

    /**
     * Runs {@code program} with the terminal to itself, and shows the app again once it is done. Nothing is drawn
     * and no key is read meanwhile.
     */
    <T> T handOver(Supplier<T> program);

    /**
     * Asks the terminal to copy {@code text} to the clipboard (ADR 94), and says how that went: whether the terminal
     * did copy cannot be told. One the app does not own copies nothing.
     */
    default Copy copy(String text) {
        return Copy.NOT_OWNED;
    }

    /**
     * How asking the terminal to copy went (ADR 94).
     */
    enum Copy {
        /**
         * The terminal was asked to copy; whether it did cannot be told.
         */
        ASKED,
        /**
         * The app owns no terminal, so it could not ask one.
         */
        NOT_OWNED,
        /**
         * The terminal could not be written to.
         */
        FAILED
    }
}
