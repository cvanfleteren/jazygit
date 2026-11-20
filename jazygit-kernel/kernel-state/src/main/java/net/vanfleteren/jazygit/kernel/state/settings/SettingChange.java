package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.preferences.ShowAgain;

/**
 * The one way a setting is changed (ADR 163), whichever row or section of the settings panel changes it.
 */
public final class SettingChange {

    private SettingChange() {}

    public static Next update(Model model, SettingMsg msg) {
        return switch (msg) {
            case SettingMsg.Toggled(SettingKey<Boolean> key) ->
                set(model, key, !model.preferences().settings().get(key));
        };
    }

    /**
     * {@code model} with {@code value} for {@code key}, which an open settings panel shows, kept for the next session.
     * A setting that only applies to a repository shown anew shows the repository again, where it was. Nothing
     * changes when the setting has that value already.
     */
    public static <T> Next set(Model model, SettingKey<T> key, T value) {
        Settings kept = model.preferences().settings();
        Settings chosen = kept.with(key, value);
        if (chosen.equals(kept)) {
            return Next.of(model);
        }
        Model changed = model.mapPreferences(preferences -> preferences.withSettings(chosen));
        Model shown = changed.popup(SettingsPanelPopup.class)
                .map(panel -> changed.openPopup(panel.withSettings(chosen)))
                .getOrElse(changed);
        List<Cmd> cmds = List.<Cmd>of(new SaveSettings(kept, chosen))
                .appendAll(key.reopens() ? List.of(new ShowAgain(Landing.of(model.repoView()))) : List.empty());
        return new Next(shown, cmds);
    }
}
