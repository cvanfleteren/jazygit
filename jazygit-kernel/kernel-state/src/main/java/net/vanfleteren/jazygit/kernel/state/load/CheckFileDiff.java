package net.vanfleteren.jazygit.kernel.state.load;

import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Looks at the files the diff {@code load} is read from, so that it is read again only once they changed: that
 * takes a git process or more, this none (ADR 58).
 */
public record CheckFileDiff(LoadFileDiff load) implements Load {

    @Override
    public Msg run(Env env) {
        return new LoadMsg.FileDiffChecked(load, env.git().status().diffStamp(load.files()));
    }
}
