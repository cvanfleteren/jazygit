package net.vanfleteren.jazygit.kernel.state.preferences;

import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Shows the repository again, so a setting that only applies to a repository shown anew, as the gh executable,
 * holds now; it is shown where {@code landing} says, where it was.
 */
public record ShowAgain(Landing landing) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.repositories()
                .switchTo(env.git().worktree(), landing)
                .mapLeft(Failure::of)
                .fold(this::failed, reopened -> new SettingsMsg.ShownAgain());
    }

    @Override
    public Msg failed(Failure failure) {
        return new SettingsMsg.NotShownAgain(failure);
    }
}
