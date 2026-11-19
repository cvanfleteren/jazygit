package net.vanfleteren.jazygit.lib.xml;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import java.util.Arrays;
import java.util.stream.IntStream;
import net.vanfleteren.jazygit.lib.xml.NotXml.Problem;
import net.vanfleteren.jazygit.lib.xml.XmlTree.Attribute;
import net.vanfleteren.jazygit.lib.xml.XmlTree.Comment;
import net.vanfleteren.jazygit.lib.xml.XmlTree.Element;
import net.vanfleteren.jazygit.lib.xml.XmlTree.Text;

/**
 * Reads XML by hand (ADR 151): what is well formed, each node with the lines it is written on. No DTD is read, so no
 * entity is declared and nothing is fetched; a reference to an entity other than XML's own five stays as written.
 * Not StAX, which says where an event ends, not where it starts, and stops at {@code &nbsp;}.
 */
final class XmlReader {

    // Bounds the recursion of element: deeper XML is an error, not a StackOverflowError, as JSON is (ADR 122).
    static final int MAX_DEPTH = 500;

    private static final Map<String, String> ENTITIES =
            HashMap.of("amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'");

    private final String text;
    private final int[] starts;
    // Where the text starts, after a byte order mark: the only place the declaration may be.
    private final int begin;
    private int at;

    private XmlReader(String text) {
        this.text = text;
        this.starts = IntStream.concat(
                        IntStream.of(0),
                        IntStream.range(0, text.length())
                                .filter(index -> text.charAt(index) == '\n')
                                .map(index -> index + 1))
                .toArray();
        this.begin = text.startsWith("﻿") ? 1 : 0;
        this.at = begin;
    }

    static Either<NotXml, List<XmlTree>> read(String text) {
        try {
            XmlReader reader = new XmlReader(text);
            reader.allowed();
            return reader.document();
        } catch (NotRead e) {
            return Either.left(e.why);
        }
    }

    /**
     * Stops at the first character XML does not allow anywhere: a control character other than a tab or a line break, a
     * half of a surrogate pair, U+FFFE or U+FFFF.
     */
    private void allowed() {
        int index = 0;
        while (index < text.length()) {
            int c = text.codePointAt(index);
            if (!allowed(c)) {
                throw broken(index, new Problem.Forbidden(c));
            }
            index += Character.charCount(c);
        }
    }

    private static boolean allowed(int c) {
        return c == 0x9
                || c == 0xA
                || c == 0xD
                || (c >= 0x20 && c <= 0xD7FF)
                || (c >= 0xE000 && c <= 0xFFFD)
                || (c >= 0x10000 && c <= 0x10FFFF);
    }

    private Either<NotXml, List<XmlTree>> document() {
        // A vector, not a list: appending to a list copies it, which made thousands of siblings take seconds.
        Vector<XmlTree> top = Vector.empty();
        boolean rooted = false;
        while (true) {
            skipSpace();
            if (at >= text.length()) {
                return rooted ? Either.right(top.toList()) : Either.left(new NotXml.Nothing());
            }
            if (opens("<!--")) {
                top = top.append(comment());
            } else if (opens("<?")) {
                instruction();
            } else if (opens("<!DOCTYPE") && !rooted) {
                doctype();
            } else if (opens("</")) {
                int from = at;
                at += 2;
                throw broken(from, new Problem.ClosesNothing(name()));
            } else if (rooted) {
                throw broken(at, new Problem.MoreAfter());
            } else if (opens("<")) {
                top = top.append(element(1));
                rooted = true;
            } else {
                throw broken(at, new Problem.TextOutside());
            }
        }
    }

