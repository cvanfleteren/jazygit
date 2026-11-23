package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the entries of the reflog of HEAD after the newest {@code skip}, at most {@code max}, in one process
 * (ADR 186).
 */
public record ReadReflog(int skip, int max) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("changes.work.reflog"));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .reflog(skip, max)
                .mapLeft(Failure::of)
                .fold(this::failed, reflog -> new ReflogMsg.Read(this, reflog));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ReflogMsg.Failed(this, failure);
    }
}
