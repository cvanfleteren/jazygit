package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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

    @Test
    void readsTheWidthsOfTheLeftColumns() throws IOException {
        write("""
                [ui]
                repository-width = 40
                pull-requests-width = 55
                """);

        assertEquals(
                new Loaded(Settings.DEFAULT.withRepositoryWidth(40).withPullRequestsWidth(55), Option.none()),
                settingsFile().load());
    }

    @Test
    void readsTheHeightsOfThePanesOfTheLeftColumns() throws IOException {
        write("""
                [ui]
                repository-heights = [30, 20, 25]
                pull-requests-heights = [50, 25]
                """);

        assertEquals(
                new Loaded(
                        Settings.DEFAULT
                                .withRepositoryHeights(List.of(30, 20, 25))
                                .withPullRequestsHeights(List.of(50, 25)),
                        Option.none()),
                settingsFile().load());
    }

    @Test
    void heightsOfTheWrongCountOrTooShortAPaneAreInvalid() throws IOException {
        write("""
                [ui]
                repository-heights = [30, 20]
                pull-requests-heights = [50, 45]
                """);

        assertEquals(
                new Loaded(
                        Settings.DEFAULT,
                        Option.of(new SettingsProblem.Invalid(
                                file(), List.of("ui.repository-heights", "ui.pull-requests-heights")))),
                settingsFile().load());
    }

    @Test
    void savedHeightsAreReadBackAndGoingBackToTheDefaultRemovesThem() throws IOException {
        Settings tall = Settings.DEFAULT.withRepositoryHeights(List.of(40, 15, 30));
        settingsFile().save(Settings.DEFAULT, tall);

        assertEquals(new Loaded(tall, Option.none()), settingsFile().load());

        settingsFile().save(tall, Settings.DEFAULT);

        assertFalse(Files.readString(file()).contains("heights"), Files.readString(file()));
    }

    @Test
    void aWidthOutOfRangeOrNoWholeNumberIsInvalid() throws IOException {
        write("""
                [ui]
                repository-width = 90
                pull-requests-width = 40.5
                """);

        assertEquals(
                new Loaded(
                        Settings.DEFAULT,
                        Option.of(new SettingsProblem.Invalid(
                                file(), List.of("ui.repository-width", "ui.pull-requests-width")))),
                settingsFile().load());
    }

    @Test
    void readsTheDiffStyleAndAnUnknownOneIsInvalid() throws IOException {
        write("""
                [ui]
                diff-style = "rich"
                """);
        assertEquals(
                new Loaded(Settings.DEFAULT.withDiffStyle(DiffStyle.RICH), Option.none()),
                settingsFile().load());

        write("""
                [ui]
                diff-style = "fancy"
                """);
        assertEquals(
                new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Invalid(file(), List.of("ui.diff-style")))),
                settingsFile().load());
    }

    @Test
    void readsWhetherCodeIsColouredByItsSyntaxAndAnythingButABooleanIsInvalid() throws IOException {
        write("""
                [ui]
                diff-syntax = false
                """);
        assertEquals(
                new Loaded(Settings.DEFAULT.withDiffSyntax(false), Option.none()),
                settingsFile().load());

        write("""
                [ui]
                diff-syntax = "no"
                """);
        assertEquals(
                new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Invalid(file(), List.of("ui.diff-syntax")))),
                settingsFile().load());
    }

    @Test
    void readsWhetherChangedWordsAreHighlightedAndAnythingButABooleanIsInvalid() throws IOException {
        write("""
                [ui]
                diff-words = false
                """);
        assertEquals(
                new Loaded(Settings.DEFAULT.withDiffWords(false), Option.none()),
                settingsFile().load());

        write("""
                [ui]
                diff-words = "no"
                """);
        assertEquals(
                new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Invalid(file(), List.of("ui.diff-words")))),
                settingsFile().load());
    }

    @Test
    void savingTheDiffWordsAndGoingBackToTheDefaultRemovesIt() throws IOException {
        Settings wholeLines = Settings.DEFAULT.withDiffWords(false);
        settingsFile().save(Settings.DEFAULT, wholeLines);

        assertEquals(wholeLines, settingsFile().load().settings());

        settingsFile().save(wholeLines, Settings.DEFAULT);

        assertFalse(Files.readString(file()).contains("diff-words"), Files.readString(file()));
    }

    @Test
    void savingTheDiffSyntaxAndGoingBackToTheDefaultRemovesIt() throws IOException {
        Settings plainCode = Settings.DEFAULT.withDiffSyntax(false);
        settingsFile().save(Settings.DEFAULT, plainCode);

        assertEquals(plainCode, settingsFile().load().settings());

        settingsFile().save(plainCode, Settings.DEFAULT);

        assertFalse(Files.readString(file()).contains("diff-syntax"), Files.readString(file()));
    }

    @Test
    void savingTheDiffStyleAndGoingBackToTheDefaultRemovesIt() throws IOException {
        Settings rich = Settings.DEFAULT.withDiffStyle(DiffStyle.RICH);
        settingsFile().save(Settings.DEFAULT, rich);

        assertEquals(rich, settingsFile().load().settings());

        settingsFile().save(rich, Settings.DEFAULT);

        assertFalse(Files.readString(file()).contains("diff-style"), Files.readString(file()));
    }

    @Test
    void savingAWidthAndGoingBackToTheDefaultRemovesIt() throws IOException {
        Settings wide = Settings.DEFAULT.withPullRequestsWidth(50);
        settingsFile().save(Settings.DEFAULT, wide);

        assertEquals(wide, settingsFile().load().settings());
        assertFalse(Files.readString(file()).contains("repository-width"), Files.readString(file()));

        settingsFile().save(wide, Settings.DEFAULT);

        assertFalse(Files.readString(file()).contains("width"), Files.readString(file()));
    }

    @Test
    void savingThroughASymlinkKeepsTheLinkAndChangesWhatItLeadsTo() throws IOException {
        Path real = Files.createDirectories(dir.resolve("dotfiles")).resolve("config.toml");
        Files.writeString(real, "");
        Files.createDirectories(file().getParent());
        Files.createSymbolicLink(file(), real);

        settingsFile().save(Settings.DEFAULT, DARCULA);

        assertTrue(Files.isSymbolicLink(file()));
        assertEquals(DARCULA, new SettingsFile(real).load().settings());
    }

    @Test
    void savingKeepsThePermissionsOfTheFile() throws IOException {
        write("");
        Files.setPosixFilePermissions(file(), PosixFilePermissions.fromString("rw-rw----"));

        settingsFile().save(Settings.DEFAULT, DARCULA);

        assertEquals(PosixFilePermissions.fromString("rw-rw----"), Files.getPosixFilePermissions(file()));
    }

    @Test
    void savingThroughASymlinkWhoseTargetDoesNotExistYetCreatesTheTarget() throws IOException {
        Path real = Files.createDirectories(dir.resolve("dotfiles")).resolve("config.toml");
        Files.createDirectories(file().getParent());
        Files.createSymbolicLink(file(), real);

        settingsFile().save(Settings.DEFAULT, DARCULA);

        assertTrue(Files.isSymbolicLink(file()));
        assertEquals(DARCULA, new SettingsFile(real).load().settings());
    }

    @Test
    void savingThroughASymlinkLeavesNoLockFileWhereItLeads() throws IOException {
        Path real = Files.createDirectories(dir.resolve("dotfiles")).resolve("config.toml");
        Files.writeString(real, "");
        Files.createDirectories(file().getParent());
        Files.createSymbolicLink(file(), real);

        settingsFile().save(Settings.DEFAULT, DARCULA);

        try (Stream<Path> files = Files.list(real.getParent())) {
            assertEquals(java.util.List.of(real), files.toList());
        }
    }

    @Test
    void savesAndChangesFromManyThreadsLoseNone() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            executor.invokeAll(IntStream.range(0, 16)
                            .<Callable<Object>>mapToObj(i -> () -> i == 0
                                    ? settingsFile().save(Settings.DEFAULT, DARCULA)
                                    : TomlFile.change(file(), config -> config.set("key" + i, i)))
                            .toList())
                    .forEach(done -> Try.of(done::get).get());
        } finally {
            executor.shutdown();
        }

        assertEquals(DARCULA, settingsFile().load().settings());
        assertEquals(
                15,
                TomlFile.read(file()).get().entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("key"))
                        .count());
    }
}
