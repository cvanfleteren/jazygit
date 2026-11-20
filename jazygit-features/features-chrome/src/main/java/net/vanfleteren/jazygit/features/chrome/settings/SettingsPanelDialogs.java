package net.vanfleteren.jazygit.features.chrome.settings;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.settings.ListDialogs;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsRows;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSections;
import net.vanfleteren.jazygit.kernel.ui.text.HomeDir;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.TextInputDialog;

/**
 * The settings panel: its own sections, how the UI looks and how git runs, then the sections the features add, then
 * the settings file (ADR 163).
 */
public final class SettingsPanelDialogs {

    /**
     * How the UI looks: the theme for all repositories and for this one, and the animations.
     */
    public static final SettingsSection APPEARANCE = SettingsSection.of(
            "chrome.dialog.settings.group.appearance",
            new Choice<SettingsPanelPopup>(
                            SettingsPanelDialogs.THEME_KEY,
                            popup -> Messages.get(
                                    "chrome.dialog.settings.theme",
                                    themeLabel(popup.settings().theme())),
                            SettingsPanelMsg.ThemeRequested::new)
                    .withDetails("chrome.dialog.settings.theme.details"),
            new Choice<SettingsPanelPopup>(
                            SettingsPanelDialogs.REPOSITORY_THEME_KEY,
                            SettingsPanelDialogs::repositoryTheme,
                            SettingsPanelMsg.RepositoryThemeRequested::new)
                    .withDetails("chrome.dialog.settings.repositoryTheme.details"),
            SettingsRows.toggle(
                    'm',
                    "chrome.dialog.settings.animations",
                    "chrome.dialog.settings.animations.details",
                    Settings.ANIMATIONS));
    /**
     * How git runs: reading the repository and fetching from the remotes every so often (ADR 125).
     */
    public static final SettingsSection GIT = SettingsSection.of(
            "chrome.dialog.settings.group.git",
            SettingsRows.toggle(
                    's',
                    "chrome.dialog.settings.autoRefresh",
                    "chrome.dialog.settings.autoRefresh.details",
                    Settings.AUTO_REFRESH),
            SettingsRows.toggle(
                    'f',
                    "chrome.dialog.settings.autoFetch",
                    "chrome.dialog.settings.autoFetch.details",
                    Settings.AUTO_FETCH));
    /**
     * The settings file, which opens in git's editor (ADR 111); last, as it holds every setting.
     */
    public static final SettingsSection SETTINGS_FILE = SettingsSection.of(
            "chrome.dialog.settings.group.file",
            new Choice<SettingsPanelPopup>('e', SettingsPanelDialogs::file, SettingsPanelMsg.FileRequested::new)
                    .withDetails("chrome.dialog.settings.file.details"));
    /**
     * The settings, one per line under the heading of their section, each changed by its key: the panel's own
     * sections, and those of the features of the app.
     */
    public static final ChoiceDialog.Spec<SettingsPanelPopup> PANEL = panel(SettingsSections.standard());

    // The keys of the settings that open the theme dialog, which the panel highlights again when it is cancelled.
    static final char THEME_KEY = 't';
    static final char REPOSITORY_THEME_KEY = 'r';
    // Wide enough for the usual gh executable and account, so it does not grow once they are read.
    private static final int WIDTH = 80;

    private SettingsPanelDialogs() {}

    /**
     * The sections of the panel, with {@code added}, those of the features, after the panel's own and before the
     * settings file.
     */
    static List<SettingsSection> sections(List<SettingsSection> added) {
        return List.of(APPEARANCE, GIT).appendAll(added).append(SETTINGS_FILE);
    }

    /**
     * The panel, with the sections of the features {@code added}.
     */
    public static ChoiceDialog.Spec<SettingsPanelPopup> panel(List<SettingsSection> added) {
        return new ChoiceDialog.Spec<>(
                        "settings",
                        SettingsPanelPopup.class,
                        popup -> Messages.get("chrome.dialog.settings.title"),
                        sections(added).flatMap(SettingsSection::shown),
                        WIDTH)
                .openingAt(SettingsPanelPopup::opensAt);
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}: the panel, and the dialogs that
     * edit a list of the settings any section has (ADR 164).
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningToFocus(new ChoiceDialog<>(PANEL, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(ListDialogs.LIST, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(ListDialogs.FORM, dispatch)),
                PopupSlot.returningToFocus(new TextInputDialog<>(ListDialogs.FIELD, dispatch)));
    }

    /**
     * The theme of the repository, as the settings show it: its own, else the one the user's git configuration gives
     * it and where, else none, as the theme for all repositories shows (ADR 70).
     */
    static String repositoryTheme(SettingsPanelPopup popup) {
        RepositoryTheme theme = popup.section(SettingsPanelUpdate.REPOSITORY_THEME);
        String shown = theme.own()
                .map(SettingsPanelDialogs::themeLabel)
                .orElse(() -> theme.inherited()
                        .map(inherited -> Messages.get(
                                "chrome.dialog.theme.inherited",
                                themeLabel(inherited.theme()),
                                HomeDir.shown(inherited.origin()))))
                .getOrElse(() -> Messages.get("chrome.dialog.settings.repositoryTheme.none"));
        return Messages.get("chrome.dialog.settings.repositoryTheme", shown);
    }

    /**
     * The settings file, as the settings show it: where it is, with {@code ~} for the home directory (ADR 111).
     */
    static String file(SettingsPanelPopup popup) {
        return Messages.get(
                "chrome.dialog.settings.file",
                popup.section(SettingsPanelUpdate.FILE).map(HomeDir::shown).getOrElse(SettingsRows::reading));
    }

    private static String themeLabel(ThemeName theme) {
        return Messages.get("chrome.dialog.theme." + theme.id());
    }
}
