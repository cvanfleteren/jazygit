package net.vanfleteren.jazygit.features.github.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsRows;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.TextInputDialog;

/**
 * The GitHub section of the settings panel (ADR 163): the gh executable, the account gh and git use for the
 * repository, and the browser of that account; and the dialogs that change them.
 */
public final class GitHubSettingsDialogs {

    /**
     * Asks which gh executable to run; left empty, the one on the PATH runs.
     */
    public static final TextInputDialog.Spec<GhExecutablePopup> GH_EXECUTABLE = new TextInputDialog.Spec<>(
            "settings-gh",
            GhExecutablePopup.class,
            popup -> Messages.get("github.dialog.settings.gh.title"),
            Option.of(Messages.get("github.dialog.settings.gh.placeholder")),
            GitHubSettingsMsg.GhExecutableEntered::new);
    /**
     * Asks which browser links open in for the account the repository uses; left empty, none is set for it, and the
     * one gh would choose opens them (ADR 88).
     */
    public static final TextInputDialog.Spec<AccountBrowserPopup> BROWSER = new TextInputDialog.Spec<>(
            "settings-browser",
            AccountBrowserPopup.class,
            popup -> Messages.get(
                    "github.dialog.settings.browser.title",
                    popup.account().user(),
                    popup.account().host()),
            Option.of(Messages.get("github.dialog.settings.browser.placeholder")),
            GitHubSettingsMsg.BrowserEntered::new);
    /**
     * Asks which account the repository uses: gh's active one, or one of the others gh is logged in to on the
     * repository's host.
     */
    public static final PickDialog.Spec<GitHubAccountPopup, String> ACCOUNT = new PickDialog.Spec<>(
            "settings-account",
            GitHubAccountPopup.class,
            popup -> Messages.get(
                    "github.dialog.settings.account.title", popup.gitHub().host()),
            popup -> accounts(popup.gitHub()).map(PickDialog.Row::of),
            shown -> new GitHubSettingsMsg.AccountChosen(user(shown)));

    /**
     * The section: how gh runs for the repository, read again each time the panel opens.
     */
    public static final SettingsSection SECTION = SettingsSection.of(
                    "github.dialog.settings.group",
                    new Choice<SettingsPanelPopup>(
                                    'g',
                                    GitHubSettingsDialogs::ghExecutable,
                                    GitHubSettingsMsg.GhExecutableRequested::new)
                            .withDetails("github.dialog.settings.gh.details"),
                    new Choice<SettingsPanelPopup>(
                                    'a',
                                    GitHubSettingsDialogs::account,
                                    GitHubSettingsMsg.AccountRequested::new,
                                    (model, popup) -> popup.section(GitHubSettingsUpdate.SETUP)
                                            .exists(gitHub -> gitHub.logins().isRight()))
                            .withDetails("github.dialog.settings.account.details"),
                    new Choice<SettingsPanelPopup>(
                                    'b',
                                    GitHubSettingsDialogs::browser,
                                    GitHubSettingsMsg.BrowserRequested::new,
                                    (model, popup) -> popup.section(GitHubSettingsUpdate.SETUP)
                                            .exists(gitHub -> gitHub.account().isDefined()))
                            .withDetails("github.dialog.settings.browser.details"))
            .reading(GitHubSettingsUpdate::opened);

    // How a warning starts.
    private static final String WARNING = "⚠ ";

    private GitHubSettingsDialogs() {}

    /**
     * The dialogs of the section, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningToFocus(new TextInputDialog<>(GH_EXECUTABLE, dispatch)),
                PopupSlot.returningToFocus(new TextInputDialog<>(BROWSER, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(ACCOUNT, dispatch)));
    }

    private static String ghExecutable(SettingsPanelPopup popup) {
        String executable = popup.settings()
                .get(GitHubSettings.GH_EXECUTABLE)
                .getOrElse(() -> Messages.get("github.dialog.settings.gh.onPath"));
        String version = popup.section(GitHubSettingsUpdate.SETUP)
                .map(gitHub -> gitHub.version().fold(failure -> WARNING + ErrorText.of(failure), found -> found))
                .getOrElse(SettingsRows::reading);
        return Messages.get("github.dialog.settings.gh", executable, version);
    }

    private static String account(SettingsPanelPopup popup) {
        return popup.section(GitHubSettingsUpdate.SETUP)
                .map(gitHub -> Messages.get(
                        "github.dialog.settings.account",
                        gitHub.host(),
                        gitHub.logins().fold(failure -> WARNING + ErrorText.of(failure), logins -> chosen(gitHub))))
                .getOrElse(() -> Messages.get("github.dialog.settings.account.reading", SettingsRows.reading()));
    }

    /**
     * The browser of the account the repository uses, as the settings show it: the one set for it, else what opens
     * instead and why (ADR 88).
     */
    static String browser(SettingsPanelPopup popup) {
        return popup.section(GitHubSettingsUpdate.SETUP)
                .map(gitHub -> gitHub.account()
                        .map(account -> Messages.get(
                                "github.dialog.settings.browser",
                                account.user(),
                                account.host(),
                                popup.settings()
                                        .get(GitHubSettings.BROWSERS)
                                        .of(account.host(), account.user())
                                        .getOrElse(() -> instead(gitHub.browser()))))
                        .getOrElse(() -> Messages.get("github.dialog.settings.browser.unknown")))
                .getOrElse(() -> Messages.get("github.dialog.settings.browser.reading", SettingsRows.reading()));
    }

    private static String instead(Option<ChosenBrowser> chosen) {
        return chosen.map(browser -> Messages.get(
                        "github.dialog.settings.browser.instead",
                        browser.command(),
                        Messages.get("github.dialog.settings.browser.source."
                                + browser.source().name())))
                .getOrElse(() -> Messages.get("github.dialog.settings.browser.system"));
    }

    /**
     * The account the repository uses, as the settings show it.
     */
    static String chosen(GitHubSetup gitHub) {
        return gitHub.user()
                .map(user -> gitHub.userAvailable()
                        ? user
                        : WARNING + Messages.get("github.dialog.settings.account.unavailable", user))
                .getOrElse(() -> ghDefault(gitHub));
    }

    private static String ghDefault(GitHubSetup gitHub) {
        return Messages.get(
                "github.dialog.settings.account.default",
                gitHub.active().getOrElse(() -> Messages.get("github.dialog.settings.account.none")));
    }

    /**
     * The accounts to choose from, as they are shown: gh's active one first, then each account gh is logged in
     * to on the host. A login is the first word of its line; the line of gh's active one starts with a
     * parenthesis, which no login does.
     */
    static List<String> accounts(GitHubSetup gitHub) {
        return gitHub.onHost()
                .map(login -> login.login()
                        + (login.valid() ? "" : " " + WARNING + Messages.get("github.dialog.settings.account.invalid"))
                        + (gitHub.user().contains(login.login())
                                ? " " + Messages.get("github.dialog.settings.account.current")
                                : ""))
                .prepend(ghDefault(gitHub)
                        + (gitHub.user().isEmpty()
                                ? " " + Messages.get("github.dialog.settings.account.current")
                                : ""));
    }

    /**
     * The account the line {@code shown} of {@link #accounts(GitHubSetup)} stands for; none for gh's active one.
     */
    static Option<String> user(String shown) {
        return Option.when(!shown.startsWith("("), () -> shown.split(" ")[0]);
    }
}
