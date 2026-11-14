package net.vanfleteren.jazygit.feature.settings;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.diff.DiffOptionsUpdate;
import net.vanfleteren.jazygit.feature.theme.ThemeScope;
import net.vanfleteren.jazygit.feature.theme.ThemeUpdate;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.CommandLog;
import net.vanfleteren.jazygit.state.preferences.Preferences;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.state.preferences.SettingsError;
import net.vanfleteren.jazygit.state.preferences.SettingsMsg;

/**
 * Looking at the settings and changing them: the theme, whether what moves moves smoothly, whether the repository is
 * read and the remotes fetched from every so often, the gh executable, the account gh and git use for the repository,
 * and the browser of that account. A change to how gh runs shows the repository again, as if the app
 * had been started with it. The settings file opens in git's editor, and is read again after it, and as the panel
 * opens (ADR 111).
 */
public final class SettingsPanelUpdate {

    private SettingsPanelUpdate() {}

    public static Next update(Model model, SettingsPanelMsg msg) {
        return switch (msg) {
            case SettingsPanelMsg.Requested() -> requested(model);
            case SettingsPanelMsg.GitHubRead(GitHubSetup gitHub) -> gitHubRead(model, gitHub);
            // Cancelling the theme goes back to the settings, on the setting it was asked for from.
            case SettingsPanelMsg.ThemeRequested() ->
                askTheme(model, ThemeScope.EVERYWHERE, SettingsPanelDialogs.THEME_KEY);
            case SettingsPanelMsg.RepositoryThemeRequested() ->
                askTheme(model, ThemeScope.REPOSITORY, SettingsPanelDialogs.REPOSITORY_THEME_KEY);
            case SettingsPanelMsg.AnimationsToggled() -> toggled(model, Settings::animations, Settings::withAnimations);
            case SettingsPanelMsg.AutoRefreshToggled() ->
                toggled(model, Settings::autoRefresh, Settings::withAutoRefresh);
            case SettingsPanelMsg.AutoFetchToggled() -> toggled(model, Settings::autoFetch, Settings::withAutoFetch);
            case SettingsPanelMsg.GhExecutableRequested() -> ghExecutableRequested(model);
            case SettingsPanelMsg.GhExecutableEntered() -> ghExecutableEntered(model);
            case SettingsPanelMsg.GhExecutableChecked(var executable, var version) ->
                ghExecutableChecked(model, executable, version);
            case SettingsPanelMsg.AccountRequested() -> accountRequested(model);
            case SettingsPanelMsg.BrowserRequested() -> browserRequested(model);
            case SettingsPanelMsg.BrowserEntered() -> browserEntered(model);
            case SettingsPanelMsg.AccountChosen(Option<String> user) -> accountChosen(model, user);
            // The app shows the repository again already, with a model of its own.
            case SettingsPanelMsg.AccountSet(), SettingsPanelMsg.Reopened() -> Next.of(model);
            case SettingsPanelMsg.AccountNotSet(var failure) ->
                Next.of(model.fail(new SettingsPanelError.GitHubAccountNotSet(failure)));
            case SettingsPanelMsg.NotReopened(var failure) ->
                Next.of(model.fail(new SettingsPanelError.SettingsNotApplied(failure)));
            case SettingsPanelMsg.FileRequested() -> Next.of(model.clearError(), new SettingsPanelCmd.EditFile());
            case SettingsPanelMsg.FileRead(var file, var loaded) -> fileRead(model, file, loaded);
            // The file is read again when the panel opens next.
            case SettingsPanelMsg.FileNotRead(var failure) -> Next.of(model);
            case SettingsPanelMsg.FileEdited(var commands, var loaded) ->
                read(CommandLog.append(model, SettingsPanelCmd.EditFile.EDIT_SETTINGS, commands), loaded, true);
            case SettingsPanelMsg.FileNotEdited(var failure) -> fileNotEdited(model, failure);
        };
    }

    /**
     * Opens the settings panel, reading what gh knows and the settings file for it. The key works everywhere, but
     * does not take away a dialog that is open.
     */
    private static Next requested(Model model) {
        if (model.popup().isDefined()) {
            return Next.of(model);
        }
        return Next.of(
                model.openPopup(new SettingsPanelPopup(
                        model.preferences().settings().theme(),
                        model.preferences().repositoryTheme(),
                        model.preferences().settings().ghExecutable(),
                        Option.none(),
                        model.preferences().settings().animations(),
                        model.preferences().settings().autoRefresh(),
                        model.preferences().settings().autoFetch(),
                        model.preferences().settings().browsers(),
                        Option.none())),
                new SettingsPanelCmd.ReadGitHub(model.preferences().settings().gh()),
                new SettingsPanelCmd.ReadFile());
    }

