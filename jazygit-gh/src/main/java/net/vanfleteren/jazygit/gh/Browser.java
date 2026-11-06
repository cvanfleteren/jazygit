package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The browser gh opens links in, which jazygit starts itself for a link gh would cut short (ADR 85).
 */
final class Browser {

    // How long a browser is waited for: one still running by then is taken to have opened the link.
    static final Duration SETTLE = Duration.ofSeconds(2);

    // What opens a link in the browser the desktop is set to, on Linux.
    static final String XDG_OPEN = "xdg-open";

    private Browser() {}

    /**
     * The browser gh would start, in gh's order: {@code $GH_BROWSER}, gh's {@code browser} setting,
     * {@code $BROWSER}, and on Linux {@code xdg-open}; none on another system without one set, where gh is left to
     * open what it can. Blank is not set.
     *
     * @param environment the variables of the environment, by name
     * @param configured  gh's {@code browser} setting, read only when {@code $GH_BROWSER} is not set
     * @param os          the name of the system, as {@code os.name} says it
     */
    static Option<String> chosen(
            Function<String, Option<String>> environment, Supplier<Option<String>> configured, String os) {
        return set(environment.apply("GH_BROWSER"))
                .orElse(() -> set(configured.get()))
                .orElse(() -> set(environment.apply("BROWSER")))
                .orElse(() -> Option.when(os.toLowerCase(Locale.ROOT).contains("linux"), XDG_OPEN));
    }

    /**
     * How {@code browser}, as it is set, is run on {@code url}: its words, as a shell splits them, and the link.
     */
    static List<String> commandLine(String browser, String url) {
        return words(browser).append(url);
    }

    /**
     * {@code line} split into words as a shell does, as gh splits its browser: on white space, but not inside single
     * or double quotes, nor where a backslash escapes it. Nothing is expanded.
     */
    static List<String> words(String line) {
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

    private static Option<String> set(Option<String> value) {
        return value.map(String::strip).filter(set -> !set.isEmpty());
    }
}
