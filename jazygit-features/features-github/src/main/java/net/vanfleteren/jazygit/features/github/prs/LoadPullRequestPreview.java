package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads what the pull request {@code key} says and changes. It runs in the background, as it talks to GitHub. Only
 * the preview of the pull request highlighted last is wanted, so one that has not started when another is asked
 * for is skipped.
 */
public record LoadPullRequestPreview(PullRequestSummary key) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(
                Label.of("state.work.pullRequest", String.valueOf(key.number().value())));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.PULL_REQUEST_PREVIEW);
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .pullRequests()
                .preview(key.url())
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, preview -> new PrOverviewMsg.PreviewLoaded(key, preview));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.PreviewLoadFailed(key, failure);
    }
}
