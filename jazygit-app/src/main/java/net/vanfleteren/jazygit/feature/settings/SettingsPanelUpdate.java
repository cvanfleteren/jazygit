package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.theme.ThemeMsg;
import net.vanfleteren.jazygit.feature.theme.ThemeScope;
import net.vanfleteren.jazygit.feature.theme.ThemeUpdate;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.SaveSettings;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Looking at the settings and changing them: the theme, whether what moves moves smoothly, the gh executable, the
 * account gh and git use for the repository, and the browser of that account. A change to how gh runs shows the repository again, as if the app
 * had been started with it.
 */
public final class SettingsPanelUpdate {

    private SettingsPanelUpdate() {}

    public static Next update(Model model, SettingsPanelMsg msg) {
        return switch (msg) {
            // The key works everywhere, but does not take away a dialog that is open.
            case SettingsPanelMsg.Requested() when model.popup().isDefined() -> Next.of(model);
            case SettingsPanelMsg.Requested() ->
                Next.of(
                        model.openPopup(new SettingsPanelPopup(
                                model.preferences().settings().theme(),
                                model.preferences().repositoryTheme(),
                                model.preferences().settings().ghExecutable(),
                                Option.none(),
                                model.preferences().settings().animations(),
                                model.preferences().settings().browsers())),
                        new SettingsPanelCmd.ReadGitHub(
                                model.preferences().settings().gh()));
            // Read for a panel that may be closed already, or open on another gh executable.
            case SettingsPanelMsg.GitHubRead(GitHubSetup gitHub) ->
                Next.of(model.popup(SettingsPanelPopup.class)
                        .map(panel -> model.openPopup(panel.withGitHub(Option.of(gitHub))))
                        .getOrElse(model));
            case SettingsPanelMsg.ThemeRequested() ->
                ThemeUpdate.update(
                        model.withoutPopup(SettingsPanelPopup.class), new ThemeMsg.Requested(ThemeScope.EVERYWHERE));
            case SettingsPanelMsg.RepositoryThemeRequested() ->
                ThemeUpdate.update(
                        model.withoutPopup(SettingsPanelPopup.class), new ThemeMsg.Requested(ThemeScope.REPOSITORY));
            case SettingsPanelMsg.AnimationsToggled() ->
                model.popup(SettingsPanelPopup.class)
                        .map(panel -> toggleAnimations(model, panel))
                        .getOrElse(() -> Next.of(model));
            case SettingsPanelMsg.GhExecutableRequested() ->
                Next.of(model.popup(SettingsPanelPopup.class)
                        .map(panel -> model.openPopup(
                                new GhExecutablePopup(panel.ghExecutable().getOrElse(""))))
                        .getOrElse(model));
            case SettingsPanelMsg.GhExecutableEntered() ->
                model.popup(GhExecutablePopup.class)
                        .map(popup -> Option.of(popup.executable().strip()).filter(path -> !path.isEmpty()))
                        .map(executable -> Next.of(
                                model.withoutPopup(GhExecutablePopup.class)
                                        .mapJobs(jobs -> jobs.withGhExecutableEntered(executable)),
                                new SettingsPanelCmd.CheckGhExecutable(executable)))
                        .getOrElse(() -> Next.of(model));
            // Another gh executable was entered since: the check of that one decides, whenever it finishes.
            case SettingsPanelMsg.GhExecutableChecked(var executable, var version)
            when !model.jobs().ghExecutableEntered().equals(executable) -> Next.of(model);
            case SettingsPanelMsg.GhExecutableChecked(var executable, var version) ->
                version.fold(
                        failure -> Next.of(model.fail(new AppError.GhExecutableUnusable(executable, failure))),
                        works -> useGhExecutable(model, executable));
            case SettingsPanelMsg.AccountRequested() ->
                Next.of(model.popup(SettingsPanelPopup.class)
                        .flatMap(SettingsPanelPopup::gitHub)
                        .filter(gitHub -> gitHub.logins().isRight())
                        .map(gitHub -> model.openPopup(new GitHubAccountPopup(gitHub)))
                        .getOrElse(model));
            case SettingsPanelMsg.BrowserRequested() ->
                Next.of(model.popup(SettingsPanelPopup.class)
                        .flatMap(panel -> panel.gitHub()
                                .flatMap(GitHubSetup::account)
                                .map(account -> model.openPopup(new AccountBrowserPopup(
                                        account,
                                        panel.browsers()
                                                .of(account.host(), account.user())
                                                .getOrElse("")))))
                        .getOrElse(model));
            case SettingsPanelMsg.BrowserEntered() ->
                model.popup(AccountBrowserPopup.class)
                        .map(popup -> useBrowser(model.withoutPopup(AccountBrowserPopup.class), popup))
                        .getOrElse(() -> Next.of(model));
            case SettingsPanelMsg.AccountChosen(Option<String> user) ->
                model.popup(GitHubAccountPopup.class)
                        .map(popup -> popup.gitHub().user().equals(user)
                                ? Next.of(model.withoutPopup(GitHubAccountPopup.class))
                                : Next.of(
                                        model.withoutPopup(GitHubAccountPopup.class)
                                                .clearError(),
                                        new SettingsPanelCmd.SetAccount(user, Landing.of(model.repoView()))))
                        .getOrElse(Next.of(model));
            // The app shows the repository again already, with a model of its own.
            case SettingsPanelMsg.AccountSet(), SettingsPanelMsg.Reopened() -> Next.of(model);
            case SettingsPanelMsg.AccountNotSet(var failure) ->
                Next.of(model.fail(new AppError.GitHubAccountNotSet(failure)));
            case SettingsPanelMsg.NotReopened(var failure) ->
                Next.of(model.fail(new AppError.SettingsNotApplied(failure)));
        };
    }

    /**
     * Turns the animations off if they are on, else on, keeping that for the next session, with the {@code panel}
     * still open to show it.
     */
    private static Next toggleAnimations(Model model, SettingsPanelPopup panel) {
        Settings kept = model.preferences().settings();
        Settings chosen = kept.withAnimations(!kept.animations());
        return new Next(
                model.mapPreferences(preferences -> preferences.withSettings(chosen))
                        .openPopup(panel.withAnimations(chosen.animations())),
                List.of(new SaveSettings(kept, chosen)));
    }

    /**
     * Opens the links of the account of {@code popup} in the browser typed in it from now on, none when it is blank,
     * keeping that for the next session (ADR 88).
     */
    private static Next useBrowser(Model model, AccountBrowserPopup popup) {
        Settings kept = model.preferences().settings();
        Settings chosen = kept.withBrowsers(kept.browsers()
                .with(
                        popup.account().host(),
                        popup.account().user(),
                        Option.of(popup.browser().strip()).filter(browser -> !browser.isEmpty())));
        return chosen.equals(kept)
                ? Next.of(model)
                : new Next(
                        model.mapPreferences(preferences -> preferences.withSettings(chosen)),
                        List.of(new SaveSettings(kept, chosen)));
    }

    /**
     * Runs the gh at {@code executable} from now on, keeping it for the next session.
     */
    private static Next useGhExecutable(Model model, Option<String> executable) {
        Settings chosen = model.preferences().settings().withGhExecutable(executable);
        return chosen.equals(model.preferences().settings())
                ? Next.of(model)
                : new Next(
                        model.mapPreferences(preferences -> preferences.withSettings(chosen)),
                        List.of(
                                new SaveSettings(model.preferences().settings(), chosen),
                                new SettingsPanelCmd.Reopen(Landing.of(model.repoView()))));
    }
}
