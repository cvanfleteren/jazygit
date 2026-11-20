package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A field of the form that edits an entry of a list in the settings panel (ADR 164): what it shows of the entry, and
 * how a value the user gives it changes the entry.
 *
 * @param <E> what an entry is
 */
public sealed interface Field<E> {

    /**
     * The message key of its label.
     */
    String labelKey();

    /**
     * A line of text, which a text input edits.
     *
     * @param labelKey the message key of its label
     * @param get      its text in an entry
     * @param set      the entry with the text typed, as typed
     */
    record Text<E>(String labelKey, Function<E, String> get, BiFunction<E, String, E> set) implements Field<E> {}

    /**
     * On or off, which picking the field turns the other way.
     *
     * @param labelKey the message key of its label
     * @param get      whether it is on in an entry
     * @param set      the entry with it on or off
     */
    record Toggle<E>(String labelKey, Predicate<E> get, BiFunction<E, Boolean, E> set) implements Field<E> {}

    /**
     * One character, or none, as a shortcut is: a text input edits it, and only its first character is kept.
     *
     * @param labelKey the message key of its label
     * @param get      its character in an entry, if any
     * @param set      the entry with the character, none when the input was left empty
     */
    record Letter<E>(String labelKey, Function<E, Option<Character>> get, BiFunction<E, Option<Character>, E> set)
            implements Field<E> {}
}
