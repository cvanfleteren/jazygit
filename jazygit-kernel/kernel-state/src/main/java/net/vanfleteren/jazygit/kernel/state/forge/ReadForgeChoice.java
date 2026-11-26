package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads what the user chose before as the home of the repository (ADR 177), from the repository's git configuration:
 * {@value #KEY} is the remote, or empty for no forge. One that cannot be read is taken as nothing chosen.
 */
public record ReadForgeChoice() implements Cmd {

    /**
     * Where the choice is kept, in the configuration of the repository itself.
     */
    public static final String KEY = "jazygit.forgeHome";

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return new ForgeMsg.Remembered(env.git()
                .config()
                .local(KEY)
                .getOrElse(Option.none())
                .<ForgeChoice>map(value -> value.isBlank()
                        ? new ForgeChoice.NoForge()
                        : new ForgeChoice.Remote(new RemoteName(value.strip())))
                .getOrElse(ForgeChoice.Unchosen::new));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ForgeMsg.Remembered(new ForgeChoice.Unchosen());
    }
}
