package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Repo;

/**
 * Reads the paths of all files of the working tree, which the files pane lists while it lists them all (ADR 108).
 */
public record LoadAllFiles() implements Targeted<List<RepoPath>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.allFiles"));
    }

    @Override
    public Target<List<RepoPath>> target() {
        return Target.inRepo(Repo::allFiles, Repo::withAllFiles);
    }

    @Override
    public Msg run(Env env) {
        return env.git().status().allFiles().mapLeft(Failure::of).fold(this::failed, LoadMsg.AllFilesLoaded::new);
    }
}
