package net.vanfleteren.jazygit.kernel.state.settings;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * The entries of a list of the settings, while the user adds, edits, deletes and moves them (ADR 164). The entries
 * themselves are in the settings, which each change saves.
 *
 * @param list  the list
 * @param panel the settings panel it was opened from, which closing it goes back to
 */
public record ListPopup(ListSetting<?> list, SettingsPanelPopup panel) implements Popup {

    /**
     * This list, going back to {@code panel}.
     */
    public ListPopup withPanel(SettingsPanelPopup panel) {
        return new ListPopup(list, panel);
    }
}
