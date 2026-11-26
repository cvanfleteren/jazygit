package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.github.prs.unseen.UnseenPullRequests;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * What happens on the pull requests screen.
 */
public sealed interface PrOverviewMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PrOverviewUpdate.update(model, this);
    }

    /**
     * Periodic prompt to read the pull requests again: all of them while they are shown, else the ones of the
     * repository, for the new ones (ADR 29).
     */
    record Tick() implements PrOverviewMsg, Msg.Periodic {

        // The pull requests every 5 minutes rather than every minute.
        public static final Duration WHILE_AWAY = Duration.ofMinutes(5);

        @Override
        public Duration whileAway() {
            return WHILE_AWAY;
        }
    }

    /**
     * The user asked to read the pull requests again.
     */
    record Refresh() implements PrOverviewMsg {}

    /**
     * The pull requests of the repository were read; the model's clock tells when.
     *
     * @param kept    the unseen pull requests that were kept for the repository, if any
     * @param remotes the repository of each remote, as {@code owner/name}, by its name, one's own fork among them,
     *                whose pull requests into the repository are those of its branches (ADR 177)
     */
    record RepositoryLoaded(
            RepositoryPullRequests pullRequests, Option<UnseenPullRequests> kept, Map<RemoteName, String> remotes)
            implements PrOverviewMsg {

        /**
         * Read without the remotes known, so the pull requests of forks are none of its branches'.
         */
        public RepositoryLoaded(RepositoryPullRequests pullRequests, Option<UnseenPullRequests> kept) {
            this(pullRequests, kept, HashMap.empty());
        }
    }

    record RepositoryLoadFailed(Failure failure) implements PrOverviewMsg {}

    /**
     * The pull requests of the user were read; the model's clock tells when.
     */
    record MineLoaded(MyPullRequests pullRequests) implements PrOverviewMsg {}

    record MineLoadFailed(Failure failure) implements PrOverviewMsg {}

    /**
     * The user highlighted {@code pullRequest}.
     */
    record Highlighted(PullRequestSummary pullRequest) implements PrOverviewMsg {}

    record PreviewLoaded(PullRequestSummary key, PullRequestPreview preview) implements PrOverviewMsg {}

    record PreviewLoadFailed(PullRequestSummary key, Failure failure) implements PrOverviewMsg {}

    /**
     * The unseen pull requests were kept, or could not be, which is not worth telling.
     */
    record UnseenKept() implements PrOverviewMsg {}
}
