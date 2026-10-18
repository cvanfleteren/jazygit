package net.vanfleteren.jazygit.state.prs;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The pull requests screen. Its transitions work on the {@link PrOverview} alone; only switching screens, and
 * whether the screen is shown, need the rest of the {@link Model}.
 */
public final class PrOverviewUpdate {

    /**
     * The reads of what the screen shows.
     */
    private static final List<Cmd> LOADS = List.of(new LoadMyPullRequests(), new LoadRepositoryPullRequests());

    // The reads, as the freshness of the pull requests names them.
    private static final String REPOSITORY = "repository";
    private static final String MINE = "mine";

    private PrOverviewUpdate() {}

    public static Next update(Model model, PrOverviewMsg msg) {
        return switch (msg) {
            case PrOverviewMsg.Toggled() ->
                reloadIfShown(Next.of(model.withScreen(model.screen().other())));
            // The screen already shown stays as it is, without reading it again.
            case PrOverviewMsg.Chosen(Screen screen) ->
                screen == model.screen() ? Next.of(model) : reloadIfShown(Next.of(model.withScreen(screen)));
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
            // What failed to read before is read again too.
            case PrOverviewMsg.Refresh() -> {
                Step loaded = load(overview);
                yield overview.preview()
                        .filter(preview -> preview.value() instanceof Loadable.Failed)
                        .map(preview -> preview(loaded.overview(), preview.key(), loaded.cmds()))
                        .getOrElse(loaded);
            }
            case PrOverviewMsg.RepositoryLoaded(var read, var at) ->
                Step.of(done(overview, new LoadRepositoryPullRequests())
                        .withFreshness(overview.freshness().succeeded(REPOSITORY, at))
                        .withRepository(overview.repository().reload(newestFirst(read.open())))
                        .withRepositoryName(Option.of(read.repository())));
            case PrOverviewMsg.RepositoryLoadFailed(Failure failure) ->
                Step.of(done(overview, new LoadRepositoryPullRequests())
                        .withFreshness(overview.freshness().failed(REPOSITORY))
                        .withRepository(failed(overview.repository(), failure)));
            case PrOverviewMsg.MineLoaded(var read, var at) ->
                Step.of(done(overview, new LoadMyPullRequests())
                        .withFreshness(overview.freshness().succeeded(MINE, at))
                        .withReviewRequested(overview.reviewRequested().reload(newestFirst(read.reviewRequested())))
                        .withAuthored(overview.authored().reload(newestFirst(read.authored()))));
            case PrOverviewMsg.MineLoadFailed(Failure failure) ->
                Step.of(done(overview, new LoadMyPullRequests())
                        .withFreshness(overview.freshness().failed(MINE))
                        .withReviewRequested(failed(overview.reviewRequested(), failure))
                        .withAuthored(failed(overview.authored(), failure)));
            case PrOverviewMsg.Highlighted(PullRequestSummary pullRequest) -> {
                PrOverview highlighted = overview.withHighlighted(Option.of(pullRequest));
                // A pull request that changed since its preview was read is read again.
                yield overview.preview().exists(preview -> preview.key().equals(pullRequest))
                        ? Step.of(highlighted)
                        : preview(highlighted, pullRequest, List.empty());
            }
            case PrOverviewMsg.PreviewLoaded(PullRequestSummary key, var preview) ->
                Step.of(overview.withPreview(Preview.updated(overview.preview(), key, shown -> shown.reload(preview))));
            case PrOverviewMsg.PreviewLoadFailed(PullRequestSummary key, Failure failure) ->
                Step.of(overview.withPreview(Preview.updated(overview.preview(), key, shown -> shown.failed(failure))));
            case PrOverviewMsg.Toggled(), PrOverviewMsg.Tick() -> Step.of(overview);
            case PrOverviewMsg.Chosen(Screen screen) -> Step.of(overview);
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
     * {@code next}, also reading the pull requests again while they are shown, e.g. as something changed on
     * GitHub.
     */
    public static Next reloadIfShown(Next next) {
        return next.model().screen() == Screen.PULL_REQUESTS ? reload(next) : next;
    }

    private static Step load(PrOverview overview) {
        List<Cmd> loads = LOADS.filter(load -> !overview.reading().contains(load));
        return new Step(overview.withReading(overview.reading().addAll(loads)), loads);
    }

    /**
     * {@code overview} reading the preview of the pull request {@code key}, after {@code cmds}; the one shown before stays until it
     * is read.
     */
    private static Step preview(PrOverview overview, PullRequestSummary key, List<Cmd> cmds) {
        return new Step(
                overview.withPreview(Option.of(Preview.loading(key, overview.preview()))),
                cmds.append(new LoadPullRequestPreview(key)));
    }

    private static PrOverview done(PrOverview overview, Cmd load) {
        return overview.withReading(overview.reading().remove(load));
    }

    /**
     * Why {@code list} could not be read; the pull requests read before stay, as reading them again can fail for a
     * while, say offline.
     */
    private static Loadable<PullRequestList> failed(Loadable<PullRequestList> list, Failure failure) {
        return list instanceof Loadable.Loaded ? list : new Loadable.Failed<>(failure);
    }

    private static PullRequestList newestFirst(PullRequestList list) {
        return new PullRequestList(
                list.pullRequests().sortBy(PullRequestSummary::updatedAt).reverse(), list.total());
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
