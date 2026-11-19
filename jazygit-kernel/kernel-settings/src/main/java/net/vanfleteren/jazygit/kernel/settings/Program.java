package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.control.Option;

/**
 * A program the user set in the settings file, as an editor (ADR 110) or an agent (ADR 161) is: run without a shell,
 * handed the terminal or started and left running, and chosen in a menu by its letter.
 */
public interface Program {

    /**
     * What the menus and the messages call it.
     */
    String name();

    /**
     * Whether it is handed the terminal, rather than started and left running.
     */
    boolean terminal();

    /**
     * The letter that chooses it in a menu, if the user gave it one.
     */
    Option<Character> shortcut();
}
