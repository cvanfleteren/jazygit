package net.vanfleteren.jazygit.features.github.settings;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.settings.SectionState;
import net.vanfleteren.jazygit.kernel.state.settings.SettingChange;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;

/**
 * Changing how gh runs from the settings panel: the gh executable, the account gh and git use for the repository, and
 * the browser of that account. A change to how gh runs shows the repository again, as if the app had been started
 * with it.
 */
public final class GitHubSettingsUpdate {

    /**
     * How gh is set up for the repository, as the panel read it when it opened; none while it is read.
     */
    public static final SectionState<Option<GitHubSetup>> SETUP = new SectionState<>("github:setup", Option.none());

    private GitHubSettingsUpdate() {}

    public static Next update(Model model, GitHubSettingsMsg msg) {
        return switch (msg) {
            case GitHubSettingsMsg.GitHubRead(GitHubSetup gitHub) -> gitHubRead(model, gitHub);
            case GitHubSettingsMsg.GhExecutableRequested() -> ghExecutableRequested(model);
            case GitHubSettingsMsg.GhExecutableEntered() -> ghExecutableEntered(model);
            case GitHubSettingsMsg.GhExecutableChecked(var executable, var version) ->
                ghExecutableChecked(model, executable, version);
            case GitHubSettingsMsg.AccountRequested() -> accountRequested(model);
            case GitHubSettingsMsg.BrowserRequested() -> browserRequested(model);
            case GitHubSettingsMsg.BrowserEntered() -> browserEntered(model);
            case GitHubSettingsMsg.AccountChosen(Option<String> user) -> accountChosen(model, user);
            // The app shows the repository again already, with a model of its own.
            case GitHubSettingsMsg.AccountSet() -> Next.of(model);
            case GitHubSettingsMsg.AccountNotSet(var failure) ->
                Next.of(model.fail(new GitHubSettingsError.AccountNotSet(failure)));
        };
    }

    /**
     * What the section reads as the settings panel opens: how the gh in use is set up for the repository.
     */
    public static List<Cmd> opened(Model model) {
        return List.of(new GitHubSettingsCmd.ReadGitHub(
                GitHubSettings.gh(model.preferences().settings())));
    }

    /**
     * Shows what gh knows, read for a panel that may be closed already, or open on another gh executable.
     */
    private static Next gitHubRead(Model model, GitHubSetup gitHub) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .map(panel -> model.openPopup(panel.with(SETUP, Option.of(gitHub))))
                .getOrElse(model));
    }

    /**
     * Asks for the gh executable, starting from the one in use.
     */
    private static Next ghExecutableRequested(Model model) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .map(panel -> model.openPopup(new GhExecutablePopup(
                        panel.settings().get(GitHubSettings.GH_EXECUTABLE).getOrElse(""))))
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
                                .mapPreferences(preferences -> preferences.withGhExecutableEntered(executable)),
                        new GitHubSettingsCmd.CheckGhExecutable(executable)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Uses the gh at {@code executable} when it runs, which shows the repository again, else says why not. Another gh
     * executable was entered since: the check of that one decides, whenever it finishes.
     */
    private static Next ghExecutableChecked(Model model, Option<String> executable, Either<Failure, String> version) {
        if (!model.preferences().ghExecutableEntered().equals(executable)) {
            return Next.of(model);
        }
        return version.fold(
                failure -> Next.of(model.fail(new GitHubSettingsError.GhExecutableUnusable(executable, failure))),
                works -> SettingChange.set(model, GitHubSettings.GH_EXECUTABLE, executable));
    }

    /**
     * Asks which account gh and git use, when gh could tell which there are.
     */
    private static Next accountRequested(Model model) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .flatMap(panel -> panel.section(SETUP))
                .filter(gitHub -> gitHub.logins().isRight())
                .map(gitHub -> model.openPopup(new GitHubAccountPopup(gitHub)))
                .getOrElse(model));
    }

    /**
     * Asks for the browser of the account in use, starting from the one it has.
     */
    private static Next browserRequested(Model model) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .flatMap(panel -> panel.section(SETUP)
                        .flatMap(GitHubSetup::account)
                        .map(account -> model.openPopup(new AccountBrowserPopup(
                                account,
                                panel.settings()
                                        .get(GitHubSettings.BROWSERS)
                                        .of(account.host(), account.user())
                                        .getOrElse("")))))
                .getOrElse(model));
    }

    /**
     * Opens the links of the account of the popup in the browser typed in it from now on, none when it is blank,
     * keeping that for the next session (ADR 88).
     */
    private static Next browserEntered(Model model) {
        return model.popup(AccountBrowserPopup.class)
                .map(popup -> SettingChange.set(
                        model.withoutPopup(AccountBrowserPopup.class),
                        GitHubSettings.BROWSERS,
                        model.preferences()
                                .settings()
                                .get(GitHubSettings.BROWSERS)
                                .with(
                                        popup.account().host(),
                                        popup.account().user(),
                                        Option.of(popup.browser().strip()).filter(browser -> !browser.isEmpty()))))
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
                                new GitHubSettingsCmd.SetAccount(user, Landing.of(model.repoView()))))
                .getOrElse(Next.of(model));
    }
}
