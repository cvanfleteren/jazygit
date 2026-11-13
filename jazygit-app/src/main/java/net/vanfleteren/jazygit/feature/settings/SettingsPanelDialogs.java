package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.text.ErrorText;
import net.vanfleteren.jazygit.ui.text.HomeDir;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The settings, and the dialogs that change them.
 */
public final class SettingsPanelDialogs {

    /**
     * Asks which gh executable to run; left empty, the one on the PATH runs.
     */
    public static final TextInputDialog.Spec<GhExecutablePopup> GH_EXECUTABLE = new TextInputDialog.Spec<>(
            "settings-gh",
            GhExecutablePopup.class,
            popup -> Messages.get("dialog.settings.gh.title"),
            Option.of(Messages.get("dialog.settings.gh.placeholder")),
            SettingsPanelMsg.GhExecutableEntered::new);
    /**
     * Asks which browser links open in for the account the repository uses; left empty, none is set for it, and the
     * one gh would choose opens them (ADR 88).
     */
    public static final TextInputDialog.Spec<AccountBrowserPopup> BROWSER = new TextInputDialog.Spec<>(
            "settings-browser",
            AccountBrowserPopup.class,
            popup -> Messages.get(
                    "dialog.settings.browser.title",
                    popup.account().user(),
                    popup.account().host()),
            Option.of(Messages.get("dialog.settings.browser.placeholder")),
            SettingsPanelMsg.BrowserEntered::new);
    /**
     * Asks which account the repository uses: gh's active one, or one of the others gh is logged in to on the
     * repository's host.
     */
    public static final PickDialog.Spec<GitHubAccountPopup, String> ACCOUNT = new PickDialog.Spec<>(
            "settings-account",
            GitHubAccountPopup.class,
            popup ->
                    Messages.get("dialog.settings.account.title", popup.gitHub().host()),
            popup -> accounts(popup.gitHub()).map(PickDialog.Row::of),
            shown -> new SettingsPanelMsg.AccountChosen(user(shown)));
    // The keys of the settings that open the theme dialog, which the panel highlights again when it is cancelled.
    static final char THEME_KEY = 't';
    static final char REPOSITORY_THEME_KEY = 'r';
    // Wide enough for the usual gh executable and account, so it does not grow once they are read.
    private static final int WIDTH = 80;
    /**
     * The settings, one per line under the heading of what they are about, each changed by its key.
     */
    public static final ChoiceDialog.Spec<SettingsPanelPopup> PANEL = new ChoiceDialog.Spec<>(
                    "settings",
                    SettingsPanelPopup.class,
                    popup -> Messages.get("dialog.settings.title"),
                    List.of(
                            new Choice<SettingsPanelPopup>(
                                            THEME_KEY,
                                            popup -> Messages.get("dialog.settings.theme", themeLabel(popup.theme())),
                                            SettingsPanelMsg.ThemeRequested::new)
                                    .withHeading("dialog.settings.group.appearance")
                                    .withDetails("dialog.settings.theme.details"),
                            new Choice<SettingsPanelPopup>(
                                            REPOSITORY_THEME_KEY,
                                            SettingsPanelDialogs::repositoryTheme,
                                            SettingsPanelMsg.RepositoryThemeRequested::new)
                                    .withDetails("dialog.settings.repositoryTheme.details"),
                            new Choice<SettingsPanelPopup>(
                                            'm',
                                            popup -> Messages.get(
                                                    "dialog.settings.animations",
                                                    Messages.get(
                                                            popup.animations()
                                                                    ? "dialog.settings.on"
                                                                    : "dialog.settings.off")),
                                            SettingsPanelMsg.AnimationsToggled::new)
                                    .withDetails("dialog.settings.animations.details"),
                            new Choice<SettingsPanelPopup>(
                                            'g',
                                            SettingsPanelDialogs::ghExecutable,
                                            SettingsPanelMsg.GhExecutableRequested::new)
                                    .withHeading("dialog.settings.group.github")
                                    .withDetails("dialog.settings.gh.details"),
                            new Choice<SettingsPanelPopup>(
                                            'a',
                                            SettingsPanelDialogs::account,
                                            SettingsPanelMsg.AccountRequested::new,
                                            (model, popup) -> popup.gitHub()
                                                    .exists(gitHub ->
                                                            gitHub.logins().isRight()))
                                    .withDetails("dialog.settings.account.details"),
                            new Choice<SettingsPanelPopup>(
                                            'b',
                                            SettingsPanelDialogs::browser,
                                            SettingsPanelMsg.BrowserRequested::new,
                                            (model, popup) -> popup.gitHub()
                                                    .exists(gitHub ->
                                                            gitHub.account().isDefined()))
                                    .withDetails("dialog.settings.browser.details"),
                            new Choice<SettingsPanelPopup>(
                                            'e', SettingsPanelDialogs::file, SettingsPanelMsg.FileRequested::new)
                                    .withHeading("dialog.settings.group.file")
                                    .withDetails("dialog.settings.file.details")),
                    WIDTH)
            .openingAt(SettingsPanelPopup::opensAt);
    // How a warning starts.
    private static final String WARNING = "⚠ ";

