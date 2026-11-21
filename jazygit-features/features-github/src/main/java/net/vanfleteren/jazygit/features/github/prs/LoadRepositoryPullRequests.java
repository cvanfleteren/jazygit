package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.GitHubStores;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the open pull requests of the repository, forks included, and the unseen ones kept for it. It runs in the
 * background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadRepositoryPullRequests() implements GitHubState.ListRead {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.pullRequests"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .pullRequests()
                .ofRepository()
                .mapLeft(GitHubFailure::of)
                .fold(
                        this::failed,
                        read -> new PrOverviewMsg.RepositoryLoaded(
                                read, env.service(GitHubStores.UNSEEN).load(read.repository())));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.RepositoryLoadFailed(failure);
    }
}
