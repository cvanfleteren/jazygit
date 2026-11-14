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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.settings.AccountBrowsers;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;
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
        return new GitHubSetup(
                Either.right("2.102.0"),
                "github.com",
                user,
                Either.right(LOGINS),
                Option.of(new GhAccount("github.com", user.getOrElse("alice"))),
                Option.of(new ChosenBrowser("xdg-open", ChosenBrowser.Source.DESKTOP)));
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
    void theBrowserOfTheAccountIsTheOneSetElseWhatOpensInsteadAndWhy() {
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARK,
                RepositoryTheme.NONE,
                Option.none(),
                Option.of(setup(Option.of("alice-work"))),
                true,
                true,
                true,
                AccountBrowsers.NONE,
                Option.none());

        assertEquals(
                "Browser for alice-work on github.com: firefox -P work",
                SettingsPanelDialogs.browser(popup.withBrowsers(
                        AccountBrowsers.NONE.with("GitHub.com", "Alice-Work", Option.of("firefox -P work")))));
        assertEquals(
                "Browser for alice-work on github.com: none, xdg-open from the desktop",
                SettingsPanelDialogs.browser(popup));
        assertEquals(
                "Browser for alice-work on github.com: none, w3m from $BROWSER",
                SettingsPanelDialogs.browser(popup.withGitHub(popup.gitHub()
                        .map(gitHub -> new GitHubSetup(
                                gitHub.version(),
                                gitHub.host(),
                                gitHub.user(),
                                gitHub.logins(),
                                gitHub.account(),
                                Option.of(new ChosenBrowser("w3m", ChosenBrowser.Source.BROWSER)))))));
        assertEquals(
                "Browser for the account: not known which account gh runs as",
                SettingsPanelDialogs.browser(popup.withGitHub(popup.gitHub()
                        .map(gitHub -> new GitHubSetup(
                                gitHub.version(),
                                gitHub.host(),
                                gitHub.user(),
                                gitHub.logins(),
                                Option.none(),
                                Option.none())))));
        assertEquals(
                "Browser for the account: reading…", SettingsPanelDialogs.browser(popup.withGitHub(Option.none())));
    }

    @Test
    void theSettingsFileShowsWhereItIsWithATildeForTheHomeDirectory() {
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARK,
                RepositoryTheme.NONE,
                Option.none(),
                Option.none(),
                true,
                true,
                true,
                AccountBrowsers.NONE,
                Option.none());
        Path home = Path.of(System.getProperty("user.home"));

        assertEquals(
                "Edit ~/.config/jazygit/config.toml",
                SettingsPanelDialogs.file(popup.withFile(
                        Option.of(home.resolve(".config/jazygit/config.toml").toString()))));
        assertEquals(
                "Edit /etc/jazygit/config.toml",
                SettingsPanelDialogs.file(popup.withFile(Option.of("/etc/jazygit/config.toml"))));
        assertEquals("Edit reading…", SettingsPanelDialogs.file(popup));
    }

    @Test
    void theThemeOfTheRepositoryIsItsOwnElseTheOneItInherits() {
        RepositoryTheme.Inherited work = new RepositoryTheme.Inherited(ThemeName.LIGHT, "/etc/gitconfig-work");
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARK,
                RepositoryTheme.NONE,
                Option.none(),
                Option.none(),
                true,
                true,
                true,
                AccountBrowsers.NONE,
                Option.none());

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
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARK,
                RepositoryTheme.NONE,
                Option.of("/opt/gh"),
                Option.none(),
                true,
                true,
                true,
                AccountBrowsers.NONE,
                Option.none());
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
                true,
                true,
                false,
                AccountBrowsers.NONE.with("github.com", "alice-work", Option.of("firefox -P work")),
                Option.none());
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
                            "(b)"),
                    lines.dropWhile(line -> !line.contains("Appearance"))
                            .take(13)
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
                ThemeName.DARK,
                RepositoryTheme.NONE,
                Option.none(),
                Option.of(setup(Option.none())),
                true,
                true,
                true,
                AccountBrowsers.NONE,
                Option.none());
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
                List.of(new SettingsPanelMsg.AccountRequested(), new SettingsPanelMsg.ThemeRequested()),
                List.ofAll(dispatched));
    }

    @Test
    void opensOnTheSettingItIsToldTo() throws Exception {
        java.util.List<Msg> dispatched = new ArrayList<>();
        CursorFeedback cursor = new CursorFeedback(dispatched::add);
        ChoiceDialog<SettingsPanelPopup> dialog = new ChoiceDialog<>(SettingsPanelDialogs.PANEL, cursor);
        SettingsPanelPopup popup = new SettingsPanelPopup(
                ThemeName.DARK,
                RepositoryTheme.NONE,
                Option.none(),
                Option.of(setup(Option.none())),
                true,
                true,
                true,
                AccountBrowsers.NONE,
                Option.none(),
                Option.of('r'));
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
