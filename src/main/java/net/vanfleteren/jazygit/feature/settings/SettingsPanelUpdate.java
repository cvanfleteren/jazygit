package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.theme.ThemeMsg;
import net.vanfleteren.jazygit.feature.theme.ThemeUpdate;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Looking at the settings and changing them: the theme, the gh executable, and the account gh and git use for the
 * repository. A change to how gh runs shows the repository again, as if the app had been started with it.
 */
public final class SettingsPanelUpdate {

    private SettingsPanelUpdate() {
    }

    public static Next update(Model model, SettingsPanelMsg msg) {
        return switch (msg) {
            // The key works everywhere, but does not take away a dialog that is open.
            case SettingsPanelMsg.Requested() -> model.popup().isDefined()
                    ? Next.of(model)
                    : Next.of(model.openPopup(new SettingsPanelPopup(model.theme(), model.settings().ghExecutable(),
                    Option.none())), new SettingsPanelCmd.ReadGitHub(model.settings().gh()));
            // Read for a panel that may be closed already, or open on another gh executable.
            case SettingsPanelMsg.GitHubRead(GitHubSetup gitHub) -> Next.of(model.popup(SettingsPanelPopup.class)
                    .map(panel -> model.openPopup(new SettingsPanelPopup(panel.theme(), panel.ghExecutable(),
                            Option.of(gitHub))))
                    .getOrElse(model));
            case SettingsPanelMsg.Closed() -> Next.of(model.withoutPopup(SettingsPanelPopup.class));
            case SettingsPanelMsg.ThemeRequested() ->
                    ThemeUpdate.update(model.withoutPopup(SettingsPanelPopup.class), new ThemeMsg.Requested());
            case SettingsPanelMsg.GhExecutableRequested() -> Next.of(model.popup(SettingsPanelPopup.class)
                    .map(panel -> model.openPopup(new GhExecutablePopup(panel.ghExecutable().getOrElse(""))))
                    .getOrElse(model));
            case SettingsPanelMsg.GhExecutableCancelled() -> Next.of(model.withoutPopup(GhExecutablePopup.class));
            case SettingsPanelMsg.GhExecutableEntered(String executable) -> model.popup(GhExecutablePopup.class).isDefined()
                    ? Next.of(model.withoutPopup(GhExecutablePopup.class),
                    new SettingsPanelCmd.CheckGhExecutable(Option.of(executable.strip()).filter(path -> !path.isEmpty())))
                    : Next.of(model);
            case SettingsPanelMsg.GhExecutableChecked(var executable, var version) -> version.fold(
                    failure -> Next.of(model.fail(new AppError.GhExecutableUnusable(executable, failure))),
                    works -> useGhExecutable(model, executable));
            case SettingsPanelMsg.AccountRequested() -> Next.of(model.popup(SettingsPanelPopup.class)
                    .flatMap(SettingsPanelPopup::gitHub)
                    .filter(gitHub -> gitHub.logins().isRight())
                    .map(gitHub -> model.openPopup(new GitHubAccountPopup(gitHub)))
                    .getOrElse(model));
            case SettingsPanelMsg.AccountCancelled() -> Next.of(model.withoutPopup(GitHubAccountPopup.class));
            case SettingsPanelMsg.AccountChosen(Option<String> user) -> model.popup(GitHubAccountPopup.class)
                    .map(popup -> popup.gitHub().user().equals(user)
                            ? Next.of(model.withoutPopup(GitHubAccountPopup.class))
                            : Next.of(model.withoutPopup(GitHubAccountPopup.class).clearError(),
                            new SettingsPanelCmd.SetAccount(user)))
                    .getOrElse(Next.of(model));
            // The app shows the repository again already, with a model of its own.
            case SettingsPanelMsg.AccountSet(), SettingsPanelMsg.Reopened() -> Next.of(model);
            case SettingsPanelMsg.AccountNotSet(var failure) -> Next.of(model.fail(new AppError.GitHubAccountNotSet(failure)));
            case SettingsPanelMsg.NotReopened(var failure) -> Next.of(model.fail(new AppError.SettingsNotApplied(failure)));
        };
    }

    /**
     * Runs the gh at {@code executable} from now on, keeping it for the next session.
     */
    private static Next useGhExecutable(Model model, Option<String> executable) {
        Settings chosen = model.settings().withGhExecutable(executable);
        return chosen.equals(model.settings())
                ? Next.of(model)
                : new Next(model.withSettings(chosen),
                List.of(new Cmd.SaveSettings(model.settings(), chosen), new SettingsPanelCmd.Reopen()));
    }
}
