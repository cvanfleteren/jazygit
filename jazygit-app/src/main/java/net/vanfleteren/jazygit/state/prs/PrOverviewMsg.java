package net.vanfleteren.jazygit.state.prs;

import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * What happens on the pull requests screen.
 */
public sealed interface PrOverviewMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PrOverviewUpdate.update(model, this);
    }

    /**
     * The user asked to show the other screen.
     */
    record Toggled() implements PrOverviewMsg {}

    /**
     * Periodic prompt to read the pull requests again, while they are shown.
     */
    record Tick() implements PrOverviewMsg {}

    /**
     * The user asked to read the pull requests again.
     */
    record Refresh() implements PrOverviewMsg {}

    /**
     * The pull requests of the repository were read, at {@code at}.
     */
    record RepositoryLoaded(RepositoryPullRequests pullRequests, Instant at) implements PrOverviewMsg {}

    record RepositoryLoadFailed(Failure failure) implements PrOverviewMsg {}

    /**
     * The pull requests of the user were read, at {@code at}.
     */
    record MineLoaded(MyPullRequests pullRequests, Instant at) implements PrOverviewMsg {}

    record MineLoadFailed(Failure failure) implements PrOverviewMsg {}

    /**
     * The user highlighted {@code pullRequest}.
     */
    record Highlighted(PullRequestSummary pullRequest) implements PrOverviewMsg {}

    record PreviewLoaded(PullRequestSummary key, PullRequestPreview preview) implements PrOverviewMsg {}

    record PreviewLoadFailed(PullRequestSummary key, Failure failure) implements PrOverviewMsg {}
}
