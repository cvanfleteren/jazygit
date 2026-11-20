package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.features.changes.edit.EditorSettings;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.kernel.settings.AccountBrowsers;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.SettingKeys;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsFile;
import net.vanfleteren.jazygit.kernel.settings.SettingsProblem;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore.Loaded;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings the app reads and writes: the kernel's, and those each feature keeps (ADR 163).
 */
class StandardSettingsTest {

    private static final String AGENTS = """
            [ui]
            theme = "darcula"

            [[agents]]
            # the main one
            name = "Claude"
            command = ["claude", "{prompt}"]
            shortcut = "c"

            [[agents]]
            name = "Broken"
            """;

    @TempDir
    Path dir;

    @Test
    void theKernelsSettingsComeFirstAndThenTheFeatures() {
        List<SettingKey<?>> keys = StandardSettings.keys().all();

        assertEquals(
                SettingKeys.KERNEL.all(), keys.take(SettingKeys.KERNEL.all().size()));
        assertTrue(keys.contains(Agents.SETTING));
    }

    @Test
    void theFileReadsTheAgentsOfTheirFeature() throws IOException {
        Loaded loaded = file(AGENTS).load();

        assertEquals(
                new Agents(List.of(new AgentProgram(
                        "Claude", List.of("claude", "{prompt}"), false, Option.some('c'), Option.none()))),
                Agents.in(loaded.settings()));
        assertEquals(
                Option.of(new SettingsProblem.Invalid(dir.resolve("config.toml"), List.of("agents[2].command"))),
                loaded.problem());
        assertEquals(StandardSettings.keys(), loaded.keys());
    }

    @Test
    void savingAnotherSettingLeavesTheAgentsAsTheUserWroteThem() throws IOException {
        SettingsFile file = file(AGENTS);
        Loaded loaded = file.load();

        file.save(loaded.settings(), loaded.settings().withTheme(ThemeName.LIGHT));

        // The TOML writer drops the blank line between the tables of an array, as it did before.
        String saved = Files.readString(dir.resolve("config.toml"));
        assertTrue(saved.contains("# the main one\nname = \"Claude\""), saved);
        assertTrue(saved.contains("name = \"Broken\""), saved);
        assertEquals(
                loaded.settings().get(Agents.SETTING), file.load().settings().get(Agents.SETTING));
    }

    @Test
    void anAgentAddedInThePanelIsWrittenAfterTheOthersWhichStayAsTheyWere() throws IOException {
        SettingsFile file = file(AGENTS.replace("shortcut = \"c\"", "shortcut = \"c\"\nmodel = \"opus\""));
        Loaded loaded = file.load();
        AgentProgram codex = new AgentProgram("Codex", List.of("codex"), true, Option.none(), Option.of("Fix it"));

        file.save(
                loaded.settings(),
                loaded.settings()
                        .with(
                                Agents.SETTING,
                                loaded.settings().get(Agents.SETTING).added(codex)));

        String saved = Files.readString(dir.resolve("config.toml"));
        assertTrue(saved.contains("theme = \"darcula\""), saved);
        // A field the app does not know, and an entry it cannot read, are written back as they were.
        assertTrue(saved.contains("model = \"opus\""), saved);
        assertTrue(saved.contains("name = \"Broken\""), saved);
        Loaded reread = file.load();
        assertEquals(
                List.of("Claude", "Codex"), Agents.in(reread.settings()).all().map(AgentProgram::name));
        assertEquals(codex, Agents.in(reread.settings()).all().last());
        assertEquals(loaded.problem(), reread.problem());
    }

    @Test
    void anAgentAddedToTheFileByHandIsAppliedWhenItIsReadAgain() throws IOException {
        SettingsFile file = file(AGENTS);
        Loaded loaded = file.load();
        Files.writeString(dir.resolve("config.toml"), AGENTS + """

                [[agents]]
                name = "Codex"
                command = ["codex"]
                """);

        SettingsStore.Applied applied = file.load().appliedTo(loaded.settings(), loaded);

        assertEquals(
                List.of("Claude", "Codex"), Agents.in(applied.settings()).all().map(AgentProgram::name));
        assertTrue(
                applied.changed().contains(Agents.SETTING.path()),
                applied.changed().toString());
    }

    @Test
    void readsTheEditorsInTheirOrder() throws IOException {
        Loaded loaded = file("""
                [[editors]]
                name = "IntelliJ"
                command = ["idea", "{dir}"]
                shortcut = "i"

                [[editors]]
                name = "Neovim"
                command = ["nvim", "."]
                terminal = true
                """).load();

        assertEquals(Option.none(), loaded.problem());
        assertEquals(
                new Editors(List.of(
                        EditorProgram.ofDir("IntelliJ", List.of("idea", "{dir}"), false, Option.of('i')),
                        EditorProgram.ofDir("Neovim", List.of("nvim", "."), true, Option.none()))),
                loaded.settings().get(EditorSettings.EDITORS));
    }

    @Test
    void anEditorWithoutACommandIsLeftOutAndSaidSo() throws IOException {
        Loaded loaded = file("""
                [[editors]]
                name = "Broken"

                [[editors]]
                name = "Kate"
                dir-command = ["kate"]
                """).load();

        assertEquals(
                Option.of(new SettingsProblem.Invalid(dir.resolve("config.toml"), List.of("editors[1].dir-command"))),
                loaded.problem());
        assertEquals(
                List.of("Kate"),
                loaded.settings().get(EditorSettings.EDITORS).all().map(EditorProgram::name));
    }

