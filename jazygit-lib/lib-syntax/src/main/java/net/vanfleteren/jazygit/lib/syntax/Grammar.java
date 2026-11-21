package net.vanfleteren.jazygit.lib.syntax;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import java.util.Locale;

/**
 * What the lexer needs to know of a language.
 *
 * @param mode          how its lines are scanned
 * @param keywords      its keywords, in lower case when they are matched ignoring case
 * @param ignoreCase    whether its keywords are matched ignoring case
 * @param lineComments  what starts a comment that runs to the end of the line
 * @param blockComments the comments that end where they say
 * @param strings       its kinds of strings, the longest openings first
 * @param annotations   whether a name after {@code @} is an annotation, or a decorator
 */
record Grammar(
        Mode mode,
        Set<String> keywords,
        boolean ignoreCase,
        List<String> lineComments,
        List<Delimited> blockComments,
        List<Delimited> strings,
        boolean annotations) {

    Grammar {
        strings = strings.sortBy(string -> -string.open().length());
    }

    /**
     * The grammar of code, with case-sensitive keywords.
     */
    static Grammar code(
            String keywords, List<String> lineComments, List<Delimited> blockComments, List<Delimited> strings) {
        return new Grammar(Mode.CODE, words(keywords), false, lineComments, blockComments, strings, false);
    }

    /**
     * This grammar, with its keywords matched ignoring case.
     */
    Grammar ignoringCase() {
        return new Grammar(
                mode,
                keywords.map(word -> word.toLowerCase(Locale.ROOT)),
                true,
                lineComments,
                blockComments,
                strings,
                annotations);
    }

    /**
     * This grammar, in which a name after {@code @} is an annotation, or a decorator.
     */
    Grammar withAnnotations() {
        return new Grammar(mode, keywords, ignoreCase, lineComments, blockComments, strings, true);
    }

    /**
     * Whether {@code word} is a keyword.
     */
    boolean keyword(String word) {
        return keywords.contains(ignoreCase ? word.toLowerCase(Locale.ROOT) : word);
    }

    static Set<String> words(String words) {
        return words.isBlank() ? HashSet.empty() : HashSet.of(words.strip().split("\\s+"));
    }

    enum Mode {
        /**
         * Keywords, comments, strings, numbers and annotations.
         */
        CODE,
        /**
         * The tags of XML and HTML: a tag's name, the values of its attributes, and comments.
         */
        MARKUP,
        /**
         * The blocks of Markdown: headings, the markers of lists and quotes, code and comments.
         */
        MARKDOWN
    }

    /**
     * Text between {@code open} and {@code close}: a string, or a block comment.
     *
     * @param multiline whether it can go on over the next lines; one that cannot ends with its line
     * @param escape    what escapes the character after it, so that it does not close it; empty when nothing does
     * @param escaped   the characters {@code escape} escapes; empty when it escapes any
     */
    record Delimited(String open, String close, boolean multiline, String escape, String escaped) {

        /**
         * A string on one line, that ends with the quote it starts with, in which a backslash escapes.
         */
        static Delimited quoted(String quote) {
            return new Delimited(quote, quote, false, "\\", "");
        }

        /**
         * A string on one line, that ends with the quote it starts with, in which a backslash is just a backslash.
         */
        static Delimited raw(String quote) {
            return new Delimited(quote, quote, false, "", "");
        }

        /**
         * This, going on over the next lines until it is closed.
         */
        Delimited overLines() {
            return new Delimited(open, close, true, escape, escaped);
        }

        /**
         * This, in which {@code escape} escapes the one of {@code escaped} after it, and a backslash is just a
         * backslash: Nix's {@code ''$}, {@code '''} and {@code ''\} in a string between {@code ''}.
         */
        Delimited escapedBy(String escape, String escaped) {
            return new Delimited(open, close, multiline, escape, escaped);
        }

        static Delimited comment(String open, String close) {
            return new Delimited(open, close, true, "", "");
        }

        /**
         * Where the first escape in {@code text} from {@code from} on starts, or -1 when there is none. A loop, as it
         * runs for each string of each line of a diff.
         */
        int escapeFrom(String text, int from) {
            if (escape.isEmpty()) {
                return -1;
            }
            for (int at = text.indexOf(escape, from); at >= 0; at = text.indexOf(escape, at + 1)) {
                int next = at + escape.length();
                if (escaped.isEmpty() || next < text.length() && escaped.indexOf(text.charAt(next)) >= 0) {
                    return at;
                }
            }
            return -1;
        }

        /**
         * How long an escape is, with the character it escapes.
         */
        int escapeLength() {
            return escape.length() + 1;
        }
    }
}
