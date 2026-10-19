package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.PopupSlot;
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
            SettingsPanelMsg.GhExecutableEntered::new,
            SettingsPanelMsg.GhExecutableCancelled::new);
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
            shown -> new SettingsPanelMsg.AccountChosen(user(shown)),
            SettingsPanelMsg.AccountCancelled::new);
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
                                    't',
                                    popup -> Messages.get(
                                            "dialog.settings.theme",
                                            Messages.get("dialog.theme."
                                                    + popup.theme().id())),
                                    SettingsPanelMsg.ThemeRequested::new)
                            .withHeading("dialog.settings.group.appearance"),
                    new Choice<SettingsPanelPopup>(
                                    'g',
                                    SettingsPanelDialogs::ghExecutable,
                                    SettingsPanelMsg.GhExecutableRequested::new)
                            .withHeading("dialog.settings.group.github"),
                    new Choice<>(
                            'a',
                            SettingsPanelDialogs::account,
                            SettingsPanelMsg.AccountRequested::new,
                            (model, popup) -> popup.gitHub()
                                    .exists(gitHub -> gitHub.logins().isRight()))),
            SettingsPanelMsg.Closed::new,
            WIDTH);
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
                PopupSlot.returningToFocus(new PickDialog<>(ACCOUNT, dispatch)));
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