    /**
     * The element at {@code at}, its {@code <}, {@code depth} elements deep.
     */
    private Element element(int depth) {
        if (depth > MAX_DEPTH) {
            throw new NotRead(new NotXml.TooDeep(MAX_DEPTH));
        }
        int from = at;
        at++;
        String name = name();
        Vector<Attribute> attributes = Vector.empty();
        while (true) {
            int before = at;
            skipSpace();
            boolean spaced = at > before;
            if (at >= text.length()) {
                throw broken(at, new Problem.EndsInside(name));
            }
            if (opens("/>")) {
                at += 2;
                int line = lineOf(at - 1);
                return new Element(lineOf(from), line, line, name, attributes.toList(), List.empty());
            }
            if (opens(">")) {
                at++;
                int tagEnd = lineOf(at - 1);
                return children(lineOf(from), tagEnd, name, attributes.toList(), depth);
            }
            if (!spaced) {
                throw broken(at, new Problem.NoSpace());
            }
            int attribute = at;
            String key = name(">");
            if (attributes.exists(written -> written.name().equals(key))) {
                throw broken(attribute, new Problem.Twice(key));
            }
            skipSpace();
            expect("=");
            skipSpace();
            attributes = attributes.append(new Attribute(key, value()));
        }
    }

    /**
     * The rest of the element {@code name}: what it holds, from {@code at}, after its start tag, up to its end tag.
     */
    private Element children(int line, int tagEnd, String name, List<Attribute> attributes, int depth) {
        Vector<XmlTree> children = Vector.empty();
        while (true) {
            if (at >= text.length()) {
                throw broken(at, new Problem.EndsInside(name));
            }
            if (opens("</")) {
                int from = at;
                at += 2;
                String close = name();
                skipSpace();
                if (!close.equals(name)) {
                    throw broken(from, new Problem.Closes(name, close));
                }
                expect(">");
                return new Element(line, tagEnd, lineOf(at - 1), name, attributes, children.toList());
            }
            if (opens("<!--")) {
                children = children.append(comment());
            } else if (opens("<![CDATA[")) {
                children = children.appendAll(cdata());
            } else if (opens("<?")) {
                instruction();
            } else if (opens("<")) {
                children = children.append(element(depth + 1));
            } else {
                children = children.appendAll(text());
            }
        }
    }

    /**
     * The text from {@code at} up to the next {@code <}, if it holds more than white space.
     */
    private List<XmlTree> text() {
        int from = at;
        int until = text.indexOf('<', at);
        at = until < 0 ? text.length() : until;
        int end = text.indexOf("]]>", from, at);
        if (end >= 0) {
            throw broken(end, new Problem.CdataEnd());
        }
        return textOf(from, at, false);
    }

    /**
     * The text written from {@code from} up to {@code until}, its references undone; a reference to a line break stays
     * in the line it is written on.
     */
    private String decoded(int from, int until) {
        StringBuilder decoded = new StringBuilder();
        int index = from;
        while (index < until) {
            char c = text.charAt(index);
            if (c == '&') {
                index = reference(index, decoded);
            } else {
                decoded.append(c);
                index++;
            }
        }
        return decoded.toString();
    }

    private List<XmlTree> cdata() {
        int from = at + "<![CDATA[".length();
        int until = text.indexOf("]]>", from);
        if (until < 0) {
            throw broken(at, new Problem.Expected("]]>"));
        }
        at = until + 3;
        return textOf(from, until, true);
    }

    /**
     * The text written from {@code from} up to {@code until}, trimmed, if it holds more than white space: its line is
     * that of the first character that is no white space in the source. It is split into the lines of the source first,
     * then the references of each are undone, unless it is a section of {@code cdata}: so its {@code n}th line is on line
     * {@code line + n}, also where a reference stands for a line break.
     */
    private List<XmlTree> textOf(int from, int until, boolean cdata) {
        int first = from;
        while (first < until && space(text.charAt(first))) {
            first++;
        }
        if (first == until) {
            return List.empty();
        }
        int last = until - 1;
        while (last > first && space(text.charAt(last))) {
            last--;
        }
        List<Integer> heads = List.ofAll(IntStream.rangeClosed(first + 1, last)
                        .filter(index -> text.charAt(index - 1) == '\n')
                        .boxed())
                .prepend(first);
        List<Integer> ends = heads.tail().map(head -> head - 1).append(last + 1);
        int shared = indentation(heads.tail().zip(ends.tail()).map(range -> text.substring(range._1, range._2)));
        List<String> lines = heads.zip(ends).zipWithIndex((range, at) -> {
            int start = at == 0 ? range._1 : Math.min(range._1 + shared, end(range._1, range._2));
            int end = end(start, range._2);
            return cdata ? text.substring(start, end) : decoded(start, end);
        });
        return List.of(new Text(lineOf(first), lineOf(last), cdata, lines));
    }

