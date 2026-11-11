package net.vanfleteren.jazygit.state.preferences;

import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Keeps {@code next}, which the user changed from {@code previous}, for the next session.
 */
public record SaveSettings(Settings previous, Settings next) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.settings()
                .save(previous, next)
                .fold(problem -> failed(new Failure.Settings(problem)), saved -> new SettingsMsg.Saved());
    }

    @Override
    public Msg failed(Failure failure) {
        return new SettingsMsg.NotSaved(failure);
    }
}
