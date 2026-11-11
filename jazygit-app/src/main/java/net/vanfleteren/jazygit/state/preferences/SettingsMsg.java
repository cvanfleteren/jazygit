package net.vanfleteren.jazygit.state.preferences;

import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * The outcome of keeping the settings.
 */
public sealed interface SettingsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SettingsUpdate.update(model, this);
    }

    record Saved() implements SettingsMsg {}

    record NotSaved(Failure failure) implements SettingsMsg {}
}
