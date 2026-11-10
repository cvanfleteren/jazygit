package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.model.Commit;

/**
 * Reads the log of HEAD from the commit {@code ahead} reads from, where the status read last says HEAD is (ADR 49),
 * and then what of it is not on its base, as {@code ahead} does: the log is shown with it. Without a commit, the
 * log is empty and nothing is read.
 */
public record LoadCommits(LoadAhead ahead) implements Targeted<List<Commit>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.commits"));
    }

    @Override
    public Target<List<Commit>> target() {
        return Target.inRepo(Repo::commits, Repo::withCommits);
    }

    @Override
    public Msg run(Env env) {
        return ahead.tip()
                .fold(
                        () -> Either.<GitError, List<Commit>>right(List.empty()),
                        env.git().history()::commits)
                .mapLeft(Failure::of)
                .fold(this::failed, commits -> new LoadMsg.CommitsLoaded(commits, ahead, ahead.read(env)));
    }
}
