package net.vanfleteren.jazygit.kernel.state.preferences;

import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Keeps {@code next}, which the user changed from {@code previous}, for the next session.
 */
public record SaveSettings(Settings previous, Settings next) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.settings()
                .save(previous, next)
                .fold(problem -> failed(new Failure.Settings(problem)), saved -> new SettingsMsg.Saved(previous, next));
    }

    @Override
    public Msg failed(Failure failure) {
        return new SettingsMsg.NotSaved(failure);
    }
}
