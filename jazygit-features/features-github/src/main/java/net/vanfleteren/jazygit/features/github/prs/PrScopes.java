package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;

/**
 * The panes of the pull requests screen, with keys of their own, and the pull request selected in each, which its
 * keys act on (ADR 168).
 */
public final class PrScopes {

    /**
     * The selected pull request that asks for the user's review.
     */
    public static final Scope<PullRequestSummary> REVIEW_REQUESTED =
            new Scope<>(PrOverview.REVIEW_REQUESTED_PANE.value(), "github.panel.prReviewRequested.title");

    /**
     * The selected pull request the user opened.
     */
    public static final Scope<PullRequestSummary> AUTHORED =
            new Scope<>(PrOverview.AUTHORED_PANE.value(), "github.panel.prAuthored.title");

    /**
     * The selected pull request of the repository.
     */
    public static final Scope<PullRequestSummary> REPOSITORY =
            new Scope<>(PrOverview.REPOSITORY_PANE.value(), "github.panel.prRepository.title");

    /**
     * The panes of the screen, top to bottom.
     */
    public static final List<Scope<PullRequestSummary>> ALL = List.of(REPOSITORY, REVIEW_REQUESTED, AUTHORED);

    private PrScopes() {}
}
