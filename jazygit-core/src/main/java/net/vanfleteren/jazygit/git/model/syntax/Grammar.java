package net.vanfleteren.jazygit.git.model.syntax;

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
 */
record Grammar(
        Mode mode,
        Set<String> keywords,
        boolean ignoreCase,
        List<String> lineComments,
        List<Delimited> blockComments,
        List<Delimited> strings) {

    Grammar {
        strings = strings.sortBy(string -> -string.open().length());
    }

    /**
     * The grammar of code, with case-sensitive keywords.
     */
    static Grammar code(
            String keywords, List<String> lineComments, List<Delimited> blockComments, List<Delimited> strings) {
        return new Grammar(Mode.CODE, words(keywords), false, lineComments, blockComments, strings);
    }

    /**
     * This grammar, with its keywords matched ignoring case.
     */
    Grammar ignoringCase() {
        return new Grammar(
                mode, keywords.map(word -> word.toLowerCase(Locale.ROOT)), true, lineComments, blockComments, strings);
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
         * Keywords, comments, strings and numbers.
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
     * @param escapes   whether a backslash escapes the character after it, so that it does not close it
     */
    record Delimited(String open, String close, boolean multiline, boolean escapes) {

        /**
         * A string on one line, that ends with the quote it starts with, in which a backslash escapes.
         */
        static Delimited quoted(String quote) {
            return new Delimited(quote, quote, false, true);
        }

        /**
         * A string on one line, that ends with the quote it starts with, in which a backslash is just a backslash.
         */
        static Delimited raw(String quote) {
            return new Delimited(quote, quote, false, false);
        }

        /**
         * This, going on over the next lines until it is closed.
         */
        Delimited overLines() {
            return new Delimited(open, close, true, escapes);
        }

        static Delimited comment(String open, String close) {
            return new Delimited(open, close, true, false);
        }
    }
}