    /**
     * Where the line from {@code start} up to {@code end} ends without the white space at its end, and a carriage
     * return.
     */
    private int end(int start, int end) {
        int until = end;
        while (until > start && space(text.charAt(until - 1))) {
            until--;
        }
        return until;
    }

    private Comment comment() {
        int from = at;
        int inside = at + "<!--".length();
        int until = text.indexOf("-->", inside);
        if (until < 0) {
            throw broken(from, new Problem.Expected("-->"));
        }
        int dashes = text.indexOf("--", inside);
        if (dashes < until) {
            throw broken(dashes, new Problem.DashesInComment());
        }
        at = until + 3;
        int first = inside;
        while (first < until && space(text.charAt(first))) {
            first++;
        }
        return new Comment(
                lineOf(from),
                lineOf(until),
                lineOf(Math.min(first, until)),
                lines(strip(text.substring(inside, until))));
    }

    /**
     * Reads past an instruction, or the declaration at the start of the text: {@code <?xml} anywhere else is not well
     * formed.
     */
    private void instruction() {
        int from = at;
        int until = text.indexOf("?>", at + 2);
        if (until < 0) {
            throw broken(at, new Problem.Expected("?>"));
        }
        at += 2;
        String target = name();
        if (target.equalsIgnoreCase("xml") && from != begin) {
            throw broken(from, new Problem.Declaration());
        }
        at = until + 2;
    }

    /**
     * Reads past a DOCTYPE, its internal subset and the strings, comments and instructions in it too, without reading
     * what it declares.
     */
    private void doctype() {
        int from = at;
        int depth = 0;
        char quote = 0;
        for (int index = at + "<!DOCTYPE".length(); index < text.length(); index++) {
            char c = text.charAt(index);
            if (quote == 0 && (text.startsWith("<!--", index) || text.startsWith("<?", index))) {
                String closer = text.startsWith("<!--", index) ? "-->" : "?>";
                int end = text.indexOf(closer, index + 2);
                if (end < 0) {
                    throw broken(index, new Problem.Expected(closer));
                }
                // Onto the last character of what closes it: the loop goes on after it.
                index = end + closer.length() - 1;
            } else if (quote != 0) {
                quote = c == quote ? 0 : quote;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '>' && depth <= 0) {
                at = index + 1;
                return;
            }
        }
        throw broken(from, new Problem.Expected(">"));
    }

    /**
     * The quoted value of an attribute at {@code at}, its references undone.
     */
    private String value() {
        char quote = at < text.length() ? text.charAt(at) : 0;
        if (quote != '"' && quote != '\'') {
            throw broken(at, new Problem.Expected(Problem.QUOTED));
        }
        int from = at;
        StringBuilder value = new StringBuilder();
        int index = at + 1;
        while (true) {
            if (index >= text.length()) {
                throw broken(from, new Problem.Expected(String.valueOf(quote)));
            }
            char c = text.charAt(index);
            if (c == quote) {
                at = index + 1;
                return value.toString();
            }
            if (c == '<') {
                throw broken(index, new Problem.LessThan());
            }
            if (c == '&') {
                index = reference(index, value);
            } else {
                value.append(c);
                index++;
            }
        }
    }

