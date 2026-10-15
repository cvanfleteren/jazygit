package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.settings.SettingsStore.Loaded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsFileTest {

    private static final Settings DARCULA = Settings.DEFAULT.withTheme(ThemeName.DARCULA);

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("jazygit").resolve("config.toml");
    }

    private SettingsFile settingsFile() {
        return new SettingsFile(file());
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), content);
    }

    @Test
    void aMissingFileGivesTheDefaultsAndIsNotCreated() {
        assertEquals(new Loaded(Settings.DEFAULT, Option.none()), settingsFile().load());
        assertFalse(Files.exists(file()));
    }

    @Test
    void readsTheTheme() throws IOException {
        write("""
                [ui]
                theme = "darcula"
                """);

        assertEquals(new Loaded(DARCULA, Option.none()), settingsFile().load());
    }

    @Test
    void anUnknownThemeGivesTheDefaultAndSaysSo() throws IOException {
        write("""
                [ui]
                theme = "pink"
                """);

        assertEquals(
                new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Invalid(file(), List.of("ui.theme")))),
                settingsFile().load());
    }

    @Test
    void aThemeThatIsNoTextIsInvalid() throws IOException {
        write("""
                [ui]
                theme = 3
                """);

        assertEquals(
                Option.of(new SettingsProblem.Invalid(file(), List.of("ui.theme"))),
                settingsFile().load().problem());
    }

    @Test
    void unknownKeysAreIgnored() throws IOException {
        write("""
                future = true
                [ui]
                theme = "darcula"
                """);

        assertEquals(new Loaded(DARCULA, Option.none()), settingsFile().load());
    }

    @Test
    void aFileThatIsNoTomlGivesTheDefaultsAndSaysSo() throws IOException {
        write("theme = ");

        Loaded loaded = settingsFile().load();

        assertEquals(Settings.DEFAULT, loaded.settings());
        assertInstanceOf(SettingsProblem.Unreadable.class, loaded.problem().get());
        assertEquals(file(), loaded.problem().get().file());
    }

    @Test
    void savingCreatesTheFileAndItsDirectory() {
        assertEquals(Either.right(DARCULA), settingsFile().save(Settings.DEFAULT, DARCULA));

        assertEquals(new Loaded(DARCULA, Option.none()), settingsFile().load());
    }

    @Test
    void savingKeepsCommentsOrderAndUnknownKeys() throws IOException {
        write("""
                # my settings
                future = true

                [ui]
                # the one I like
                theme = "light"
                other = 3

                [mine]
                x = [1, 2]
                """);

        settingsFile().save(Settings.DEFAULT.withTheme(ThemeName.LIGHT), DARCULA);

        String saved = Files.readString(file());
        List<String> lines = List.of(saved.split("\n")).map(String::strip);
        assertTrue(
                lines.containsAll(List.of(
                        "# my settings",
                        "future = true",
                        "# the one I like",
                        "theme = \"darcula\"",
                        "other = 3",
                        "x = [1, 2]")),
                saved);
        assertTrue(lines.indexOf("theme = \"darcula\"") < lines.indexOf("other = 3"), saved);
        assertTrue(lines.indexOf("other = 3") < lines.indexOf("[mine]"), saved);
    }

    @Test
    void savingOnlyChangesWhatChanged() throws IOException {
        // Edited by hand while jazygit ran: a setting that did not change in jazygit keeps the edit.
        write("""
                [ui]
                theme = "light"
                """);

        settingsFile().save(Settings.DEFAULT, Settings.DEFAULT);

        assertEquals(
                Settings.DEFAULT.withTheme(ThemeName.LIGHT),
                settingsFile().load().settings());
    }

    @Test
    void readsTheGhExecutable() throws IOException {
        write("""
                [github]
                executable = "C:\\\\Tools\\\\gh.exe"
                """);

        Settings settings = settingsFile().load().settings();

        assertEquals(Option.of("C:\\Tools\\gh.exe"), settings.ghExecutable());
        assertEquals("C:\\Tools\\gh.exe", settings.gh());
    }

    @Test
    void withoutAGhExecutableTheOneOnThePathRuns() {
        assertEquals("gh", Settings.DEFAULT.gh());
    }

    @Test
    void aBlankGhExecutableIsInvalid() throws IOException {
        write("""
                [github]
                executable = " "
                """);

        Loaded loaded = settingsFile().load();

        assertEquals(Option.none(), loaded.settings().ghExecutable());
        assertEquals(Option.of(new SettingsProblem.Invalid(file(), List.of("github.executable"))), loaded.problem());
    }

    @Test
    void savingTheGhExecutable() {
        Settings chosen = Settings.DEFAULT.withGhExecutable(Option.of("/opt/gh/bin/gh"));

        settingsFile().save(Settings.DEFAULT, chosen);

        assertEquals(chosen, settingsFile().load().settings());
    }

    @Test
    void goingBackToTheGhOnThePathRemovesTheExecutable() throws IOException {
        Settings chosen = Settings.DEFAULT.withGhExecutable(Option.of("/opt/gh/bin/gh"));
        settingsFile().save(Settings.DEFAULT, chosen);

        settingsFile().save(chosen, Settings.DEFAULT);

        assertFalse(Files.readString(file()).contains("executable"), Files.readString(file()));
        assertEquals(Settings.DEFAULT, settingsFile().load().settings());
    }

    @Test
    void aFileThatIsNoTomlIsNotOverwritten() throws IOException {
        write("theme = ");

        Either<SettingsProblem, Settings> saved = settingsFile().save(Settings.DEFAULT, DARCULA);

        assertInstanceOf(SettingsProblem.Unwritable.class, saved.getLeft());
        assertEquals("theme = ", Files.readString(file()));
    }

    @Test
    void aDirectoryThatCannotBeMadeIsReported() throws IOException {
        Files.writeString(dir.resolve("jazygit"), "a file where the directory should be");

        Either<SettingsProblem, Settings> saved = settingsFile().save(Settings.DEFAULT, DARCULA);

        assertEquals(file(), saved.getLeft().file());
        assertInstanceOf(SettingsProblem.Unwritable.class, saved.getLeft());
    }
}
