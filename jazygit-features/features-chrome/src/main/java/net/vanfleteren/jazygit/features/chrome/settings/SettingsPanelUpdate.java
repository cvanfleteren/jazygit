package net.vanfleteren.jazygit.features.chrome.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.changes.diff.DiffOptionsUpdate;
import net.vanfleteren.jazygit.features.chrome.theme.ThemeScope;
import net.vanfleteren.jazygit.features.chrome.theme.ThemeUpdate;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsProblem;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.CommandLog;
import net.vanfleteren.jazygit.kernel.state.preferences.Preferences;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.kernel.state.preferences.SettingsError;
import net.vanfleteren.jazygit.kernel.state.preferences.SettingsMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.ShowAgain;
import net.vanfleteren.jazygit.kernel.state.settings.PanelReads;
import net.vanfleteren.jazygit.kernel.state.settings.SectionReads;
import net.vanfleteren.jazygit.kernel.state.settings.SectionState;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;

/**
 * Looking at the settings: opening the panel, which each of its sections reads for, choosing the theme from it, and
 * the settings file, which opens in git's editor, and is read again after it, and as the panel opens (ADR 111). A
 * setting is changed through {@code SettingChange.set}, by its row or by the section that owns it (ADR 163).
 */
public final class SettingsPanelUpdate {

    /**
     * The theme the git configuration gives the repository (ADR 70), as the panel read it when it opened.
     */
    public static final SectionState<RepositoryTheme> REPOSITORY_THEME =
            new SectionState<>("chrome:repositoryTheme", RepositoryTheme.NONE);
    /**
     * The settings file, which {@code e} opens; none while it is read, or when the settings are kept in no file
     * (ADR 111).
     */
    public static final SectionState<Option<String>> FILE = new SectionState<>("chrome:file", Option.none());

    private SettingsPanelUpdate() {}

    public static Next update(Model model, SettingsPanelMsg msg) {
        return switch (msg) {
            case SettingsPanelMsg.Requested() -> requested(model, PanelReads.standard());
            // Cancelling the theme goes back to the settings, on the setting it was asked for from.
            case SettingsPanelMsg.ThemeRequested() ->
                askTheme(model, ThemeScope.EVERYWHERE, SettingsPanelDialogs.THEME_KEY);
            case SettingsPanelMsg.RepositoryThemeRequested() ->
                askTheme(model, ThemeScope.REPOSITORY, SettingsPanelDialogs.REPOSITORY_THEME_KEY);
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
     * Opens the settings panel, with what the sections {@code added} by the features read for it, and then the
     * settings file. The key works everywhere, but does not take away a dialog that is open.
     */
    static Next requested(Model model, List<SectionReads> added) {
        if (model.popup().isDefined()) {
            return Next.of(model);
        }
        List<Cmd> reads = added.flatMap(section -> section.on(model)).append(new SettingsPanelCmd.ReadFile());
        return new Next(
                model.openPopup(new SettingsPanelPopup(model.preferences().settings())
                        .with(REPOSITORY_THEME, model.preferences().repositoryTheme())),
                reads);
    }

    /**
     * Applies the settings file read as the panel opened, which shows where it is.
     */
    private static Next fileRead(Model model, Option<String> file, SettingsStore.Loaded loaded) {
        return read(
                model.popup(SettingsPanelPopup.class)
                        .map(panel -> model.openPopup(panel.with(FILE, file)))
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
     * that changed a setting that only applies to a repository shown anew shows the repository again at once
     * (ADR 111).
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
     * the settings as applied. A changed setting that only applies to a repository shown anew, as the gh executable,
     * shows the repository again.
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
                .map(panel -> updated.openPopup(panel.withSettings(settings)))
                .getOrElse(updated);
        Option<SettingsProblem> report = edited
                ? loaded.problem()
                : loaded.problem().filter(problem -> !before.reported().contains(problem));
        Model reported = report.map(problem -> shown.fail(new SettingsError.NotReread(problem)))
                .getOrElse(shown);
        if (loaded.keys().all().exists(key -> key.reopens() && key.changed(settings, before.settings()))) {
            // Shown again, the diffs are read with the settings as they are now, too.
            return Next.of(reported, new ShowAgain(Landing.of(reported.repoView())));
        }
        return settings.diffStyle().rich() == before.settings().diffStyle().rich()
                ? Next.of(reported)
                : DiffOptionsUpdate.changed(reported);
    }
}
