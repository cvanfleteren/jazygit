package net.vanfleteren.jazygit.app;

import static net.vanfleteren.jazygit.app.TestApp.SIZE;
import static net.vanfleteren.jazygit.app.TestApp.await;
import static net.vanfleteren.jazygit.app.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.app.keys.StandardSettings;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.settings.SettingsFile;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An agent added from the settings panel, with the keys a user presses, ends up in the settings file (ADR 164).
 */
class JazygitAppAgentsTest {

    @TempDir
    Path dir;

    @Test
    void anAgentAddedInThePanelIsWrittenToTheSettingsFile() throws Exception {
        Path config = dir.resolve("config.toml");
        Files.writeString(config, """
                [ui]
                theme = "darcula"
                """);
        SettingsFile file = new SettingsFile(config, StandardSettings.keys());
        TestApp app =
                new TestApp(new Env(SampleData.git(), file).with(GitHubServices.GH, StubGitHub.of()), file.load());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            pilot.press('s', KeyModifiers.CTRL);
            pilot.press('i');
            pilot.press('a');
            // The name, the first field.
            pilot.press(KeyCode.ENTER);
            type(pilot, "Claude");
            pilot.press(KeyCode.ENTER);
            // The command, the next one.
            pilot.press(KeyCode.DOWN);
            pilot.press(KeyCode.ENTER);
            type(pilot, "claude --model opus");
            pilot.press(KeyCode.ENTER);
            pilot.press('s');

            await(() -> Files.readString(config).contains("name = \"Claude\""), "the agent written to " + config);
        } finally {
            app.onStop();
        }

        assertEquals(
                List.of(new AgentProgram(
                        "Claude", List.of("claude", "--model", "opus"), false, Option.none(), Option.none())),
                Agents.in(file.load().settings()).all());
    }

    private static void type(Pilot pilot, String text) {
        text.chars().forEach(c -> pilot.press((char) c));
    }
}
