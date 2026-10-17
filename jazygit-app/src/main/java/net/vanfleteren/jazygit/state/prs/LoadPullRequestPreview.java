package net.vanfleteren.jazygit.state.prs;

import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads what the pull request {@code key} says and changes. It runs in the background, as it talks to GitHub.
 */
public record LoadPullRequestPreview(PullRequestSummary key) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .pullRequestPreview(key.url())
                .fold(this::failedWith, preview -> new PrOverviewMsg.PreviewLoaded(key, preview));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.PreviewLoadFailed(key, failure);
    }
}
