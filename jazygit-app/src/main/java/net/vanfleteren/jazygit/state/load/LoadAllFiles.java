package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Repo;

/**
 * Reads the paths of all files of the working tree, which the files pane lists while it lists them all (ADR 108).
 */
public record LoadAllFiles() implements Targeted<List<String>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.allFiles"));
    }

    @Override
    public Target<List<String>> target() {
        return Target.inRepo(Repo::allFiles, Repo::withAllFiles);
    }

    @Override
    public Msg run(Env env) {
        return env.git().status().allFiles().mapLeft(Failure::of).fold(this::failed, LoadMsg.AllFilesLoaded::new);
    }
}
