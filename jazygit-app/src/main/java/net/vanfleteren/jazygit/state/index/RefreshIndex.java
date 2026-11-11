package net.vanfleteren.jazygit.state.index;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Writes into the index what the files in the working tree are like now, where it no longer says so (ADR 93, ADR 97):
 * the status that every tick reads takes no optional locks, so it never does, and compares the contents of those
 * files again every time. That it could not, as another git command held the index, only goes to the debug log:
 * the next change of the index, or the next one anyway, tries again.
 */
public record RefreshIndex() implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.status"));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .status()
                .refreshIndex()
                .mapLeft(Failure::of)
                .fold(this::failed, done -> new IndexMsg.Refreshed(true));
    }

    @Override
    public Msg failed(Failure failure) {
        LoggerFactory.getLogger(RefreshIndex.class).debug("The index was not refreshed: {}", failure);
        return new IndexMsg.Refreshed(false);
    }
}
