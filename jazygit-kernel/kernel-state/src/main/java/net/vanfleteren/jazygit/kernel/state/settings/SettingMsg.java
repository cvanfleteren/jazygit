package net.vanfleteren.jazygit.kernel.state.settings;

import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Changing a setting from a row of the settings panel that needs no code of its own (ADR 163).
 */
public sealed interface SettingMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SettingChange.update(model, this);
    }

    /**
     * The user turned the setting of {@code key} off if it was on, else on.
     */
    record Toggled(SettingKey<Boolean> key) implements SettingMsg {}
}
