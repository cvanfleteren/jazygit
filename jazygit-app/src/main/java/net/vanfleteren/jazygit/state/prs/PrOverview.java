package net.vanfleteren.jazygit.state.prs;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * What the pull requests screen shows: everything of it, apart from the rest of the {@code Model}.
 *
 * @param repository  the open pull requests of the repository, forks included, the most recently updated first;
 *                    loading until they were read once, which they are when the screen is first shown
 * @param reading     how many reads of them are running, so a tick does not start another one
 * @param highlighted the pull request highlighted in the pane that has the focus, once one is
 * @param focusedPane the id of the pane of the screen that had the focus last, which it gets back when the screen
 *                    is shown again; none before one had it
 */
@With
public record PrOverview(
        Loadable<List<PullRequestSummary>> repository,
        int reading,
        Option<PullRequestSummary> highlighted,
        Option<String> focusedPane) {

    public static final PrOverview INITIAL = new PrOverview(Loadable.loading(), 0, Option.none(), Option.none());
}
