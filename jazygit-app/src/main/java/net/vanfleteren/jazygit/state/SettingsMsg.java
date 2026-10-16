package net.vanfleteren.jazygit.state;

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
