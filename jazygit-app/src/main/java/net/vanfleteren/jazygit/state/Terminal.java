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
}
