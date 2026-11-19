package net.vanfleteren.jazygit.xml;

import io.vavr.collection.List;
import io.vavr.control.Either;

/**
 * XML as it is written, read to be drawn (ADR 151): each element with the lines of its tags, its attributes, and its
 * elements, texts and comments in order, each with its lines. No DTD is read and nothing is fetched.
 */
public sealed interface XmlTree {

    /**
     * What is at the top of {@code text}: its root element, and the comments before and after it; or why it is not
     * well formed.
     */
    static Either<NotXml, List<XmlTree>> read(String text) {
        return XmlReader.read(text);
    }

    /**
     * The line it starts on, counted from 1.
     */
    int line();

    /**
     * The line it ends on, counted from 1.
     */
    int end();

    /**
     * An element.
     *
     * @param line     the line its start tag starts on
     * @param tagEnd   the line its start tag ends on, the {@code >} of it
     * @param end      the line it ends on, the {@code >} of its end tag, or of its start tag when it has none
     * @param name     its name as written, its prefix too
     * @param children its elements, texts and comments, in order
     */
    record Element(int line, int tagEnd, int end, String name, List<Attribute> attributes, List<XmlTree> children)
            implements XmlTree {}

    /**
     * An attribute, a declaration of a namespace too.
     *
     * @param value its value, its references undone
     */
    record Attribute(String name, String value) {}

    /**
     * Text that is not only white space, or a section of CDATA, trimmed: its {@code n}th line is on line
     * {@code line + n}.
     *
     * @param lines its lines, its references undone, without the indentation they share after the first
     */
    record Text(int line, int end, boolean cdata, List<String> lines) implements XmlTree {}

    /**
     * A comment.
     *
     * @param line  the line of its {@code <!--}
     * @param end   the line of its {@code -->}
     * @param first the line of the first of its {@code lines}
     * @param lines what it says, trimmed, its lines without the indentation they share after the first
     */
    record Comment(int line, int end, int first, List<String> lines) implements XmlTree {}
}
