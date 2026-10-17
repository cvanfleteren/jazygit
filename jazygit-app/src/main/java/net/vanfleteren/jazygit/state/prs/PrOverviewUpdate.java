package net.vanfleteren.jazygit.state.prs;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The pull requests screen. Its transitions work on the {@link PrOverview} alone; only switching screens, and
 * whether the screen is shown, need the rest of the {@link Model}.
 */
public final class PrOverviewUpdate {

    private PrOverviewUpdate() {}

    public static Next update(Model model, PrOverviewMsg msg) {
        return switch (msg) {
            case PrOverviewMsg.Toggled() -> {
                Model toggled = model.withScreen(model.screen().other());
                yield toggled.screen() == Screen.PULL_REQUESTS ? reload(Next.of(toggled)) : Next.of(toggled);
            }
            case PrOverviewMsg.Tick() -> reloadIfShown(Next.of(model));
            default -> {
                Step step = update(model.prOverview(), msg);
                yield new Next(model.withPrOverview(step.overview()), step.cmds());
            }
        };
    }

    /**
     * What {@code msg} does to {@code overview}, whatever the rest of the model is.
     */
    static Step update(PrOverview overview, PrOverviewMsg msg) {
        return switch (msg) {
            case PrOverviewMsg.Refresh() -> load(overview);
            case PrOverviewMsg.RepositoryLoaded(var pullRequests) ->
                Step.of(read(overview).withRepository(overview.repository().reload(newestFirst(pullRequests))));
            // Pull requests read before stay, as reading them again can fail for a while, say offline.
            case PrOverviewMsg.RepositoryLoadFailed(var failure) ->
                Step.of(read(overview)
                        .withRepository(
                                overview.repository() instanceof Loadable.Loaded
                                        ? overview.repository()
                                        : new Loadable.Failed<>(failure)));
            case PrOverviewMsg.Highlighted(PullRequestSummary pullRequest) ->
                Step.of(overview.withHighlighted(Option.of(pullRequest)));
            case PrOverviewMsg.Toggled(), PrOverviewMsg.Tick() -> Step.of(overview);
        };
    }

    /**
     * {@code next}, also reading the pull requests again, unless a read is still running.
     */
    public static Next reload(Next next) {
        Step step = load(next.model().prOverview());
        return new Next(
                next.model().withPrOverview(step.overview()), next.cmds().appendAll(step.cmds()));
    }

    /**
     * {@code next}, also reading the pull requests again while they are shown, as something changed on GitHub.
     */
    public static Next reloadIfShown(Next next) {
        return next.model().screen() == Screen.PULL_REQUESTS ? reload(next) : next;
    }

    private static Step load(PrOverview overview) {
        return overview.reading() > 0
                ? Step.of(overview)
                : new Step(overview.withReading(overview.reading() + 1), List.of(new LoadRepositoryPullRequests()));
    }

    private static PrOverview read(PrOverview overview) {
        return overview.withReading(Math.max(0, overview.reading() - 1));
    }

    private static List<PullRequestSummary> newestFirst(List<PullRequestSummary> pullRequests) {
        return pullRequests.sortBy(PullRequestSummary::updatedAt).reverse();
    }

    /**
     * The next overview, and the commands to run for it.
     */
    record Step(PrOverview overview, List<Cmd> cmds) {

        static Step of(PrOverview overview) {
            return new Step(overview, List.empty());
        }
    }
}
