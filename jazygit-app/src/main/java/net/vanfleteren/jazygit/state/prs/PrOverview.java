package net.vanfleteren.jazygit.state.prs;

import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.settings.UnseenPullRequests;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Freshness;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Preview;

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
 * @param listPane        the id of the list that had the focus last, whose highlighted pull request the content
 *                        panel shows; none before one had it
 * @param focusedPane     the id of the pane of the screen that had the focus last, which it gets back when the
 *                        screen is shown again; none before one had it
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
        Option<Preview<PullRequestSummary, PullRequestPreview>> preview,
        Option<String> listPane,
        Option<String> focusedPane) {

    public static final PrOverview INITIAL = new PrOverview(
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Option.none(),
            Option.none(),
            Freshness.NEVER,
            Option.none(),
            Option.none(),
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
                && unseen.exists(read -> read.numbers().contains(pullRequest.number()));
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
                                ? new AppError.PullRequestsUnavailable(failure)
                                : new AppError.PullRequestsNotRead()));
    }
}