    @Test
    void savingAnotherSettingKeepsTheEditors() throws IOException {
        SettingsFile file = file("""
                [ui]
                theme = "light"

                [[editors]]
                name = "Kate"
                command = ["kate", "{dir}"]
                shortcut = "k"
                """);
        Settings loaded = file.load().settings();

        file.save(loaded, loaded.withTheme(ThemeName.DARCULA));

        Settings reread = file.load().settings();
        assertEquals(ThemeName.DARCULA, reread.theme());
        assertEquals(loaded.get(EditorSettings.EDITORS), reread.get(EditorSettings.EDITORS));
    }

    @Test
    void readsTheGhExecutable() throws IOException {
        Settings settings = file("""
                [github]
                executable = "C:\\\\Tools\\\\gh.exe"
                """).load().settings();

        assertEquals(Option.of("C:\\Tools\\gh.exe"), settings.get(GitHubSettings.GH_EXECUTABLE));
        assertEquals("C:\\Tools\\gh.exe", GitHubSettings.gh(settings));
    }

    @Test
    void withoutAGhExecutableTheOneOnThePathRuns() {
        assertEquals("gh", GitHubSettings.gh(Settings.DEFAULT));
    }

    @Test
    void aBlankGhExecutableIsInvalid() throws IOException {
        Loaded loaded = file("""
                [github]
                executable = " "
                """).load();

        assertEquals(Option.none(), loaded.settings().get(GitHubSettings.GH_EXECUTABLE));
        assertEquals(
                Option.of(new SettingsProblem.Invalid(dir.resolve("config.toml"), List.of("github.executable"))),
                loaded.problem());
    }

    @Test
    void savingTheGhExecutable() throws IOException {
        SettingsFile file = file("");
        Settings chosen = Settings.DEFAULT.with(GitHubSettings.GH_EXECUTABLE, Option.of("/opt/gh/bin/gh"));

        file.save(Settings.DEFAULT, chosen);

        assertEquals(chosen, file.load().settings());
    }

    @Test
    void goingBackToTheGhOnThePathRemovesTheExecutable() throws IOException {
        SettingsFile file = file("");
        Settings chosen = Settings.DEFAULT.with(GitHubSettings.GH_EXECUTABLE, Option.of("/opt/gh/bin/gh"));
        file.save(Settings.DEFAULT, chosen);

        file.save(chosen, Settings.DEFAULT);

        String saved = Files.readString(dir.resolve("config.toml"));
        assertFalse(saved.contains("executable"), saved);
        assertEquals(Settings.DEFAULT, file.load().settings());
    }

    @Test
    void readsTheBrowserOfEachAccountMatchingHostAndLoginWithoutRegardToCase() throws IOException {
        Loaded loaded = file("""
                [github.browsers."github.com"]
                cvf-work = "firefox -P work"
                CVanfleteren = "firefox -P personal"
                blank = " "

                [github.browsers."GHE.acme.com"]
                cvf = "chromium --profile-directory=Acme"
                """).load();
        AccountBrowsers browsers = loaded.settings().get(GitHubSettings.BROWSERS);

        assertEquals(Option.none(), loaded.problem());
        assertEquals(Option.of("firefox -P work"), browsers.of("github.com", "cvf-work"));
        assertEquals(Option.of("firefox -P personal"), browsers.of("GitHub.com", "cvanfleteren"));
        assertEquals(Option.of("chromium --profile-directory=Acme"), browsers.of("ghe.acme.com", "CVF"));
        assertEquals(Option.none(), browsers.of("github.com", "blank"));
        assertEquals(Option.none(), browsers.of("github.com", "cvf"));
    }

    @Test
    void aBrowserThatIsNoTextIsInvalid() throws IOException {
        Loaded loaded = file("""
                [github.browsers."github.com"]
                cvf = 3
                """).load();

        assertEquals(AccountBrowsers.NONE, loaded.settings().get(GitHubSettings.BROWSERS));
        assertEquals(
                Option.of(new SettingsProblem.Invalid(dir.resolve("config.toml"), List.of("github.browsers"))),
                loaded.problem());
    }

    @Test
    void savingABrowserKeepsTheOthersAndTakingTheLastAwayLeavesNoTable() throws IOException {
        SettingsFile file = file("""
                [github]
                executable = "/opt/gh"

                [github.browsers."github.com"]
                # by hand
                cvf = "firefox -P personal"
                """);
        Path path = dir.resolve("config.toml");
        Settings read = file.load().settings();
        Settings work = read.with(
                GitHubSettings.BROWSERS,
                read.get(GitHubSettings.BROWSERS).with("github.com", "cvf-work", Option.of("firefox -P work")));

        file.save(read, work);

        assertEquals(work, file.load().settings());
        assertTrue(Files.readString(path).contains("# by hand"), Files.readString(path));

        Settings none = work.with(
                GitHubSettings.BROWSERS,
                work.get(GitHubSettings.BROWSERS)
                        .with("github.com", "CVF", Option.none())
                        .with("github.com", "cvf-work", Option.none()));
        file.save(work, none);

        assertFalse(Files.readString(path).contains("browsers"), Files.readString(path));
        assertEquals(none, file.load().settings());
    }

    private SettingsFile file(String content) throws IOException {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, content);
        return new SettingsFile(file, StandardSettings.keys());
    }
}
