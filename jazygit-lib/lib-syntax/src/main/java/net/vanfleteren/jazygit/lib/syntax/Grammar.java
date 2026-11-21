package net.vanfleteren.jazygit.lib.syntax;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import java.util.Locale;
import lombok.With;

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
 * @param nameChars     the characters a name holds after its first, besides letters, digits and underscores: Nix's
 *                      {@code -} and {@code '} ({@code foo-bar}, {@code x'}), so that the {@code then} of
 *                      {@code then-y} is no keyword
 * @param properties    whether a name, or a path of names with points between them, followed by one {@code =} is a
 *                      property: Nix's {@code services.nginx.enable = true;} (ADR 170)
 * @param variables     whether a name after {@code $} is a variable, and so no keyword, as PHP's {@code $list}
 * @param hashAnywhere  whether a comment that starts with {@code #} starts right after anything, not only after a
 *                      space or at the start of the line, as R's {@code f(1)# note}; a shell's needs the space, as
 *                      its {@code $#} is no comment
 */
record Grammar(
        Mode mode,
        Set<String> keywords,
        boolean ignoreCase,
        List<String> lineComments,
        List<Delimited> blockComments,
        List<Delimited> strings,
        @With boolean annotations,
        @With String nameChars,
        @With boolean properties,
        @With boolean variables,
        @With boolean hashAnywhere) {

    Grammar {
        strings = strings.sortBy(string -> -string.open().length());
    }

    /**
     * The grammar of code, with case-sensitive keywords.
     */
    static Grammar code(
            String keywords, List<String> lineComments, List<Delimited> blockComments, List<Delimited> strings) {
        return new Grammar(
                Mode.CODE,
                words(keywords),
                false,
                lineComments,
                blockComments,
                strings,
                false,
                "",
                false,
                false,
                false);
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
                annotations,
                nameChars,
                properties,
                variables,
                hashAnywhere);
    }

    /**
     * Whether {@code c} is part of a name after its first character.
     */
    boolean namePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || nameChars.indexOf(c) >= 0;
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
     * @param escapes   what escapes a character in it, so that it does not close it; the first that fits is taken
     */
    record Delimited(String open, String close, boolean multiline, List<Escape> escapes) {

        private static final List<Escape> BACKSLASH = List.of(new Escape("\\", ""));

        /**
         * A string on one line, that ends with the quote it starts with, in which a backslash escapes.
         */
        static Delimited quoted(String quote) {
            return new Delimited(quote, quote, false, BACKSLASH);
        }

        /**
         * A string on one line, that ends with the quote it starts with, in which a backslash is just a backslash.
         */
        static Delimited raw(String quote) {
            return new Delimited(quote, quote, false, List.empty());
        }

        /**
         * This, going on over the next lines until it is closed.
         */
        Delimited overLines() {
            return new Delimited(open, close, true, escapes);
        }

        /**
         * This, escaped by {@code escapes} alone: Nix's {@code ''$}, {@code '''} and {@code ''\} in a string between
         * {@code ''}, in which a backslash is just a backslash.
         */
        Delimited escapedBy(Escape... escapes) {
            return new Delimited(open, close, multiline, List.of(escapes));
        }

        static Delimited comment(String open, String close) {
            return new Delimited(open, close, true, List.empty());
        }

        /**
         * Where the first escape in {@code text} from {@code from} on starts, or -1 when there is none.
         */
        int escapeFrom(String text, int from) {
            int first = -1;
            for (Escape escape : escapes) {
                int at = escape.from(text, from);
                if (at >= 0 && (first < 0 || at < first)) {
                    first = at;
                }
            }
            return first;
        }

        /**
         * Where the escape that starts at {@code at} in {@code text} ends, with the character it escapes.
         */
        int escapeEnd(String text, int at) {
            return escapes.find(escape -> escape.at(text, at))
                    .map(escape -> at + escape.prefix().length() + 1)
                    .getOrElse(at + 1);
        }
    }

    /**
     * What escapes the one character after it: {@code prefix}, when that character is one of {@code escaped}, or any
     * when {@code escaped} is empty. A loop rather than a stream, as it runs for each string of each line of a diff.
     */
    record Escape(String prefix, String escaped) {

        /**
         * Where the first of it in {@code text} from {@code from} on starts, or -1 when there is none.
         */
        int from(String text, int from) {
            for (int at = text.indexOf(prefix, from); at >= 0; at = text.indexOf(prefix, at + 1)) {
                if (escapes(text, at + prefix.length())) {
                    return at;
                }
            }
            return -1;
        }

        /**
         * Whether it starts at {@code at} in {@code text}.
         */
        boolean at(String text, int at) {
            return text.startsWith(prefix, at) && escapes(text, at + prefix.length());
        }

        private boolean escapes(String text, int next) {
            return escaped.isEmpty() || next < text.length() && escaped.indexOf(text.charAt(next)) >= 0;
        }
    }
}
