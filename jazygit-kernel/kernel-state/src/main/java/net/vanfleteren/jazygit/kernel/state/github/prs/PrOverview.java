package net.vanfleteren.jazygit.kernel.state.github.prs;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.UnseenPullRequests;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.github.PullRequestsError;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.view.Preview;

/**
 * What the pull requests screen shows: everything of it, apart from the rest of the {@code Model}. The lists are
 * loading until they were read once, which they are when the screen is first shown; each has the most recently
 * updated first.
 *
 * @param reviewRequested the open pull requests that ask for the user's review, in any repository
 * @param authored        the open pull requests the user opened, in any repository
 * @param repository      the open pull requests of the repository, forks included
 * @param repositoryName  the repository on GitHub, as {@code owner/name}, once its pull requests were read
 * @param unseen          the pull requests of the repository that were opened and not seen yet, once they were
 *                        read (ADR 29)
 * @param freshness       when the pull requests were last read, and which reads failed the last time
 * @param highlighted     the pull request highlighted in the pane in use, once one is
 * @param preview         what the highlighted pull request says and changes, once one is highlighted
 */
@With
public record PrOverview(
        Loadable<PullRequestList> reviewRequested,
        Loadable<PullRequestList> authored,
        Loadable<PullRequestList> repository,
        Option<String> repositoryName,
        Option<UnseenPullRequests> unseen,
        Freshness freshness,
        Option<PullRequestSummary> highlighted,
        Option<Preview<PullRequestSummary, PullRequestPreview>> preview) {

    /**
     * The pane of the open pull requests of the repository, which the screen starts with.
     */
    public static final PaneId REPOSITORY_PANE = new PaneId("prRepository");

    /**
     * The pane of the open pull requests that ask for the user's review.
     */
    public static final PaneId REVIEW_REQUESTED_PANE = new PaneId("prReviewRequested");

    /**
     * The pane of the open pull requests the user opened.
     */
    public static final PaneId AUTHORED_PANE = new PaneId("prAuthored");

    /**
     * The screen of the pull requests, top to bottom those of the repository, those that ask for the user's review
     * and those they opened, which is still declared in the kernel (ADR 168, step 1).
     */
    public static final Screen SCREEN = new Screen(
            "pullRequests",
            List.of(REPOSITORY_PANE, REVIEW_REQUESTED_PANE, AUTHORED_PANE),
            Settings.PULL_REQUESTS_WIDTH,
            Settings.PULL_REQUESTS_HEIGHTS);

    public static final PrOverview INITIAL = new PrOverview(
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Option.none(),
            Option.none(),
            Freshness.NEVER,
            Option.none(),
            Option.none());

    /**
     * How many pull requests of the repository were opened and not seen yet.
     */
    public int unseenCount() {
        return unseen.map(read -> read.numbers().size()).getOrElse(0);
    }

    /**
     * Whether {@code pullRequest} is of the repository, and was opened and not seen yet.
     */
    public boolean isUnseen(PullRequestSummary pullRequest) {
        return ofThisRepository(pullRequest).getOrElse(false)
                && unseen.exists(
                        read -> read.numbers().contains(pullRequest.number().value()));
    }

    /**
     * Whether {@code pullRequest} is of the repository, wherever it is listed, once that is known: it is known once
     * the pull requests of the repository were read, and why it is not known otherwise, as they are still being
     * read or could not be.
     */
    public Either<AppError, Boolean> ofThisRepository(PullRequestSummary pullRequest) {
        return repositoryName
                .map(name -> Either.<AppError, Boolean>right(name.equalsIgnoreCase(pullRequest.repository())))
                .getOrElse(() -> Either.left(
                        repository instanceof Loadable.Failed<PullRequestList>(Failure failure)
                                ? new PullRequestsError.Unavailable(failure)
                                : new PullRequestsError.NotRead()));
    }
}
