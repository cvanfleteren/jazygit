package net.vanfleteren.jazygit.json;

import io.vavr.control.Option;

/**
 * Why text could not be read as a {@link JsonTree} (ADR 122), told as data, for whoever shows it to write in words.
 */
public sealed interface NotJson {

    /**
     * The text holds nothing but white space.
     */
    record Nothing() implements NotJson {}

    /**
     * Reading stopped at a place that is not JSON.
     *
     * @param line   the line it stopped on, counted from 1
     * @param column the column it stopped at, counted from 1: that of the last character of what it read there
     * @param found  the character of the text there; none where the text stops
     */
    record Broken(int line, int column, Option<String> found) implements NotJson {}

    /**
     * The text holds more after its one value.
     *
     * @param line the line the more is on
     */
    record MoreAfter(int line) implements NotJson {}

    /**
     * Its values are nested deeper than they are read.
     *
     * @param depth how deep they are read
     */
    record TooDeep(int depth) implements NotJson {}
}