    /**
     * Shows what gh knows, read for a panel that may be closed already, or open on another gh executable.
     */
    private static Next gitHubRead(Model model, GitHubSetup gitHub) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .map(panel -> model.openPopup(panel.withGitHub(Option.of(gitHub))))
                .getOrElse(model));
    }

    /**
     * Turns the setting that {@code on} reads off if it is on, else on, from the open panel, keeping that for the next
     * session, with the panel still open to show it.
     */
    private static Next toggled(
            Model model, Function<Settings, Boolean> on, BiFunction<Settings, Boolean, Settings> with) {
        return model.popup(SettingsPanelPopup.class)
                .map(panel -> {
                    Settings kept = model.preferences().settings();
                    Settings chosen = with.apply(kept, !on.apply(kept));
                    return Next.of(
                            model.mapPreferences(preferences -> preferences.withSettings(chosen))
                                    .openPopup(shown(panel, chosen)),
                            new SaveSettings(kept, chosen));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code panel} showing the on or off settings of {@code settings}.
     */
    private static SettingsPanelPopup shown(SettingsPanelPopup panel, Settings settings) {
        return panel.withAnimations(settings.animations())
                .withAutoRefresh(settings.autoRefresh())
                .withAutoFetch(settings.autoFetch());
    }

    /**
     * Asks for the gh executable, starting from the one in use.
     */
    private static Next ghExecutableRequested(Model model) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .map(panel -> model.openPopup(
                        new GhExecutablePopup(panel.ghExecutable().getOrElse(""))))
                .getOrElse(model));
    }

    /**
     * Checks the gh executable entered, none when it is blank.
     */
    private static Next ghExecutableEntered(Model model) {
        return model.popup(GhExecutablePopup.class)
                .map(popup -> Option.of(popup.executable().strip()).filter(path -> !path.isEmpty()))
                .map(executable -> Next.of(
                        model.withoutPopup(GhExecutablePopup.class)
                                .mapJobs(jobs -> jobs.withGhExecutableEntered(executable)),
                        new SettingsPanelCmd.CheckGhExecutable(executable)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Uses the gh at {@code executable} when it runs, else says why not. Another gh executable was entered since:
     * the check of that one decides, whenever it finishes.
     */
    private static Next ghExecutableChecked(Model model, Option<String> executable, Either<Failure, String> version) {
        if (!model.jobs().ghExecutableEntered().equals(executable)) {
            return Next.of(model);
        }
        return version.fold(
                failure -> Next.of(model.fail(new SettingsPanelError.GhExecutableUnusable(executable, failure))),
                works -> useGhExecutable(model, executable));
    }

    /**
     * Asks which account gh and git use, when gh could tell which there are.
     */
    private static Next accountRequested(Model model) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .flatMap(SettingsPanelPopup::gitHub)
                .filter(gitHub -> gitHub.logins().isRight())
                .map(gitHub -> model.openPopup(new GitHubAccountPopup(gitHub)))
                .getOrElse(model));
    }

    /**
     * Asks for the browser of the account in use, starting from the one it has.
     */
    private static Next browserRequested(Model model) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .flatMap(panel -> panel.gitHub()
                        .flatMap(GitHubSetup::account)
                        .map(account -> model.openPopup(new AccountBrowserPopup(
                                account,
                                panel.browsers()
                                        .of(account.host(), account.user())
                                        .getOrElse("")))))
                .getOrElse(model));
    }

    /**
     * Uses the browser entered for its account.
     */
    private static Next browserEntered(Model model) {
        return model.popup(AccountBrowserPopup.class)
                .map(popup -> useBrowser(model.withoutPopup(AccountBrowserPopup.class), popup))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Has gh and git use the account of {@code user}, when it is not the one in use already.
     */
    private static Next accountChosen(Model model, Option<String> user) {
        return model.popup(GitHubAccountPopup.class)
                .map(popup -> popup.gitHub().user().equals(user)
                        ? Next.of(model.withoutPopup(GitHubAccountPopup.class))
                        : Next.of(
                                model.withoutPopup(GitHubAccountPopup.class).clearError(),
                                new SettingsPanelCmd.SetAccount(user, Landing.of(model.repoView()))))
                .getOrElse(Next.of(model));
    }

    /**
     * Applies the settings file read as the panel opened, which shows where it is.
     */
    private static Next fileRead(Model model, Option<String> file, SettingsStore.Loaded loaded) {
        return read(
                model.popup(SettingsPanelPopup.class)
                        .map(panel -> model.openPopup(panel.withFile(file)))
                        .getOrElse(model),
                loaded,
                false);
    }

    /**
     * Says the settings file could not be edited, logging the commands that ran.
     */
    private static Next fileNotEdited(Model model, Failure failure) {
        return Next.of(CommandLog.append(model, SettingsPanelCmd.EditFile.EDIT_SETTINGS, failure.commands())
                .fail(new AppError.OperationFailed(SettingsPanelCmd.EditFile.EDIT_SETTINGS, Option.none(), failure)));
    }

    private static Next askTheme(Model model, ThemeScope scope, char key) {
        return model.popup(SettingsPanelPopup.class)
                .map(panel -> ThemeUpdate.requestedFrom(model, scope, panel.withOpensAt(Option.of(key))))
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
                        .flatMap(error -> error instanceof SettingsError.NotReread(var problem)
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
                .map(panel -> updated.openPopup(shown(panel, settings)
                        .withTheme(settings.theme())
                        .withGhExecutable(settings.ghExecutable())
                        .withBrowsers(settings.browsers())))
                .getOrElse(updated);
        Option<SettingsProblem> report = edited
                ? loaded.problem()
                : loaded.problem().filter(problem -> !before.reported().contains(problem));
        Model reported = report.map(problem -> shown.fail(new SettingsError.NotReread(problem)))
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
                : Next.of(
                        model.mapPreferences(preferences -> preferences.withSettings(chosen)),
                        new SaveSettings(kept, chosen));
    }

    /**
     * Runs the gh at {@code executable} from now on, keeping it for the next session.
     */
    private static Next useGhExecutable(Model model, Option<String> executable) {
        Settings chosen = model.preferences().settings().withGhExecutable(executable);
        return chosen.equals(model.preferences().settings())
                ? Next.of(model)
                : Next.of(
                        model.mapPreferences(preferences -> preferences.withSettings(chosen)),
                        new SaveSettings(model.preferences().settings(), chosen),
                        new SettingsPanelCmd.Reopen(Landing.of(model.repoView())));
    }
}
