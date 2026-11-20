package net.vanfleteren.jazygit.kernel.ui.settings;

import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.state.settings.ListMsg;
import net.vanfleteren.jazygit.kernel.state.settings.ListSetting;
import net.vanfleteren.jazygit.kernel.state.settings.SettingMsg;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * The rows of the settings panel that need no code of their own (ADR 163): choosing one changes its setting through
 * {@code SettingChange.set}, so a feature declares the row and nothing else.
 */
public final class SettingsRows {

    private SettingsRows() {}

    /**
     * A setting that is on or off, which its key turns the other way: its label, with the message key
     * {@code labelKey}, shows which, and what it does, with the message key {@code detailsKey}, shows while it is
     * highlighted.
     */
    public static Choice<SettingsPanelPopup> toggle(
            char key, String labelKey, String detailsKey, SettingKey<Boolean> setting) {
        return new Choice<SettingsPanelPopup>(
                        key,
                        popup -> Messages.get(labelKey, onOff(popup.settings().get(setting))),
                        () -> new SettingMsg.Toggled(setting))
                .withDetails(detailsKey);
    }

    /**
     * A list of the settings that the panel edits (ADR 164): its label, with the message key {@code labelKey}, shows
     * the names of its entries, and choosing it opens the dialog of its entries. What saving it does to the file shows
     * while it is highlighted.
     */
    public static <E> Choice<SettingsPanelPopup> list(char key, String labelKey, ListSetting<E> list) {
        return new Choice<SettingsPanelPopup>(
                        key,
                        popup -> Messages.get(labelKey, names(popup.settings().get(list.key()), list)),
                        () -> new ListMsg.Opened(list, key))
                .withDetails(popup ->
                        Messages.get("settingsPanel.list.details", list.key().path()));
    }

    /**
     * The names of {@code entries}, as a row of the panel shows them; what is wrong in an entry the app cannot read
     * instead of its name.
     */
    static <E> String names(Entries<E> entries, ListSetting<E> list) {
        return entries.all().isEmpty()
                ? Messages.get("settingsPanel.list.none")
                : entries.all()
                        .map(entry -> entry.value()
                                .map(list.name())
                                .getOrElse(() -> ListDialogs.WARNING + Messages.get("settingsPanel.list.invalid")))
                        .mkString(", ");
    }

    /**
     * What a setting that is on or off shows: on or off.
     */
    public static String onOff(boolean on) {
        return Messages.get(on ? "settingsPanel.on" : "settingsPanel.off");
    }

    /**
     * What a row shows for what its section is still reading as the panel opens.
     */
    public static String reading() {
        return Messages.get("settingsPanel.reading");
    }
}
