package net.vanfleteren.jazygit.features.changes.commit;

import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the commits HEAD has and none of the shared tips has, in one {@code git rev-list}, as many as the log reads
 * at most (#722).
 */
public record ReadUnpushed(Unpushed.Tips tips) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .onlyOn(tips.head(), tips.shared(), History.MAX_COMMITS)
                .mapLeft(Failure::of)
                .fold(this::failed, commits -> new UnpushedMsg.Read(tips, commits));
    }

    @Override
    public Msg failed(Failure failure) {
        return new UnpushedMsg.Failed(tips, failure);
    }
}
