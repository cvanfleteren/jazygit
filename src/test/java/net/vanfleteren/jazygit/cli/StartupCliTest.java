package net.vanfleteren.jazygit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import net.vanfleteren.jazygit.settings.ThemeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StartupCliTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private Either<StartupCli.Exit, StartupOptions> parse(String... args) {
        return StartupCli.parse(args, new PrintWriter(out), new PrintWriter(err));
    }

    @Test
    void withoutArgumentsTheCurrentDirectoryOpensInTheSavedTheme() {
        assertEquals(Either.right(new StartupOptions(Option.none(), Option.none())), parse());
    }

    @Test
    void aPathIsWhereToOpen() {
        assertEquals(Either.right(new StartupOptions(Option.of(Path.of("src/main")), Option.none())), parse("src/main"));
    }

    @ParameterizedTest
    @EnumSource(ThemeName.class)
    void everyThemeCanBeAskedForByItsName(ThemeName theme) {
        assertEquals(Either.right(new StartupOptions(Option.none(), Option.of(theme))), parse("--theme", theme.id()));
    }

    @Test
    void aPathAndATheme() {
        assertEquals(Either.right(new StartupOptions(Option.of(Path.of("repo")), Option.of(ThemeName.LIGHT))),
                parse("--theme=light", "repo"));
    }

    @Test
    void anUnknownThemeExitsWithTheThemesThereAre() {
        assertEquals(Either.left(new StartupCli.Exit(2)), parse("--theme", "purple"));
        assertTrue(err.toString().contains("'purple' is not a theme; expected one of: dark, light, darcula, dark-colorblind, mono"),
                err.toString());
        assertTrue(err.toString().contains("Usage: jazygit"), err.toString());
        assertEquals("", out.toString());
    }

    @Test
    void anUnknownOptionExits() {
        assertEquals(Either.left(new StartupCli.Exit(2)), parse("--colour", "red"));
        assertTrue(err.toString().contains("--colour"), err.toString());
    }

    @Test
    void twoPathsExit() {
        assertEquals(Either.left(new StartupCli.Exit(2)), parse("one", "two"));
    }

    @Test
    void helpListsTheOptionsAndExits() {
        assertEquals(Either.left(new StartupCli.Exit(0)), parse("--help"));
        assertTrue(List.of("Usage: jazygit", "--theme=THEME", "dark-colorblind", "PATH",
                "--version").forAll(out.toString()::contains), out.toString());
        assertEquals("", err.toString());
    }

    @Test
    void versionPrintsTheVersionAndExits() {
        assertEquals(Either.left(new StartupCli.Exit(0)), parse("--version"));
        assertTrue(out.toString().startsWith("jazygit"), out.toString());
    }
}
