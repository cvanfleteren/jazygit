package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.features.github.prs.unseen.UnseenPullRequests;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.PullRequestsError;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Part;
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
     * How wide the left column of the screen is kept: under the same key in the settings file as before the screen
     * was GitHub's feature's, so nothing needs migrating (ADR 168).
     */
    public static final SettingKey<Integer> WIDTH = Settings.leftWidth("ui.pull-requests-width");

    /**
     * How tall the first two panes of the screen are kept, unless the user changed it, in percent of the height of the
     * left column; the last takes what is left, which is about as much in a column under the status.
     */
    public static final SettingKey<List<Integer>> HEIGHTS =
            Settings.leftHeights("ui.pull-requests-heights", List.of(32, 32));

    /**
     * The screen of the pull requests, which GitHub's feature brings (ADR 168): top to bottom those of the repository,
     * those that ask for the user's review and those they opened.
     */
    public static final Screen SCREEN = new Screen(
            "pullRequests",
            List.of(REPOSITORY_PANE, REVIEW_REQUESTED_PANE, AUTHORED_PANE),
            WIDTH,
            HEIGHTS,
            Option.of(GitHubFeature.ID));

    public static final PrOverview INITIAL = new PrOverview(
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Option.none(),
            Freshness.NEVER,
            Option.none(),
            Option.none());

    /**
     * Where the model keeps what the screen shows, as a part of GitHub's feature (ADR 157, ADR 168).
     */
    public static final Part<PrOverview> PART = Part.of("github.prs", INITIAL);

    /**
     * How many pull requests of the repository were opened and not seen yet.
     */
    public int unseenCount() {
        return unseen.map(read -> read.numbers().size()).getOrElse(0);
    }

    /**
     * Whether {@code pullRequest} is of the repository of {@code model}, and was opened and not seen yet.
     */
    public static boolean isUnseen(Model model, PullRequestSummary pullRequest) {
        return model.part(PART).isUnseen(model.part(GitHubState.PART).repository(), pullRequest);
    }

    /**
     * Whether {@code pullRequest} is of the repository, {@code repositoryName}, and was opened and not seen yet.
     */
    public boolean isUnseen(Option<RepoSlug> repositoryName, PullRequestSummary pullRequest) {
        return ofThisRepository(repositoryName, pullRequest).getOrElse(false)
                && unseen.exists(
                        read -> read.numbers().contains(pullRequest.number().value()));
    }

    /**
     * Whether {@code pullRequest} is of the repository of {@code model}, as {@link #ofThisRepository(Option,
     * PullRequestSummary)} says: GitHub's state knows which one it is once its pull requests were read.
     */
    public static Either<AppError, Boolean> ofThisRepository(Model model, PullRequestSummary pullRequest) {
        return model.part(PART).ofThisRepository(model.part(GitHubState.PART).repository(), pullRequest);
    }

    /**
     * Whether {@code pullRequest} is of the repository, {@code repositoryName} as {@code owner/name}, wherever it is
     * listed, once that is known: it is known once the pull requests of the repository were read, and why it is not
     * known otherwise, as they are still being read or could not be.
     */
    public Either<AppError, Boolean> ofThisRepository(Option<RepoSlug> repositoryName, PullRequestSummary pullRequest) {
        return repositoryName
                .map(name -> Either.<AppError, Boolean>right(
                        name.value().equalsIgnoreCase(pullRequest.repository().value())))
                .getOrElse(() -> Either.left(
                        repository instanceof Loadable.Failed<PullRequestList>(Failure failure)
                                ? new PullRequestsError.Unavailable(failure)
                                : new PullRequestsError.NotRead()));
    }
}
