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
        List<String> words = List.empty();
        StringBuilder word = new StringBuilder();
        boolean inWord = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else {
                    word.append(c);
                }
            } else if (quote == '"') {
                if (c == '"') {
                    quote = 0;
                } else if (c == '\\' && i + 1 < line.length() && "\"\\$`".indexOf(line.charAt(i + 1)) >= 0) {
                    word.append(line.charAt(++i));
                } else {
                    word.append(c);
                }
            } else if (Character.isWhitespace(c)) {
                if (inWord) {
                    words = words.append(word.toString());
                    word.setLength(0);
                    inWord = false;
                }
            } else {
                inWord = true;
                if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '\\' && i + 1 < line.length()) {
                    word.append(line.charAt(++i));
                } else {
                    word.append(c);
                }
            }
        }
        return inWord ? words.append(word.toString()) : words;
    }

    private static Option<String> set(Option<String> value) {
        return value.map(String::strip).filter(set -> !set.isEmpty());
    }
}
