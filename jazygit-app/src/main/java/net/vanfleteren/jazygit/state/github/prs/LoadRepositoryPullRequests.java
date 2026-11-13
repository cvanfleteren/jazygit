package net.vanfleteren.jazygit.state.github.prs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads the open pull requests of the repository, forks included, and the unseen ones kept for it. It runs in the
 * background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadRepositoryPullRequests() implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.pullRequests"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .pullRequests()
                .ofRepository()
                .mapLeft(Failure::of)
                .fold(
                        this::failed,
                        read -> new PrOverviewMsg.RepositoryLoaded(
                                read, env.unseen().load(read.repository())));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.RepositoryLoadFailed(failure);
    }
}
