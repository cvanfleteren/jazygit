package net.vanfleteren.jazygit.core.process;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.regex.Pattern;

/**
 * A command as the line one would type in a shell, and such a line split back into its words.
 */
public final class CommandLine {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_@%+=:,./^-]+");

    private CommandLine() {}

    public static String format(List<String> command) {
        return command.map(CommandLine::quote).mkString(" ");
    }

    /**
     * {@code line} split into words as a shell does, on white space, but not inside single
     * or double quotes, nor where a backslash escapes it. Nothing is expanded.
     */
    public static List<String> words(String line) {
        return words(line, 0, Option.none(), Option.none(), List.empty());
    }

    /**
     * @param at    the index of the next character of {@code line}
     * @param quote the quote the text is inside, if any
     * @param word  the word being read, none between words (an empty quoted word is still a word)
     * @param done  the words read so far
     */
    private static List<String> words(
            String line, int at, Option<Character> quote, Option<String> word, List<String> done) {
        if (at >= line.length()) {
            return word.map(done::append).getOrElse(done);
        }
        char c = line.charAt(at);
        char next = at + 1 < line.length() ? line.charAt(at + 1) : 0;
        String current = word.getOrElse("");
        return switch (quote.getOrElse((char) 0)) {
            case '\'' ->
                c == '\''
                        ? words(line, at + 1, Option.none(), Option.of(current), done)
                        : words(line, at + 1, quote, Option.of(current + c), done);
            case '"' ->
                c == '"'
                        ? words(line, at + 1, Option.none(), Option.of(current), done)
                        : c == '\\' && "\"\\$`".indexOf(next) >= 0 && next != 0
                                ? words(line, at + 2, quote, Option.of(current + next), done)
                                : words(line, at + 1, quote, Option.of(current + c), done);
            default ->
                Character.isWhitespace(c)
                        ? words(
                                line,
                                at + 1,
                                Option.none(),
                                Option.none(),
                                word.map(done::append).getOrElse(done))
                        : c == '\'' || c == '"'
                                ? words(line, at + 1, Option.of(c), Option.of(current), done)
                                : c == '\\' && next != 0
                                        ? words(line, at + 2, Option.none(), Option.of(current + next), done)
                                        : words(line, at + 1, Option.none(), Option.of(current + c), done);
        };
    }

    private static String quote(String arg) {
        return SAFE.matcher(arg).matches() ? arg : "'" + arg.replace("'", "'\\''") + "'";
    }
}
