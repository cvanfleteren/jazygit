package net.vanfleteren.jazygit.state.preferences;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.settings.SettingsStore;
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

    /**
     * {@code next} was kept over {@code previous}: the settings that differ between them are in the file now.
     */
    record Saved(Settings previous, Settings next) implements SettingsMsg {}

    record NotSaved(Failure failure) implements SettingsMsg {}

    /**
     * A repository is shown in place of another, with what its session knew of the settings file: what the file had
     * when last read or written, what was reported wrong in it, and the problem in it that session was showing, which
     * goes on showing, as a read that changed the gh executable shows the repository again at once (ADR 111).
     */
    record Carried(SettingsStore.Loaded file, Option<SettingsProblem> reported, Option<SettingsProblem> shown)
            implements SettingsMsg {}
}
