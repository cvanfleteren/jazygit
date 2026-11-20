package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the lists of programs in the settings file share, the editors (ADR 110) and the agents (ADR 161): how an entry
 * is read, how a program gets its letter in a menu, and how the placeholders of its command are filled in.
 */
public final class Programs {

    /**
     * The key of a program's name in its table.
     */
    public static final String NAME = "name";
    /**
     * The key of whether a program is handed the terminal, in its table.
     */
    public static final String TERMINAL = "terminal";
    /**
     * The key of a program's shortcut in its table.
     */
    public static final String SHORTCUT = "shortcut";

    /**
     * The letters a program without a shortcut gets, in this order, but those a shortcut takes.
     */
    static final List<Character> LETTERS = List.ofAll("123456789abcdefghijklmnopqrstuvwxyz".toCharArray());

    /**
     * What opens the filter of a menu, which no shortcut can be.
     */
    private static final char FILTER = '/';

    private Programs() {}

    /**
     * Each of {@code programs} with the letter that chooses it in the menu: its shortcut, or else the first of
     * {@link #LETTERS} that no shortcut takes, in their order. Past those, a program is left out; no user lists that
     * many.
     */
    public static <P extends Program> List<Tuple2<Character, P>> lettered(List<P> programs) {
        Set<Character> taken = programs.flatMap(Program::shortcut).toSet();
        return lettered(programs, LETTERS.filter(letter -> !taken.contains(letter)));
    }

    private static <P extends Program> List<Tuple2<Character, P>> lettered(List<P> programs, List<Character> free) {
        if (programs.isEmpty()) {
            return List.empty();
        }
        P program = programs.head();
        Option<Character> own = program.shortcut();
        Option<Character> letter = own.orElse(free::headOption);
        List<Character> left = own.isDefined() || free.isEmpty() ? free : free.tail();
        return letter.map(chosen -> Tuple.of(chosen, program)).toList().appendAll(lettered(programs.tail(), left));
    }

    /**
     * {@code argument} with each of the placeholders of {@code values} in it replaced by its value in one pass, so a
     * value that holds a placeholder is left as it is. A {@code {…}} that is none of them stays as it is (ADR 161).
     */
    public static String fill(String argument, Map<String, String> values) {
        if (values.isEmpty()) {
            return argument;
        }
        Pattern placeholders =
                Pattern.compile(values.keySet().toList().map(Pattern::quote).mkString("|"));
        return placeholders
                .matcher(argument)
                .replaceAll(found ->
                        Matcher.quoteReplacement(values.get(found.group()).get()));
    }

    /**
     * {@code value}, if it is text that is not blank.
     */
    public static Option<String> text(Object value) {
        return Option.of(value)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }

    /**
     * The command {@code value} is, if it is an array of text whose first word, the program, is not blank.
     */
    public static Option<List<String>> command(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .filter(words -> words.forAll(String.class::isInstance))
                .map(words -> words.map(String.class::cast))
                .filter(words -> words.headOption().exists(program -> !program.isBlank()));
    }

    /**
     * Whether {@code value} is a {@code terminal} a program may have: none, or a boolean.
     */
    public static boolean validTerminal(Object value) {
        return value == null || value instanceof Boolean;
    }

    /**
     * The shortcut {@code value} is, if it is one character that is not {@code /}, blank, or among {@code taken}.
     */
    public static Option<Character> shortcut(Object value, Set<Character> taken) {
        return Option.of(value)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(letter -> letter.length() == 1)
                .map(letter -> letter.charAt(0))
                .filter(letter -> letter != FILTER && !Character.isWhitespace(letter) && !taken.contains(letter));
    }

    /**
     * The tables {@code value} holds, an array of tables as the settings file is read; none when it is no array.
     */
    public static Option<List<?>> tables(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array));
    }
}
