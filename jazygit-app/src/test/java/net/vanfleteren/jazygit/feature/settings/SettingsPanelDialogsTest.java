package net.vanfleteren.jazygit.feature.settings;

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
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.RepositoryTheme;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

class SettingsPanelDialogsTest {

    private static final List<GhLogin> LOGINS = List.of(
            new GhLogin("github.com", "alice", true, true),
            new GhLogin("github.com", "alice-work", false, false),
            new GhLogin("ghe.corp", "alice", true, true));

    private static GitHubSetup setup(Option<String> user) {
        return new GitHubSetup(Either.right("2.102.0"), "github.com", user, Either.right(LOGINS));
    }

    @Test
    void theAccountsOnTheHostWithGhsActiveOneFirst() {
        assertEquals(
                List.of("(gh default: alice) (current)", "alice", "alice-work ⚠ token does not work"),
                SettingsPanelDialogs.accounts(setup(Option.none())));
        assertEquals(
                List.of("(gh default: alice)", "alice", "alice-work ⚠ token does not work (current)"),
                SettingsPanelDialogs.accounts(setup(Option.of("alice-work"))));
    }

    @Test
    void eachLineStandsForItsAccount() {
        assertEquals(
                List.of(Option.none(), Option.of("alice"), Option.of("alice-work")),
                SettingsPanelDialogs.accounts(setup(Option.none())).map(SettingsPanelDialogs::user));
    }

    @Test
    void anAccountWithoutAWorkingTokenIsWarnedAbout() {
        assertEquals("alice", SettingsPanelDialogs.chosen(setup(Option.of("alice"))));
        assertEquals(
                "⚠ alice-work, which gh has no working token of",
                SettingsPanelDialogs.chosen(setup(Option.of("alice-work"))));
        assertEquals("⚠ bob, which gh has no working token of", SettingsPanelDialogs.chosen(setup(Option.of("bob"))));
        assertEquals("(gh default: alice)", SettingsPanelDialogs.chosen(setup(Option.none())));
    }

    @Test
    void theThemeOfTheRepositoryIsItsOwnElseTheOneItInherits() {
        RepositoryTheme.Inherited work = new RepositoryTheme.Inherited(ThemeName.LIGHT, "/etc/gitconfig-work");
        SettingsPanelPopup popup =
                new SettingsPanelPopup(ThemeName.DARK, RepositoryTheme.NONE, Option.none(), Option.none(), true);

        assertEquals(
                "Theme for this repository: ember, blues and oranges",
                SettingsPanelDialogs.repositoryTheme(
                        popup.withRepositoryTheme(new RepositoryTheme(Option.of(ThemeName.EMBER), Option.of(work)))));
        assertEquals(
                "Theme for this repository: light, from /etc/gitconfig-work",
                SettingsPanelDialogs.repositoryTheme(
                        popup.withRepositoryTheme(new RepositoryTheme(Option.none(), Option.of(work)))));
        assertEquals(
                "Theme for this repository: none, the one for all repositories",
                SettingsPanelDialogs.repositoryTheme(popup));
    }

    @Test
    void showsThatGhIsReadWhileItIs() throws Exception {
        ChoiceDialog<SettingsPanelPopup> dialog =
                new ChoiceDialog<>(SettingsPanelDialogs.PANEL, new ArrayList<Msg>()::add);
        SettingsPanelPopup popup =
                new SettingsPanelPopup(ThemeName.DARK, RepositoryTheme.NONE, Option.of("/opt/gh"), Option.none(), true);
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
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARCULA,
                RepositoryTheme.NONE,
                Option.none(),
                Option.of(setup(Option.of("alice-work"))),
                true);
        Element view = stack(
                text("background"),
                dialog.render(TestModels.loaded().openPopup(popup)).get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view)) {
            String screen = RenderedText.ofLarge(runner, () -> view);
            assertTrue(screen.contains("(g) gh executable: gh on the PATH (2.102.0)"), screen);
            assertTrue(screen.contains("(t) Theme for all repositories: darcula"), screen);
            assertTrue(screen.contains("(r) Theme for this repository: none, the one for all repositories"), screen);
            assertTrue(screen.contains("(a) Account for this repository on github.com: ⚠ alice-work"), screen);
            assertTrue(screen.contains("(m) Animations: on"), screen);
            List<String> lines = List.of(screen.split("\n")).map(String::strip);
            assertEquals(
                    List.of("Appearance", "(t)", "(r)", "(m)", "", "GitHub", "(g)", "(a)"),
                    lines.dropWhile(line -> !line.contains("Appearance"))
                            .take(8)
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
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARK, RepositoryTheme.NONE, Option.none(), Option.of(setup(Option.none())), true);
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
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                List.of(new SettingsPanelMsg.AccountRequested(), new SettingsPanelMsg.ThemeRequested()),
                List.ofAll(dispatched));
    }
}
