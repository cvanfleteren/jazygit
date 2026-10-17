package net.vanfleteren.jazygit.state.prs;

import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads the open pull requests that ask for the user's review, and the ones they opened, in any repository. It
 * runs in the background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadMyPullRequests() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub().myPullRequests().fold(this::failedWith, PrOverviewMsg.MineLoaded::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.MineLoadFailed(failure);
    }
}
