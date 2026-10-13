package net.vanfleteren.jazygit.state;

/**
 * Reads the open pull requests. It runs in the background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadPullRequests() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub().pullRequests().fold(
                error -> failed(new Failure.GitHub(error)),
                PullRequestsMsg.Loaded::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestsMsg.LoadFailed(failure);
    }
}
