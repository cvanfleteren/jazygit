package net.vanfleteren.jazygit.kernel.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore.Loaded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A list the settings panel edits, kept as an array of tables (ADR 164): what the app does not know is kept, an
 * entry it cannot read is written back as it was, and a list another session changed meanwhile is not written over.
 */
class EntriesFileTest {

    /**
     * A tool, as a test's list holds them: a name, and whether it runs in the terminal.
     */
    record Tool(String name, boolean terminal) {}

    private static final TableCodec<Tool> TOOL = new TableCodec<>() {
        @Override
        public List<String> fields() {
            return List.of("name", "terminal");
        }

        @Override
        public Decoded<Tool> decode(String at, LinkedHashMap<String, Object> table) {
            Option<String> name =
                    table.get("name").filter(String.class::isInstance).map(String.class::cast);
            Option<Object> terminal = table.get("terminal");
            boolean badTerminal = terminal.exists(value -> !(value instanceof Boolean));
            List<String> wrong = Option.when(name.isEmpty(), at + ".name")
                    .toList()
                    .appendAll(Option.when(badTerminal, at + ".terminal"));
            // A terminal that is wrong is not used; a missing name leaves the entry out.
            return new Decoded<>(name.map(found -> new Tool(found, terminal.contains(true))), wrong);
        }

        @Override
        public LinkedHashMap<String, Object> encode(Tool tool) {
            LinkedHashMap<String, Object> named = LinkedHashMap.of("name", tool.name());
            return tool.terminal() ? named.put("terminal", true) : named;
        }
    };

