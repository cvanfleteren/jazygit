package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Looks at the stamp of what the reflog of HEAD is read from, without running git (ADR 186): it changes with every move
 * of HEAD, also one made outside the app that keeps HEAD's commit, which the log is not read again for.
 */
public record StampReflog() implements Cmd {

    @Override
    public Msg run(Env env) {
        return new ReflogMsg.Stamped(env.git().status().reflogStamp());
    }

    /**
     * Not looked at, it is not known to be the same: the reflog is read again.
     */
    @Override
    public Msg failed(Failure failure) {
        return new ReflogMsg.Stamped(Option.none());
    }
}
