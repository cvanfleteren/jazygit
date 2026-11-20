package net.vanfleteren.jazygit.kernel.ui.settings;

import net.vanfleteren.jazygit.kernel.settings.SettingKey;
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
