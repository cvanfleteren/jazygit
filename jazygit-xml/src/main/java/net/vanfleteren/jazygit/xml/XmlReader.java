package net.vanfleteren.jazygit.xml;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import java.util.Arrays;
import java.util.stream.IntStream;
import net.vanfleteren.jazygit.xml.NotXml.Problem;
import net.vanfleteren.jazygit.xml.XmlTree.Attribute;
import net.vanfleteren.jazygit.xml.XmlTree.Comment;
import net.vanfleteren.jazygit.xml.XmlTree.Element;
import net.vanfleteren.jazygit.xml.XmlTree.Text;

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
    private int at;

    private XmlReader(String text) {
        this.text = text;
        this.starts = IntStream.concat(
                        IntStream.of(0),
                        IntStream.range(0, text.length())
                                .filter(index -> text.charAt(index) == '\n')
                                .map(index -> index + 1))
                .toArray();
        this.at = text.startsWith("﻿") ? 1 : 0;
    }

    static Either<NotXml, List<XmlTree>> read(String text) {
        try {
            return new XmlReader(text).document();
        } catch (NotRead e) {
            return Either.left(e.why);
        }
    }

    private Either<NotXml, List<XmlTree>> document() {
        List<XmlTree> top = List.empty();
        boolean rooted = false;
        while (true) {
            skipSpace();
            if (at >= text.length()) {
                return rooted ? Either.right(top) : Either.left(new NotXml.Nothing());
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
        List<Attribute> attributes = List.empty();
        while (true) {
            skipSpace();
            if (at >= text.length()) {
                throw broken(at, new Problem.EndsInside(name));
            }
            if (opens("/>")) {
                at += 2;
                int line = lineOf(at - 1);
                return new Element(lineOf(from), line, line, name, attributes, List.empty());
            }
            if (opens(">")) {
                at++;
                int tagEnd = lineOf(at - 1);
                return children(lineOf(from), tagEnd, name, attributes, depth);
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
        List<XmlTree> children = List.empty();
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
                return new Element(line, tagEnd, lineOf(at - 1), name, attributes, children);
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
        StringBuilder decoded = new StringBuilder();
        int index = from;
        while (index < at) {
            char c = text.charAt(index);
            if (c == '&') {
                index = reference(index, decoded);
            } else {
                decoded.append(c);
                index++;
            }
        }
        return textOf(from, at, decoded.toString(), false);
    }

    private List<XmlTree> cdata() {
        int from = at + "<![CDATA[".length();
        int until = text.indexOf("]]>", from);
        if (until < 0) {
            throw broken(at, new Problem.Expected("]]>"));
        }
        at = until + 3;
        return textOf(from, until, text.substring(from, until), true);
    }

    /**
     * The text written from {@code from} up to {@code until}, read as {@code decoded}, trimmed, if it holds more than
     * white space: its line is that of the first character that is no white space in the source.
     */
    private List<XmlTree> textOf(int from, int until, String decoded, boolean cdata) {
        if (decoded.isBlank()) {
            return List.empty();
        }
        int first = from;
        while (first < until && Character.isWhitespace(text.charAt(first))) {
            first++;
        }
        int last = until - 1;
        while (last > first && Character.isWhitespace(text.charAt(last))) {
            last--;
        }
        return List.of(new Text(lineOf(first), lineOf(last), cdata, lines(decoded.strip())));
    }

    private Comment comment() {
        int from = at;
        int inside = at + "<!--".length();
        int until = text.indexOf("-->", inside);
        if (until < 0) {
            throw broken(from, new Problem.Expected("-->"));
        }
        at = until + 3;
        int first = inside;
        while (first < until && Character.isWhitespace(text.charAt(first))) {
            first++;
        }
        return new Comment(
                lineOf(from),
                lineOf(until),
                lineOf(Math.min(first, until)),
                lines(text.substring(inside, until).strip()));
    }

    private void instruction() {
        int until = text.indexOf("?>", at + 2);
        if (until < 0) {
            throw broken(at, new Problem.Expected("?>"));
        }
        at = until + 2;
    }

    /**
     * Reads past a DOCTYPE, its internal subset and the strings in it too, without reading what it declares.
     */
    private void doctype() {
        int from = at;
        int depth = 0;
        char quote = 0;
        for (int index = at + "<!DOCTYPE".length(); index < text.length(); index++) {
            char c = text.charAt(index);
            if (quote != 0) {
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
            if (!digits.isEmpty() && digits.chars().allMatch(c -> Character.digit(c, hex ? 16 : 10) >= 0)) {
                try {
                    to.appendCodePoint(Integer.parseInt(digits, hex ? 16 : 10));
                    return semicolon + 1;
                } catch (IllegalArgumentException e) {
                    throw broken(index, new Problem.Ampersand());
                }
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
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
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
        List<String> lines = List.of(text.split("\n", -1)).map(String::stripTrailing);
        int shared = lines.tail()
                .filter(line -> !line.isBlank())
                .map(line -> line.length() - line.stripLeading().length())
                .min()
                .getOrElse(0);
        return lines.tail()
                .map(line -> line.length() < shared ? "" : line.substring(shared))
                .prepend(lines.head());
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
