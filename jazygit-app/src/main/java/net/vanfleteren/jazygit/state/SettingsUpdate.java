package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Keeping the settings: a setting that could not be kept still holds for this session.
 */
public final class SettingsUpdate {

    private SettingsUpdate() {}

    public static Next update(Model model, SettingsMsg msg) {
        return switch (msg) {
            case SettingsMsg.Saved() -> Next.of(model);
            case SettingsMsg.NotSaved(Failure failure) -> Next.of(model.fail(new AppError.SettingsNotSaved(failure)));
        };
    }
}
