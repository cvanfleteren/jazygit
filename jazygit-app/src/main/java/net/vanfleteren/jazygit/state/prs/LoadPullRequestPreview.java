package net.vanfleteren.jazygit.state.prs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads what the pull request {@code key} says and changes. It runs in the background, as it talks to GitHub. Only
 * the preview of the pull request highlighted last is wanted, so one that has not started when another is asked
 * for is skipped.
 */
public record LoadPullRequestPreview(PullRequestSummary key) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.pullRequest", String.valueOf(key.number())));
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
                .pullRequestPreview(key.url())
                .fold(this::failedWith, preview -> new PrOverviewMsg.PreviewLoaded(key, preview));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.PreviewLoadFailed(key, failure);
    }
}
