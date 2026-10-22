package net.vanfleteren.jazygit.state.prs;

import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads the open pull requests of the repository, forks included. It runs in the background, as it talks to
 * GitHub, and nobody waits for it.
 */
public record LoadRepositoryPullRequests() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .repositoryPullRequests()
                .fold(this::failedWith, read -> new PrOverviewMsg.RepositoryLoaded(read));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.RepositoryLoadFailed(failure);
    }
}
