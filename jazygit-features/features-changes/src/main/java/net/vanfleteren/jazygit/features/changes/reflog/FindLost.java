package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Finds which of {@code commits}, those of the entries the read {@code read} read, no branch, tag or remote reaches,
 * in one process, once their rows are drawn (ADR 186). It runs in the background, as the list does not wait for it.
 *
 * @param read    the read it follows, counted from the first
 * @param all     whether that read read all the entries there are, from the top
 * @param commits the commits of the entries it read
 */
public record FindLost(int read, boolean all, List<Sha> commits) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .lost(commits)
                .mapLeft(Failure::of)
                .fold(this::failed, lost -> new ReflogMsg.LostFound(this, lost));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ReflogMsg.LostUnknown(this, failure);
    }
}
