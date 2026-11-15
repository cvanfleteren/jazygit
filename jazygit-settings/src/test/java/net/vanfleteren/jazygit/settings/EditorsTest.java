package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The editors of the settings file, which a worktree is opened in (ADR 110).
 */
class EditorsTest {

    private static final EditorProgram IDEA = editor("IntelliJ", Option.none());
    private static final EditorProgram KATE = editor("Kate", Option.of('k'));
    private static final EditorProgram CODE = editor("Code", Option.none());

    private static EditorProgram editor(String name, Option<Character> shortcut) {
        return new EditorProgram(name, List.of(name.toLowerCase(java.util.Locale.ROOT)), false, shortcut);
    }

    private static java.util.Map<String, Object> table(Object... keysAndValues) {
        java.util.Map<String, Object> table = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            table.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return table;
    }

    @Test
    void theWorktreeTakesThePlaceOfDirWhereverAndHoweverOftenItIs() {
        EditorProgram konsole = new EditorProgram(
                "Konsole",
                List.of("konsole", "--workdir", "{dir}", "-e", "nvim", "--cmd=cd {dir}"),
                false,
                Option.none());

        assertEquals(
                List.of("konsole", "--workdir", "/repos/app", "-e", "nvim", "--cmd=cd /repos/app"),
                konsole.commandLine("/repos/app"));
    }

    @Test
    void withoutDirTheWorktreeIsTheLastArgument() {
        EditorProgram idea = new EditorProgram("IntelliJ", List.of("idea", "--wait"), false, Option.none());

        assertEquals(List.of("idea", "--wait", "/repos/app"), idea.commandLine("/repos/app"));
    }

    @Test
    void editorsWithoutAShortcutGetTheDigitsAndThenTheLettersNoShortcutTakes() {
        EditorProgram one = editor("One", Option.of('1'));
        List<EditorProgram> rest = List.range(0, 9).map(i -> editor("E" + i, Option.none()));

        Editors editors = new Editors(rest.prepend(one).append(KATE));

        assertEquals(
                List.of('1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'k'),
                editors.lettered().map(lettered -> lettered._1));
        assertEquals(editors.all(), editors.lettered().map(lettered -> lettered._2));
    }

    @Test
    void theLettersFollowTheOrderOfTheFile() {
        Editors editors = new Editors(List.of(IDEA, KATE, CODE));

        assertEquals(List.of(Tuple.of('1', IDEA), Tuple.of('k', KATE), Tuple.of('2', CODE)), editors.lettered());
    }

    @Test
    void readsTheEntriesWithTheirShortcutsAndWhetherTheyAreForTheTerminal() {
        Editors.Read read = Editors.read(java.util.List.of(
                table("name", "IntelliJ", "command", java.util.List.of("idea", "{dir}"), "shortcut", "i"),
                table("name", "Neovim", "command", java.util.List.of("nvim"), "terminal", true)));

        assertEquals(List.empty(), read.invalid());
        assertEquals(
                List.of(
                        new EditorProgram("IntelliJ", List.of("idea", "{dir}"), false, Option.of('i')),
                        new EditorProgram("Neovim", List.of("nvim"), true, Option.none())),
                read.editors().all());
    }

    @Test
    void anEntryWithoutANameOrACommandOrWithATerminalThatIsNoBooleanIsLeftOut() {
        Editors.Read read = Editors.read(java.util.List.of(
                table("command", java.util.List.of("idea")),
                table("name", "Empty", "command", java.util.List.of()),
                table("name", "Blank", "command", java.util.List.of(" ", "x")),
                table("name", "Words", "command", "kate {dir}"),
                table("name", "Odd", "command", java.util.List.of("kate"), "terminal", "yes"),
                table("name", "Kate", "command", java.util.List.of("kate")),
                "not a table"));

        assertEquals(
                List.of(
                        "editors[1].name",
                        "editors[2].command",
                        "editors[3].command",
                        "editors[4].command",
                        "editors[5].terminal",
                        "editors[7]"),
                read.invalid());
        assertEquals(List.of("Kate"), read.editors().all().map(EditorProgram::name));
    }

    @Test
    void aShortcutThatIsNotOneCharacterIsTheFilterOrIsTakenIsDroppedButTheEntryStays() {
        Editors.Read read = Editors.read(java.util.List.of(
                table("name", "A", "command", java.util.List.of("a"), "shortcut", "k"),
                table("name", "B", "command", java.util.List.of("b"), "shortcut", "k"),
                table("name", "C", "command", java.util.List.of("c"), "shortcut", "/"),
                table("name", "D", "command", java.util.List.of("d"), "shortcut", "dd"),
                table("name", "E", "command", java.util.List.of("e"), "shortcut", 3)));

        assertEquals(
                List.of("editors[2].shortcut", "editors[3].shortcut", "editors[4].shortcut", "editors[5].shortcut"),
                read.invalid());
        assertEquals(
                List.of(Option.of('k'), Option.none(), Option.none(), Option.none(), Option.none()),
                read.editors().all().map(EditorProgram::shortcut));
    }

    @Test
    void editorsThatAreNoArrayAreNoneAndSaidSo() {
        Editors.Read read = Editors.read("idea");

        assertEquals(Editors.NONE, read.editors());
        assertEquals(List.of("editors"), read.invalid());
    }
}
