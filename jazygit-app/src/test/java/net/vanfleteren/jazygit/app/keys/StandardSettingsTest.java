package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.SettingKeys;
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
                loaded.settings().get(Agents.SETTING));
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
                List.of("Claude", "Codex"),
                applied.settings().get(Agents.SETTING).all().map(AgentProgram::name));
        assertTrue(
                applied.changed().contains(Agents.SETTING.path()),
                applied.changed().toString());
    }

    private SettingsFile file(String content) throws IOException {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, content);
        return new SettingsFile(file, StandardSettings.keys());
    }
}