    /**
     * Appends what the reference at {@code index}, its {@code &}, stands for to {@code to}, and gives the index after
     * it: a character, one of the five entities of XML, or any other entity as it is written.
     */
    private int reference(int index, StringBuilder to) {
        int semicolon = text.indexOf(';', index);
        String inside = semicolon < 0 ? "" : text.substring(index + 1, semicolon);
        if (inside.startsWith("#")) {
            boolean hex = inside.startsWith("#x");
            String digits = inside.substring(hex ? 2 : 1);
            // Digits of ASCII only: Character.digit takes those of every script, as fullwidth ones.
            if (!digits.isEmpty() && digits.chars().allMatch(c -> c < 0x80 && Character.digit(c, hex ? 16 : 10) >= 0)) {
                int c;
                try {
                    c = Integer.parseInt(digits, hex ? 16 : 10);
                } catch (NumberFormatException e) {
                    throw broken(index, new Problem.Ampersand());
                }
                if (!allowed(c)) {
                    throw broken(index, new Problem.Forbidden(c));
                }
                to.appendCodePoint(c);
                return semicolon + 1;
            }
        } else if (!inside.isEmpty()
                && nameStart(inside.charAt(0))
                && inside.chars().allMatch(XmlReader::namePart)) {
            to.append(ENTITIES.get(inside).getOrElse(() -> "&" + inside + ";"));
            return semicolon + 1;
        }
        throw broken(index, new Problem.Ampersand());
    }

    /**
     * The name at {@code at}.
     */
    private String name() {
        return name(Problem.NAME);
    }

    /**
     * The name at {@code at}, or what {@code expected} says when there is none.
     */
    private String name(String expected) {
        int from = at;
        if (at >= text.length() || !nameStart(text.charAt(at))) {
            throw broken(at, new Problem.Expected(expected));
        }
        while (at < text.length() && namePart(text.charAt(at))) {
            at++;
        }
        return text.substring(from, at);
    }

    private static boolean nameStart(int c) {
        return Character.isLetter(c) || c == '_' || c == ':' || c > 0x7F && !Character.isWhitespace(c);
    }

    private static boolean namePart(int c) {
        return nameStart(c) || Character.isDigit(c) || c == '-' || c == '.';
    }

    private void expect(String token) {
        if (!opens(token)) {
            throw broken(at, new Problem.Expected(token));
        }
        at += token.length();
    }

    private boolean opens(String token) {
        return text.startsWith(token, at);
    }

    private void skipSpace() {
        while (at < text.length() && space(text.charAt(at))) {
            at++;
        }
    }

    /**
     * Whether {@code c} is white space as XML has it: a space, a tab or a line break, not every space of Unicode.
     */
    private static boolean space(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /**
     * {@code text} without the white space of XML at its start and its end.
     */
    private static String strip(String text) {
        int from = 0;
        int until = text.length();
        while (from < until && space(text.charAt(from))) {
            from++;
        }
        while (until > from && space(text.charAt(until - 1))) {
            until--;
        }
        return text.substring(from, until);
    }

    /**
     * How much indentation {@code lines} share: the least white space any that holds anything starts with.
     */
    private static int indentation(List<String> lines) {
        return lines.filter(line -> !strip(line).isEmpty())
                .map(XmlReader::leading)
                .min()
                .getOrElse(0);
    }

    /**
     * How much white space {@code line} starts with.
     */
    private static int leading(String line) {
        int at = 0;
        while (at < line.length() && space(line.charAt(at))) {
            at++;
        }
        return at;
    }

    /**
     * The line of the character at {@code index}, counted from 1.
     */
    private int lineOf(int index) {
        int found = Arrays.binarySearch(starts, index);
        return found >= 0 ? found + 1 : -found - 1;
    }

    private NotRead broken(int index, Problem problem) {
        int line = lineOf(index);
        return new NotRead(new NotXml.Broken(line, index - starts[line - 1] + 1, problem));
    }

    /**
     * The lines of {@code text}, trimmed: each without the white space at its end, those after the first without the
     * indentation they share.
     */
    private static List<String> lines(String text) {
        List<String> lines = List.of(text.split("\n", -1)).map(XmlReader::stripEnd);
        int shared = indentation(lines.tail());
        return lines.tail()
                .map(line -> line.length() < shared ? "" : line.substring(shared))
                .prepend(lines.head());
    }

    private static String stripEnd(String line) {
        int until = line.length();
        while (until > 0 && space(line.charAt(until - 1))) {
            until--;
        }
        return line.substring(0, until);
    }

    /**
     * Stops reading: the text is not read as XML, for {@code why}.
     */
    private static final class NotRead extends RuntimeException {

        private final transient NotXml why;

        NotRead(NotXml why) {
            super(null, null, false, false);
            this.why = why;
        }
    }
}
