package net.vanfleteren.jazygit.askpass;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.process.Exec;

/**
 * Whether an ssh command asks through jazygit when told to: OpenSSH has {@code SSH_ASKPASS_REQUIRE} since 8.4. One that
 * is older, or that does not say it is OpenSSH, would ask on the terminal instead, so it runs in batch mode
 * (ADR 150).
 */
final class OpenSsh {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // "OpenSSH_9.6p1 Ubuntu-3ubuntu13, OpenSSL 3.0.13 30 Jan 2024", or "OpenSSH_for_Windows_9.5p1, LibreSSL 3.8.2".
    private static final Pattern VERSION = Pattern.compile("OpenSSH_(?:for_Windows_)?(\\d+)\\.(\\d+)");
    private static final int MAJOR = 8;
    private static final int MINOR = 4;

    private OpenSsh() {}

    /**
     * Whether {@code ssh}, a command as git runs it, asks through jazygit: it is asked its version, in
     * {@code directory}.
     */
    static boolean asks(Path directory, String ssh) {
        return Exec.printed(directory, words(ssh).append("-V"), HashMap.empty(), TIMEOUT)
                .map(OpenSsh::asks)
                .getOrElse(false);
    }

    /**
     * Whether the version {@code printed} by {@code ssh -V} has {@code SSH_ASKPASS_REQUIRE}.
     */
    static boolean asks(String printed) {
        Matcher version = VERSION.matcher(printed);
        if (!version.find()) {
            return false;
        }
        int major = Integer.parseInt(version.group(1));
        return major > MAJOR || major == MAJOR && Integer.parseInt(version.group(2)) >= MINOR;
    }

    /**
     * The words of {@code command}, as a shell splits them: at blanks, but not inside quotes, and with a backslash
     * taking the character after it as it is, but inside single quotes. Nothing is expanded.
     */
    static List<String> words(String command) {
        return List.ofAll(command.toCharArray())
                .foldLeft(Words.START, Words::next)
                .done();
    }

    /**
     * The words of a command as far as it was read.
     *
     * @param words   the words read whole
     * @param word    the word being read, if any
     * @param quote   the quote the next character is in, if any: {@code '}, {@code "}, or none
     * @param escaped whether the next character comes after a backslash
     */
    private record Words(List<String> words, Option<String> word, Option<Character> quote, boolean escaped) {

        static final Words START = new Words(List.empty(), Option.none(), Option.none(), false);

        Words next(char c) {
            if (escaped) {
                return add(c);
            }
            if (quote.contains('\'')) {
                return c == '\'' ? quoted(Option.none()) : add(c);
            }
            if (c == '\\') {
                return new Words(words, word.orElse(Option.of("")), quote, true);
            }
            if (quote.contains('"')) {
                return c == '"' ? quoted(Option.none()) : add(c);
            }
            if (c == '\'' || c == '"') {
                return quoted(Option.of(c));
            }
            return Character.isWhitespace(c) ? new Words(done(), Option.none(), quote, false) : add(c);
        }

        List<String> done() {
            return words.appendAll(word);
        }

        private Words add(char c) {
            return new Words(words, Option.of(word.getOrElse("") + c), quote, false);
        }

        private Words quoted(Option<Character> into) {
            return new Words(words, word.orElse(Option.of("")), into, false);
        }
    }
}