    private SettingsPanelDialogs() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningToFocus(new ChoiceDialog<>(PANEL, dispatch)),
                PopupSlot.returningToFocus(new TextInputDialog<>(GH_EXECUTABLE, dispatch)),
                PopupSlot.returningToFocus(new TextInputDialog<>(BROWSER, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(ACCOUNT, dispatch)));
    }

    /**
     * The theme of the repository, as the settings show it: its own, else the one the user's git configuration gives
     * it and where, else none, as the theme for all repositories shows (ADR 70).
     */
    static String repositoryTheme(SettingsPanelPopup popup) {
        RepositoryTheme theme = popup.repositoryTheme();
        String shown = theme.own()
                .map(SettingsPanelDialogs::themeLabel)
                .orElse(() -> theme.inherited()
                        .map(inherited -> Messages.get(
                                "dialog.theme.inherited",
                                themeLabel(inherited.theme()),
                                HomeDir.shown(inherited.origin()))))
                .getOrElse(() -> Messages.get("dialog.settings.repositoryTheme.none"));
        return Messages.get("dialog.settings.repositoryTheme", shown);
    }

    /**
     * The settings file, as the settings show it: where it is, with {@code ~} for the home directory (ADR 111).
     */
    static String file(SettingsPanelPopup popup) {
        return Messages.get(
                "dialog.settings.file",
                popup.file().map(HomeDir::shown).getOrElse(() -> Messages.get("dialog.settings.reading")));
    }

    private static String themeLabel(ThemeName theme) {
        return Messages.get("dialog.theme." + theme.id());
    }

    private static String ghExecutable(SettingsPanelPopup popup) {
        String executable = popup.ghExecutable().getOrElse(() -> Messages.get("dialog.settings.gh.onPath"));
        String version = popup.gitHub()
                .map(gitHub -> gitHub.version().fold(failure -> WARNING + ErrorText.of(failure), found -> found))
                .getOrElse(() -> Messages.get("dialog.settings.reading"));
        return Messages.get("dialog.settings.gh", executable, version);
    }

    private static String account(SettingsPanelPopup popup) {
        return popup.gitHub()
                .map(gitHub -> Messages.get(
                        "dialog.settings.account",
                        gitHub.host(),
                        gitHub.logins().fold(failure -> WARNING + ErrorText.of(failure), logins -> chosen(gitHub))))
                .getOrElse(
                        () -> Messages.get("dialog.settings.account.reading", Messages.get("dialog.settings.reading")));
    }

    /**
     * The browser of the account the repository uses, as the settings show it: the one set for it, else what opens
     * instead and why (ADR 88).
     */
    static String browser(SettingsPanelPopup popup) {
        return popup.gitHub()
                .map(gitHub -> gitHub.account()
                        .map(account -> Messages.get(
                                "dialog.settings.browser",
                                account.user(),
                                account.host(),
                                popup.browsers()
                                        .of(account.host(), account.user())
                                        .getOrElse(() -> instead(gitHub.browser()))))
                        .getOrElse(() -> Messages.get("dialog.settings.browser.unknown")))
                .getOrElse(
                        () -> Messages.get("dialog.settings.browser.reading", Messages.get("dialog.settings.reading")));
    }

    private static String instead(Option<ChosenBrowser> chosen) {
        return chosen.map(browser -> Messages.get(
                        "dialog.settings.browser.instead",
                        browser.command(),
                        Messages.get("dialog.settings.browser.source."
                                + browser.source().name())))
                .getOrElse(() -> Messages.get("dialog.settings.browser.system"));
    }

    /**
     * The account the repository uses, as the settings show it.
     */
    static String chosen(GitHubSetup gitHub) {
        return gitHub.user()
                .map(user -> gitHub.userAvailable()
                        ? user
                        : WARNING + Messages.get("dialog.settings.account.unavailable", user))
                .getOrElse(() -> ghDefault(gitHub));
    }

    private static String ghDefault(GitHubSetup gitHub) {
        return Messages.get(
                "dialog.settings.account.default",
                gitHub.active().getOrElse(() -> Messages.get("dialog.settings.account.none")));
    }

    /**
     * The accounts to choose from, as they are shown: gh's active one first, then each account gh is logged in
     * to on the host. A login is the first word of its line; the line of gh's active one starts with a
     * parenthesis, which no login does.
     */
    static List<String> accounts(GitHubSetup gitHub) {
        return gitHub.onHost()
                .map(login -> login.login()
                        + (login.valid() ? "" : " " + WARNING + Messages.get("dialog.settings.account.invalid"))
                        + (gitHub.user().contains(login.login())
                                ? " " + Messages.get("dialog.settings.account.current")
                                : ""))
                .prepend(ghDefault(gitHub)
                        + (gitHub.user().isEmpty() ? " " + Messages.get("dialog.settings.account.current") : ""));
    }

    /**
     * The account the line {@code shown} of {@link #accounts(GitHubSetup)} stands for; none for gh's active one.
     */
    static Option<String> user(String shown) {
        return Option.when(!shown.startsWith("("), () -> shown.split(" ")[0]);
    }
}
