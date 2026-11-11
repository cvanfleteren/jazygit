package net.vanfleteren.jazygit.settings;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * The editors the user set in the settings file, as its {@code [[editors]]} tables list them, in their order
 * (ADR 110). Only read: the app never writes them.
 *
 * @param all the editors, in the order of the file
 */
public record Editors(List<EditorProgram> all) {

    /**
     * No editor set.
     */
    public static final Editors NONE = new Editors(List.empty());

    /**
     * The key of the editors in the settings file: an array of tables.
     */
    static final String KEY = "editors";

    /**
     * The letters an editor without a shortcut gets, in this order, but those a shortcut takes.
     */
    static final List<Character> LETTERS = List.ofAll("123456789abcdefghijklmnopqrstuvwxyz".toCharArray());

    /**
     * What opens the filter of a menu, which no shortcut can be.
     */
    private static final char FILTER = '/';

    private static final String NAME = "name";
    private static final String COMMAND = "command";
    private static final String TERMINAL = "terminal";
    private static final String SHORTCUT = "shortcut";

    /**
     * Whether no editor is set.
     */
    public boolean isEmpty() {
        return all.isEmpty();
    }

    /**
     * Each editor with the letter that chooses it in the menu: its shortcut, or else the first of {@link #LETTERS} that
     * no shortcut takes, in the order of the file. Past those, an editor is left out; no user lists that many.
     */
    public List<Tuple2<Character, EditorProgram>> lettered() {
        Set<Character> taken = all.flatMap(EditorProgram::shortcut).toSet();
        return lettered(all, LETTERS.filter(letter -> !taken.contains(letter)));
    }

    /**
     * Each of {@code editors} with its shortcut, or else the next of {@code free}.
     */
    private static List<Tuple2<Character, EditorProgram>> lettered(List<EditorProgram> editors, List<Character> free) {
        if (editors.isEmpty()) {
            return List.empty();
        }
        EditorProgram editor = editors.head();
        Option<Character> own = editor.shortcut();
        Option<Character> letter = own.orElse(free::headOption);
        List<Character> left = own.isDefined() || free.isEmpty() ? free : free.tail();
        return letter.map(chosen -> Tuple.of(chosen, editor)).toList().appendAll(lettered(editors.tail(), left));
    }

    /**
     * The editors {@code value} holds, an array of tables as the settings file is read, with the keys of what is wrong
     * in it, such as {@code editors[2].command}, counted from 1 as the tables are in the file. An entry without a
     * name, with no command, or with a {@code terminal} that is not a boolean is left out; a shortcut that is not one
     * character, is {@code /}, or is an earlier entry's is dropped. When {@code value} is no array, there are none,
     * and {@code editors} itself is wrong.
     */
    static Read read(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .map(Editors::entries)
                .getOrElse(() -> new Read(NONE, List.of(KEY)));
    }

    private static Read entries(List<?> tables) {
        return tables.zipWithIndex()
                .foldLeft(new Read(NONE, List.empty()), (read, table) -> read.and(entry(table._1, table._2 + 1, read)));
    }

    /**
     * The editor of the entry at {@code number}, counted from 1, if it is one, with what is wrong in it; {@code before}
     * has the editors of the entries before it, whose shortcuts it cannot take.
     */
    private static Read entry(Object value, int number, Read before) {
        String at = KEY + "[" + number + "].";
        if (!(value instanceof java.util.Map<?, ?> table)) {
            return new Read(NONE, List.of(KEY + "[" + number + "]"));
        }
        Option<String> name = text(table.get(NAME));
        Option<List<String>> command = command(table.get(COMMAND));
        Object terminal = table.get(TERMINAL);
        boolean validTerminal = terminal == null || terminal instanceof Boolean;
        List<String> wrong = Option.when(name.isEmpty(), at + NAME)
                .toList()
                .appendAll(Option.when(command.isEmpty(), at + COMMAND))
                .appendAll(Option.when(!validTerminal, at + TERMINAL));
        if (wrong.nonEmpty()) {
            return new Read(NONE, wrong);
        }
        Object shortcut = table.get(SHORTCUT);
        Set<Character> taken =
                before.editors().all().flatMap(EditorProgram::shortcut).toSet();
        Option<Character> validShortcut = Option.of(shortcut)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(letter -> letter.length() == 1)
                .map(letter -> letter.charAt(0))
                .filter(letter -> letter != FILTER && !Character.isWhitespace(letter) && !taken.contains(letter));
        EditorProgram editor =
                new EditorProgram(name.get(), command.get(), Boolean.TRUE.equals(terminal), validShortcut);
        return new Read(
                new Editors(List.of(editor)),
                Option.when(shortcut != null && validShortcut.isEmpty(), at + SHORTCUT)
                        .toList());
    }

    /**
     * {@code value}, if it is text that is not blank.
     */
    private static Option<String> text(Object value) {
        return Option.of(value)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }

    /**
     * The command {@code value} is, if it is an array of text whose first word, the program, is not blank.
     */
    private static Option<List<String>> command(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .filter(words -> words.forAll(String.class::isInstance))
                .map(words -> words.map(String.class::cast))
                .filter(words -> words.headOption().exists(program -> !program.isBlank()));
    }

    /**
     * Editors as read, with the keys of what was wrong in them.
     */
    record Read(Editors editors, List<String> invalid) {

        /**
         * These editors and those of {@code next} after them, with what was wrong in both.
         */
        Read and(Read next) {
            return new Read(
                    new Editors(editors.all().appendAll(next.editors().all())), invalid.appendAll(next.invalid()));
        }
    }
}
