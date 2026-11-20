package net.vanfleteren.jazygit.features.chrome.settings;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.app.keys.StandardSettings;
import net.vanfleteren.jazygit.core.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsCmd;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsMsg;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsUpdate;
import net.vanfleteren.jazygit.features.github.settings.GitHubSetup;
import net.vanfleteren.jazygit.kernel.settings.AccountBrowsers;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.CursorFeedback;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import org.junit.jupiter.api.Test;

/**
 * The settings panel of the app: the panel's own sections with those of the features (ADR 163).
 */
class SettingsPanelTest {

    private static final List<GhLogin> LOGINS = List.of(
            new GhLogin("github.com", "alice", true, true),
            new GhLogin("github.com", "alice-work", false, false),
            new GhLogin("ghe.corp", "alice", true, true));

    private static GitHubSetup setup(Option<String> user) {
        return new GitHubSetup(
                Either.right("2.102.0"),
                "github.com",
                user,
                Either.right(LOGINS),
                Option.of(new GhAccount("github.com", user.getOrElse("alice"))),
                Option.of(new ChosenBrowser("xdg-open", ChosenBrowser.Source.DESKTOP)));
    }

    private static SettingsPanelPopup panel(Settings settings, Option<GitHubSetup> gitHub) {
        return new SettingsPanelPopup(settings).with(GitHubSettingsUpdate.SETUP, gitHub);
    }

    @Test
    void eachSettingHasALetterOfItsOwn() {
        List<Character> keys = SettingsPanelDialogs.PANEL.choices().map(Choice::key);

        assertEquals(keys.distinct(), keys);
    }

    @Test
    void theSettingsOfTheFileDoNotClash() {
        assertEquals(List.empty(), StandardSettings.keys().clashes());
    }

    @Test
    void openingThePanelReadsHowGhIsSetUpAndTheFile() {
        assertEquals(
                List.of(new GitHubSettingsCmd.ReadGitHub("gh"), new SettingsPanelCmd.ReadFile()),
                Update.update(TestModels.loaded(), new SettingsPanelMsg.Requested())
                        .cmds());
    }

    @Test
    void showsThatGhIsReadWhileItIs() throws Exception {
        ChoiceDialog<SettingsPanelPopup> dialog =
                new ChoiceDialog<>(SettingsPanelDialogs.PANEL, new ArrayList<Msg>()::add);
        SettingsPanelPopup popup = panel(Settings.DEFAULT.withGhExecutable(Option.of("/opt/gh")), Option.none());
        Element view = stack(
                text("background"),
                dialog.render(TestModels.loaded().openPopup(popup)).get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view)) {
            String screen = RenderedText.ofLarge(runner, () -> view);
            assertTrue(screen.contains("(g) gh executable: /opt/gh (reading…)"), screen);
            assertTrue(screen.contains("(a) Account for this repository: reading…"), screen);
        }
    }

    @Test
    void showsTheSettings() throws Exception {
        ChoiceDialog<SettingsPanelPopup> dialog =
                new ChoiceDialog<>(SettingsPanelDialogs.PANEL, new ArrayList<Msg>()::add);
        SettingsPanelPopup popup = panel(
                Settings.DEFAULT
                        .withTheme(ThemeName.DARCULA)
                        .withAutoFetch(false)
                        .withBrowsers(
                                AccountBrowsers.NONE.with("github.com", "alice-work", Option.of("firefox -P work"))),
                Option.of(setup(Option.of("alice-work"))));
        Element view = stack(
                text("background"),
                dialog.render(TestModels.loaded().openPopup(popup)).get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view)) {
            String screen = RenderedText.ofLarge(runner, () -> view);
            assertTrue(screen.contains("(s) Read the repository every 3 s: on"), screen);
            assertTrue(screen.contains("(f) Fetch from the remotes every minute: off"), screen);
            assertTrue(screen.contains("(g) gh executable: gh on the PATH (2.102.0)"), screen);
            assertTrue(screen.contains("(t) Theme for all repositories: darcula"), screen);
            assertTrue(screen.contains("(r) Theme for this repository: none, the one for all repositories"), screen);
            assertTrue(screen.contains("(a) Account for this repository on github.com: ⚠ alice-work"), screen);
            assertTrue(screen.contains("(b) Browser for alice-work on github.com: firefox -P work"), screen);
            assertTrue(screen.contains("(m) Animations: on"), screen);
            assertTrue(screen.contains("(i) Agents: none"), screen);
            List<String> lines = List.of(screen.split("\n")).map(String::strip);
            assertEquals(
                    List.of(
                            "Appearance",
                            "(t)",
                            "(r)",
                            "(m)",
                            "",
                            "Git",
                            "(s)",
                            "(f)",
                            "",
                            "GitHub",
                            "(g)",
                            "(a)",
                            "(b)",
                            "",
                            "Agents",
                            "(i)"),
                    lines.dropWhile(line -> !line.contains("Appearance"))
                            .take(16)
                            .map(line -> line.replaceAll("^│", "").strip())
                            .map(line -> line.startsWith("(")
                                    ? line.substring(0, 3)
                                    : line.replaceAll("│$", "").strip()));
        }
    }

    @Test
    void theArrowsSkipTheHeadings() throws Exception {
        java.util.List<Msg> dispatched = new ArrayList<>();
        CursorFeedback cursor = new CursorFeedback(dispatched::add);
        ChoiceDialog<SettingsPanelPopup> dialog = new ChoiceDialog<>(SettingsPanelDialogs.PANEL, cursor);
        SettingsPanelPopup popup = panel(Settings.DEFAULT, Option.of(setup(Option.none())));
        Supplier<Element> view = () -> stack(
                text("background"),
                dialog.render(cursor.on(TestModels.loaded().openPopup(popup))).get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            SettledPilot.focus(runner, SettingsPanelDialogs.PANEL.id());
            RenderedText.of(runner, view);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                List.of(new GitHubSettingsMsg.AccountRequested(), new SettingsPanelMsg.ThemeRequested()),
                List.ofAll(dispatched));
    }

    @Test
    void opensOnTheSettingItIsToldTo() throws Exception {
        java.util.List<Msg> dispatched = new ArrayList<>();
        CursorFeedback cursor = new CursorFeedback(dispatched::add);
        ChoiceDialog<SettingsPanelPopup> dialog = new ChoiceDialog<>(SettingsPanelDialogs.PANEL, cursor);
        SettingsPanelPopup popup =
                panel(Settings.DEFAULT, Option.of(setup(Option.none()))).withOpensAt(Option.of('r'));
        Supplier<Element> view = () -> stack(
                text("background"),
                dialog.render(cursor.on(TestModels.loaded().openPopup(popup))).get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            SettledPilot.focus(runner, SettingsPanelDialogs.PANEL.id());
            RenderedText.of(runner, view);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(List.of(new SettingsPanelMsg.RepositoryThemeRequested()), List.ofAll(dispatched));
    }
}
