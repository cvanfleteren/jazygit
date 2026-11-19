package net.vanfleteren.jazygit.kernel.state.preferences;

import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Keeping the settings: a setting that could not be kept still holds for this session.
 */
public final class SettingsUpdate {

    private SettingsUpdate() {}

    public static Next update(Model model, SettingsMsg msg) {
        return switch (msg) {
            case SettingsMsg.Saved(Settings previous, Settings next) ->
                Next.of(model.mapPreferences(
                        preferences -> preferences.withFile(preferences.file().saved(previous, next))));
            case SettingsMsg.NotSaved(Failure failure) -> Next.of(model.fail(new SettingsError.NotSaved(failure)));
            case SettingsMsg.Carried(var file, var reported, var shown) -> {
                Model carried = model.mapPreferences(
                        preferences -> preferences.withFile(file).withReported(reported));
                yield Next.of(shown.map(problem -> carried.fail(new SettingsError.NotReread(problem)))
                        .getOrElse(carried));
            }
        };
    }
}
