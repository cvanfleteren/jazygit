package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * Reads the open pull requests. It runs in the background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadPullRequests() implements Cmd {

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
        return env.gitHub().pullRequests().fold(this::failedWith, PullRequestsMsg.Loaded::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestsMsg.LoadFailed(failure);
    }
}
