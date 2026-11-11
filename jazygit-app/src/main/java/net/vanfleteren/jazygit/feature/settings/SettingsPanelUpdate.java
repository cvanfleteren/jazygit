package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.DiffOptionsUpdate;
import net.vanfleteren.jazygit.feature.theme.ThemeScope;
import net.vanfleteren.jazygit.feature.theme.ThemeUpdate;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.CommandLog;
import net.vanfleteren.jazygit.state.preferences.Preferences;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.state.preferences.SettingsMsg;

/**
 * Looking at the settings and changing them: the theme, whether what moves moves smoothly, the gh executable, the
 * account gh and git use for the repository, and the browser of that account. A change to how gh runs shows the repository again, as if the app
 * had been started with it. The settings file opens in git's editor, and is read again after it, and as the panel
 * opens (ADR 111).
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
                                model.preferences().settings().browsers(),
                                Option.none())),
                        new SettingsPanelCmd.ReadGitHub(
                                model.preferences().settings().gh()),
                        new SettingsPanelCmd.ReadFile());
            // Read for a panel that may be closed already, or open on another gh executable.
            case SettingsPanelMsg.GitHubRead(GitHubSetup gitHub) ->
                Next.of(model.popup(SettingsPanelPopup.class)
                        .map(panel -> model.openPopup(panel.withGitHub(Option.of(gitHub))))
                        .getOrElse(model));
            // Cancelling the theme goes back to the settings.
            case SettingsPanelMsg.ThemeRequested() -> askTheme(model, ThemeScope.EVERYWHERE);
            case SettingsPanelMsg.RepositoryThemeRequested() -> askTheme(model, ThemeScope.REPOSITORY);
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
            case SettingsPanelMsg.FileRequested() -> Next.of(model.clearError(), new SettingsPanelCmd.EditFile());
            case SettingsPanelMsg.FileRead(var file, var loaded) ->
                read(
                        model.popup(SettingsPanelPopup.class)
                                .map(panel -> model.openPopup(panel.withFile(file)))
                                .getOrElse(model),
                        loaded,
                        false);
            // The file is read again when the panel opens next.
            case SettingsPanelMsg.FileNotRead(var failure) -> Next.of(model);
            case SettingsPanelMsg.FileEdited(var commands, var loaded) ->
                read(CommandLog.append(model, SettingsPanelCmd.EditFile.EDIT_SETTINGS, commands), loaded, true);
            case SettingsPanelMsg.FileNotEdited(var failure) ->
                Next.of(CommandLog.append(model, SettingsPanelCmd.EditFile.EDIT_SETTINGS, failure.commands())
                        .fail(new AppError.OperationFailed(
                                SettingsPanelCmd.EditFile.EDIT_SETTINGS, Option.none(), failure)));
        };
    }

    private static Next askTheme(Model model, ThemeScope scope) {
        return model.popup(SettingsPanelPopup.class)
                .map(panel -> ThemeUpdate.requestedFrom(model, scope, panel))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * What the session of {@code last} knew of the settings file, for the repository shown in its place: what the file
     * had, what was reported, and the problem in it the session was showing, which goes on showing there, as a read
     * that changed the gh executable shows the repository again at once (ADR 111).
     */
    public static SettingsMsg.Carried carried(Model last) {
        return new SettingsMsg.Carried(
                last.preferences().file(),
                last.preferences().reported(),
                last.outcome()
                        .error()
                        .flatMap(error -> error instanceof AppError.SettingsNotReread(var problem)
                                ? Option.of(problem)
                                : Option.none()));
    }

    /**
     * Applies the settings file, read again as {@code loaded}, after the editor when {@code edited}, else as the panel
     * opened (ADR 111). Only what changed in the file since it was last read or written, and is valid, is applied;
     * a setting only the session has, or a value that is not valid, leaves the one in use. What is wrong in the file
     * is reported after the editor always, else only when it differs from what was reported last. An open panel shows
     * the settings as applied.
     */
    static Next read(Model model, SettingsStore.Loaded loaded, boolean edited) {
        Preferences before = model.preferences();
        SettingsStore.Applied applied = loaded.appliedTo(before.settings(), before.file());
        Settings settings = applied.settings();
        Model updated = model.mapPreferences(preferences -> preferences
                .withSettings(settings)
                .withFile(loaded)
                .withReported(loaded.problem())
                .withThemeOverride(applied.themeChanged() ? Option.none() : preferences.themeOverride()));
        Model shown = updated.popup(SettingsPanelPopup.class)
                .map(panel -> updated.openPopup(panel.withTheme(settings.theme())
                        .withGhExecutable(settings.ghExecutable())
                        .withAnimations(settings.animations())
                        .withBrowsers(settings.browsers())))
                .getOrElse(updated);
        Option<SettingsProblem> report = edited
                ? loaded.problem()
                : loaded.problem().filter(problem -> !before.reported().contains(problem));
        Model reported = report.map(problem -> shown.fail(new AppError.SettingsNotReread(problem)))
                .getOrElse(shown);
        if (!settings.ghExecutable().equals(before.settings().ghExecutable())) {
            // Shown again, the diffs are read with the settings as they are now, too.
            return Next.of(reported, new SettingsPanelCmd.Reopen(Landing.of(reported.repoView())));
        }
        return settings.diffStyle() == before.settings().diffStyle()
                ? Next.of(reported)
                : DiffOptionsUpdate.changed(reported);
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
