package net.vanfleteren.jazygit.features.github.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.kernel.settings.AccountBrowsers;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import org.junit.jupiter.api.Test;

/**
 * What the GitHub section of the settings panel shows.
 */
class GitHubSettingsDialogsTest {

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

    private static SettingsPanelPopup panel(Option<GitHubSetup> gitHub) {
        return new SettingsPanelPopup(Settings.DEFAULT).with(GitHubSettingsUpdate.SETUP, gitHub);
    }

    @Test
    void theAccountsOnTheHostWithGhsActiveOneFirst() {
        assertEquals(
                List.of("(gh default: alice) (current)", "alice", "alice-work ⚠ token does not work"),
                GitHubSettingsDialogs.accounts(setup(Option.none())));
        assertEquals(
                List.of("(gh default: alice)", "alice", "alice-work ⚠ token does not work (current)"),
                GitHubSettingsDialogs.accounts(setup(Option.of("alice-work"))));
    }

    @Test
    void eachLineStandsForItsAccount() {
        assertEquals(
                List.of(Option.none(), Option.of("alice"), Option.of("alice-work")),
                GitHubSettingsDialogs.accounts(setup(Option.none())).map(GitHubSettingsDialogs::user));
    }

    @Test
    void anAccountWithoutAWorkingTokenIsWarnedAbout() {
        assertEquals("alice", GitHubSettingsDialogs.chosen(setup(Option.of("alice"))));
        assertEquals(
                "⚠ alice-work, which gh has no working token of",
                GitHubSettingsDialogs.chosen(setup(Option.of("alice-work"))));
        assertEquals("⚠ bob, which gh has no working token of", GitHubSettingsDialogs.chosen(setup(Option.of("bob"))));
        assertEquals("(gh default: alice)", GitHubSettingsDialogs.chosen(setup(Option.none())));
    }

    @Test
    void theBrowserOfTheAccountIsTheOneSetElseWhatOpensInsteadAndWhy() {
        GitHubSetup gitHub = setup(Option.of("alice-work"));
        SettingsPanelPopup popup = panel(Option.of(gitHub));

        assertEquals(
                "Browser for alice-work on github.com: firefox -P work",
                GitHubSettingsDialogs.browser(popup.withSettings(Settings.DEFAULT.with(
                        GitHubSettings.BROWSERS,
                        AccountBrowsers.NONE.with("GitHub.com", "Alice-Work", Option.of("firefox -P work"))))));
        assertEquals(
                "Browser for alice-work on github.com: none, xdg-open from the desktop",
                GitHubSettingsDialogs.browser(popup));
        assertEquals(
                "Browser for alice-work on github.com: none, w3m from $BROWSER",
                GitHubSettingsDialogs.browser(panel(Option.of(new GitHubSetup(
                        gitHub.version(),
                        gitHub.host(),
                        gitHub.user(),
                        gitHub.logins(),
                        gitHub.account(),
                        Option.of(new ChosenBrowser("w3m", ChosenBrowser.Source.BROWSER)))))));
        assertEquals(
                "Browser for the account: not known which account gh runs as",
                GitHubSettingsDialogs.browser(panel(Option.of(new GitHubSetup(
                        gitHub.version(),
                        gitHub.host(),
                        gitHub.user(),
                        gitHub.logins(),
                        Option.none(),
                        Option.none())))));
        assertEquals("Browser for the account: reading…", GitHubSettingsDialogs.browser(panel(Option.none())));
    }
}
