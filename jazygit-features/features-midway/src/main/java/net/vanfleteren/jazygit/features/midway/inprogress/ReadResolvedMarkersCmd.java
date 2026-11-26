package net.vanfleteren.jazygit.features.midway.inprogress;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the files resolved since the operation stopped, {@code resolved}, for conflict markers left in them, before
 * asking what to do with it (ADR 198).
 */
public record ReadResolvedMarkersCmd(List<RepoPath> resolved) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .index()
                .markers(resolved)
                .fold(error -> failed(Failure.of(error)), InProgressMsg.MarkersRead::new);
    }

    /**
     * Files that cannot be read have nothing to warn of: it is asked as it was before files were read.
     */
    @Override
    public Msg failed(Failure failure) {
        return new InProgressMsg.MarkersRead(List.empty());
    }
}
