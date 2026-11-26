package net.vanfleteren.jazygit.features.changes.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the files in conflict among those asked to be staged, {@code conflicted}, for conflict markers left in them,
 * before they are staged (ADR 198).
 *
 * @param files      the files asked to be staged, as {@link StageMsg.Requested} has them
 * @param directory  the directory asked to be staged, as {@link StageMsg.Requested} has it
 * @param conflicted the paths of those of {@code files} that are in conflict
 */
public record ReadMarkersCmd(List<FileEntry> files, Option<RepoPath> directory, List<RepoPath> conflicted)
        implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .index()
                .markers(conflicted)
                .fold(error -> failed(Failure.of(error)), marked -> new StageMsg.MarkersRead(files, directory, marked));
    }

    /**
     * A file that cannot be read has no markers to warn of: it is staged, as it was before files were read.
     */
    @Override
    public Msg failed(Failure failure) {
        return new StageMsg.MarkersRead(files, directory, List.empty());
    }
}
