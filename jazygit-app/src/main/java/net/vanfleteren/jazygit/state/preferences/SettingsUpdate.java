package net.vanfleteren.jazygit.state.preferences;

import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

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
