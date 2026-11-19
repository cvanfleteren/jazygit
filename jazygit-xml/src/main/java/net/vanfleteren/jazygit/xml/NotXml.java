package net.vanfleteren.jazygit.xml;

/**
 * Why text could not be read as {@link XmlTree} (ADR 151), told as data, for whoever shows it to write in words.
 */
public sealed interface NotXml {

    /**
     * The text holds no element.
     */
    record Nothing() implements NotXml {}

    /**
     * Reading stopped at a place where the text is not well formed.
     *
     * @param line   the line it stopped on, counted from 1
     * @param column the column it stopped at, counted from 1
     */
    record Broken(int line, int column, Problem problem) implements NotXml {}

    /**
     * Its elements are nested deeper than they are read.
     *
     * @param depth how deep they are read
     */
    record TooDeep(int depth) implements NotXml {}

    /**
     * What is wrong where reading stopped.
     */
    sealed interface Problem {

        /**
         * What {@link Expected} says where a name has to be.
         */
        String NAME = "name";

        /**
         * What {@link Expected} says where a quoted value has to be.
         */
        String QUOTED = "quoted";

        /**
         * Something else was written where {@code what} has to be.
         *
         * @param what what has to be there: {@code '>'}, or {@link #NAME} or {@link #QUOTED}
         */
        record Expected(String what) implements Problem {}

        /**
         * An end tag, {@code </close>}, where the element {@code open} is to be closed.
         */
        record Closes(String open, String close) implements Problem {}

        /**
         * An end tag, {@code </close>}, outside every element.
         */
        record ClosesNothing(String close) implements Problem {}

        /**
         * The text ends before the element {@code name} is closed.
         */
        record EndsInside(String name) implements Problem {}

        /**
         * An element with the attribute {@code name} written twice.
         */
        record Twice(String name) implements Problem {}

        /**
         * An element, or text, after the root element.
         */
        record MoreAfter() implements Problem {}

        /**
         * Text outside the root element.
         */
        record TextOutside() implements Problem {}

        /**
         * A {@code <} in the value of an attribute.
         */
        record LessThan() implements Problem {}

        /**
         * An {@code &} that starts no reference.
         */
        record Ampersand() implements Problem {}
    }
}
