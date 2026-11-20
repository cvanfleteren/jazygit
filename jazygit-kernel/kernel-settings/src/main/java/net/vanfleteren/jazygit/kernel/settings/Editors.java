package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * The editors the user set in the settings file, as its {@code [[editors]]} tables list them, in their order:
 * those that open a worktree (ADR 110), and those that open a file (ADR 145). Only read: the app never writes them.
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
    public static final String KEY = "editors";

    private static final String DIR_COMMAND = "dir-command";
    private static final String FILE_COMMAND = "file-command";

    /**
     * What ADR 110 called the command that opens a worktree, read when there is no {@link #DIR_COMMAND}.
     */
    private static final String FORMER_DIR_COMMAND = "command";

    /**
     * Whether no editor is set.
     */
    public boolean isEmpty() {
        return all.isEmpty();
    }

    /**
     * The editors that open a worktree, in the order of the file.
     */
    public Editors worktrees() {
        return new Editors(all.filter(EditorProgram::opensDirectories));
    }

    /**
     * The editors that open a file, in the order of the file (ADR 145).
     */
    public Editors files() {
        return new Editors(all.filter(EditorProgram::opensFiles));
    }

    /**
     * Each editor with the letter that chooses it in the menu: its shortcut, or else the first free one, in the order
     * of the file (see {@link Programs#lettered}).
     */
    public List<Tuple2<Character, EditorProgram>> lettered() {
        return Programs.lettered(all);
    }

    /**
     * The editors {@code value} holds, an array of tables as the settings file is read, with the keys of what is wrong
     * in it, such as {@code editors[2].file-command}, counted from 1 as the tables are in the file. An entry without a
     * name, with neither command, with a command that is not a list of words, or with a {@code terminal} that is not a
     * boolean is left out; a shortcut that is not one
     * character, is {@code /}, or is an earlier entry's is dropped. When {@code value} is no array, there are none,
     * and {@code editors} itself is wrong.
     */
    public static Read read(Object value) {
        return Programs.tables(value).map(Editors::entries).getOrElse(() -> new Read(NONE, List.of(KEY)));
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
        Option<String> name = Programs.text(table.get(Programs.NAME));
        String dirKey = table.containsKey(DIR_COMMAND) ? DIR_COMMAND : FORMER_DIR_COMMAND;
        Object dirValue = table.get(dirKey);
        Object fileValue = table.get(FILE_COMMAND);
        Option<List<String>> dirCommand = Programs.command(dirValue);
        Option<List<String>> fileCommand = Programs.command(fileValue);
        Object terminal = table.get(Programs.TERMINAL);
        boolean neither = dirValue == null && fileValue == null;
        List<String> wrong = Option.when(name.isEmpty(), at + Programs.NAME)
                .toList()
                .appendAll(Option.when(neither, at + DIR_COMMAND))
                .appendAll(Option.when(dirValue != null && dirCommand.isEmpty(), at + dirKey))
                .appendAll(Option.when(fileValue != null && fileCommand.isEmpty(), at + FILE_COMMAND))
                .appendAll(Option.when(!Programs.validTerminal(terminal), at + Programs.TERMINAL));
        if (wrong.nonEmpty()) {
            return new Read(NONE, wrong);
        }
        Object shortcut = table.get(Programs.SHORTCUT);
        Set<Character> taken =
                before.editors().all().flatMap(EditorProgram::shortcut).toSet();
        Option<Character> validShortcut = Programs.shortcut(shortcut, taken);
        EditorProgram editor =
                new EditorProgram(name.get(), dirCommand, fileCommand, Boolean.TRUE.equals(terminal), validShortcut);
        return new Read(
                new Editors(List.of(editor)),
                Option.when(shortcut != null && validShortcut.isEmpty(), at + Programs.SHORTCUT)
                        .toList());
    }

    /**
     * Editors as read, with the keys of what was wrong in them.
     */
    public record Read(Editors editors, List<String> invalid) {

        /**
         * These editors and those of {@code next} after them, with what was wrong in both.
         */
        Read and(Read next) {
            return new Read(
                    new Editors(editors.all().appendAll(next.editors().all())), invalid.appendAll(next.invalid()));
        }
    }
}
