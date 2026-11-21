package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the open pull requests that ask for the user's review, and the ones they opened, in any repository. It
 * runs in the background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadMyPullRequests() implements Cmd {

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
                .mine()
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, read -> new PrOverviewMsg.MineLoaded(read));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.MineLoadFailed(failure);
    }
}