    private static final SettingKey<Entries<Tool>> TOOLS = SettingKey.of("tools", Entries.none(), Codec.tables(TOOL));
    private static final SettingKeys KEYS = new SettingKeys(List.of(Settings.THEME, TOOLS));

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config.toml");
    }

    private SettingsFile store() {
        return new SettingsFile(file(), KEYS);
    }

    private static final String USERS_FILE = """
            # my settings
            [ui]
            # the one I like
            theme = "darcula"

            [[tools]]
            # the editor
            name = "vim"
            terminal = true
            since = 2024

            [[tools]]
            terminal = "yes"

            [[tools]]
            name = "idea"
            terminal = "sometimes"

            [mine]
            x = 1
            """;

    @Test
    void theEntriesAreReadInOrderKeepingWhatTheAppDoesNotKnow() throws IOException {
        Files.writeString(file(), USERS_FILE);

        Loaded loaded = store().load();
        Entries<Tool> tools = loaded.settings().get(TOOLS);

        assertEquals(List.of(new Tool("vim", true), new Tool("idea", false)), tools.values());
        assertEquals(
                LinkedHashMap.of("since", (Object) 2024), tools.all().head().unknown());
        // The entry without a name is kept, and so is the terminal of idea that is wrong.
        assertInstanceOf(Entries.Entry.Invalid.class, tools.all().get(1));
        assertEquals(
                LinkedHashMap.of("terminal", (Object) "sometimes"),
                ((Entries.Entry.Valid<Tool>) tools.all().get(2)).wrong());
        assertEquals(LinkedHashMap.empty(), tools.all().get(2).unknown());
        assertEquals(
                Option.of(new SettingsProblem.Invalid(
                        file(), List.of("tools[2].name", "tools[2].terminal", "tools[3].terminal"))),
                loaded.problem());
    }

    @Test
    void anEditWritesTheListKeepingTheRestOfTheFileTheUnknownFieldsAndTheEntriesThatAreWrong() throws IOException {
        Files.writeString(file(), USERS_FILE);
        Settings read = store().load().settings();
        Entries<Tool> tools = read.get(TOOLS);

        Settings edited =
                read.with(TOOLS, tools.replaced(0, new Tool("nvim", true)).added(new Tool("code", false)));
        assertEquals(Either.right(edited), store().save(read, edited));

        String saved = Files.readString(file());
        assertEquals("""
                # my settings
                [ui]
                # the one I like
                theme = "darcula"

                [mine]
                x = 1

                [[tools]]
                name = "nvim"
                terminal = true
                since = 2024
                [[tools]]
                terminal = "yes"
                [[tools]]
                name = "idea"
                terminal = "sometimes"
                [[tools]]
                name = "code"
                """, saved);
        Loaded reread = store().load();
        assertEquals(edited.get(TOOLS), reread.settings().get(TOOLS));
    }

    @Test
    void theCommentsInsideTheListAreLostWhenItIsWritten() throws IOException {
        Files.writeString(file(), USERS_FILE);
        Settings read = store().load().settings();

        store().save(read, read.with(TOOLS, read.get(TOOLS).moved(2, -1)));

        String saved = Files.readString(file());
        assertFalse(saved.contains("# the editor"), saved);
        assertTrue(saved.contains("# the one I like"), saved);
    }

    @Test
    void aListEmptiedHasNoArrayAnymore() throws IOException {
        Files.writeString(file(), """
                [[tools]]
                name = "vim"
                """);
        Settings read = store().load().settings();

        store().save(read, read.with(TOOLS, read.get(TOOLS).removed(0)));

        assertEquals("", Files.readString(file()).strip());
    }

    @Test
    void aListAnotherSessionChangedMeanwhileIsNotWrittenOver() throws IOException {
        Files.writeString(file(), """
                [[tools]]
                name = "vim"
                """);
        Settings first = store().load().settings();
        Settings second = store().load().settings();

        Settings added = second.with(TOOLS, second.get(TOOLS).added(new Tool("idea", false)));
        assertEquals(Either.right(added), store().save(second, added));
        Either<SettingsProblem, Settings> refused =
                store().save(first, first.with(TOOLS, first.get(TOOLS).added(new Tool("code", true))));

        assertInstanceOf(SettingsProblem.Unwritable.class, refused.getLeft());
        assertEquals(added.get(TOOLS), store().load().settings().get(TOOLS));
    }

    @Test
    void anEntryThatIsWrongAndMovedUpDoesNotStopTheNextSave() throws IOException {
        Files.writeString(file(), """
                [[tools]]
                name = "vim"

                [[tools]]
                terminal = "yes"
                """);
        Settings read = store().load().settings();
        Settings deleted = read.with(TOOLS, read.get(TOOLS).removed(0));
        assertEquals(Either.right(deleted), store().save(read, deleted));

        Settings added = deleted.with(TOOLS, deleted.get(TOOLS).added(new Tool("code", false)));

        assertEquals(Either.right(added), store().save(deleted, added));
    }

    @Test
    void aSettingOfOneValueIsWrittenOverWhateverTheFileHasNow() throws IOException {
        Files.writeString(file(), """
                [ui]
                theme = "light"
                """);
        Settings first = store().load().settings();
        Files.writeString(file(), """
                [ui]
                theme = "darcula"
                """);

        Settings dark = first.withTheme(ThemeName.DARK);
        assertEquals(Either.right(dark), store().save(first, dark));
    }

    @Test
    void anUnchangedListIsNotWrittenEvenWhenItsTablesAreWrittenOtherwise() throws IOException {
        String written = """
                [[tools]]
                terminal = false
                name = "vim"

                [[tools]]
                name = "idea"
                """;
        Files.writeString(file(), written);
        Settings read = store().load().settings();

        store().save(read, read.withTheme(ThemeName.LIGHT));

        assertTrue(Files.readString(file()).contains("terminal = false\nname = \"vim\""), Files.readString(file()));
    }

    @Test
    void aValueThatIsNoArrayOfTablesIsWrongAsAWhole() throws IOException {
        Files.writeString(file(), "tools = 3\n");

        Loaded loaded = store().load();

        assertEquals(Entries.none(), loaded.settings().get(TOOLS));
        assertEquals(Option.of(new SettingsProblem.Invalid(file(), List.of("tools"))), loaded.problem());
    }

    @Test
    void anEntryIsMovedOnlyWithinTheList() {
        Entries<Tool> tools = Entries.of(List.of(new Tool("a", false), new Tool("b", false), new Tool("c", false)));

        assertEquals(List.of("b", "a", "c"), tools.moved(1, -1).values().map(Tool::name));
        assertEquals(List.of("a", "c", "b"), tools.moved(1, 1).values().map(Tool::name));
        assertEquals(tools, tools.moved(0, -1));
        assertEquals(tools, tools.moved(2, 1));
    }
}
