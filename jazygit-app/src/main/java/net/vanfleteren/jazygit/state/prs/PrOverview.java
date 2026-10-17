package net.vanfleteren.jazygit.state.prs;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
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
 * @param reading         the reads that are running, so a tick does not start them again
 * @param highlighted     the pull request highlighted in the pane in use, once one is
 * @param preview         what the highlighted pull request says and changes, once one is highlighted
 * @param focusedPane     the id of the pane of the screen that had the focus last, which it gets back when the
 *                        screen is shown again; none before one had it
 */
@With
public record PrOverview(
        Loadable<PullRequestList> reviewRequested,
        Loadable<PullRequestList> authored,
        Loadable<PullRequestList> repository,
        Option<String> repositoryName,
        Set<Cmd> reading,
        Option<PullRequestSummary> highlighted,
        Option<Preview<PullRequestSummary, PullRequestPreview>> preview,
        Option<String> focusedPane) {

    public static final PrOverview INITIAL = new PrOverview(
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Option.none(),
            HashSet.empty(),
            Option.none(),
            Option.none(),
            Option.none());

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
