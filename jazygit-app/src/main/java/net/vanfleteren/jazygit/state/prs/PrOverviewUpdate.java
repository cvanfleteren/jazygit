package net.vanfleteren.jazygit.state.prs;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.settings.UnseenPullRequests;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Jobs;
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
    /**
     * The read while the screen is hidden, for the new pull requests of the repository (ADR 29).
     */
    private static final List<Cmd> HIDDEN_LOADS = List.of(new LoadRepositoryPullRequests());

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
            case PrOverviewMsg.Tick() ->
                model.screen() == Screen.PULL_REQUESTS ? reload(Next.of(model)) : loaded(Next.of(model), HIDDEN_LOADS);
            default -> {
                Step step = update(model.prOverview(), msg, model.now(), model.jobs());
                yield new Next(model.withPrOverview(step.overview()), step.cmds());
            }
        };
    }

    /**
     * What {@code msg} does to {@code overview} at {@code now}, while {@code jobs} run, whatever the rest of the model
     * is.
     */
    static Step update(PrOverview overview, PrOverviewMsg msg, Instant now, Jobs jobs) {
        return switch (msg) {
            // What failed to read before is read again too.
            case PrOverviewMsg.Refresh() -> {
                List<Cmd> loads = notRunning(LOADS, jobs);
                yield overview.preview()
                        .filter(preview -> preview.value() instanceof Loadable.Failed)
                        .map(preview -> preview(overview, preview.key(), loads))
                        .getOrElse(() -> new Step(overview, loads));
            }
            case PrOverviewMsg.RepositoryLoaded(var read, var kept) -> {
                PrOverview loaded = overview.withFreshness(overview.freshness().succeeded(REPOSITORY, now))
                        .withRepository(overview.repository().reload(newestFirst(read.open())))
                        .withRepositoryName(Option.of(read.repository()));
                // What was kept is only of use until the model has its own.
                Option<UnseenPullRequests> before = overview.unseen().orElse(kept);
                UnseenPullRequests after = unseen(before, read);
                yield kept(Step.of(loaded.withUnseen(Option.of(after))), before.contains(after));
            }
            case PrOverviewMsg.RepositoryLoadFailed(Failure failure) ->
                Step.of(overview.withFreshness(overview.freshness().failed(REPOSITORY))
                        .withRepository(failed(overview.repository(), failure)));
            case PrOverviewMsg.MineLoaded(var read) ->
                Step.of(overview.withFreshness(overview.freshness().succeeded(MINE, now))
                        .withReviewRequested(overview.reviewRequested().reload(newestFirst(read.reviewRequested())))
                        .withAuthored(overview.authored().reload(newestFirst(read.authored()))));
            case PrOverviewMsg.MineLoadFailed(Failure failure) ->
                Step.of(overview.withFreshness(overview.freshness().failed(MINE))
                        .withReviewRequested(failed(overview.reviewRequested(), failure))
                        .withAuthored(failed(overview.authored(), failure)));
            case PrOverviewMsg.Highlighted(PullRequestSummary pullRequest) -> {
                PrOverview highlighted = overview.withHighlighted(Option.of(pullRequest));
                // A pull request that changed since its preview was read is read again.
                Step shown = overview.preview().exists(preview -> preview.key().equals(pullRequest))
                        ? Step.of(highlighted)
                        : preview(highlighted, pullRequest, List.empty());
                yield seen(shown, pullRequest);
            }
            case PrOverviewMsg.PreviewLoaded(PullRequestSummary key, var preview) ->
                Step.of(overview.withPreview(Preview.updated(overview.preview(), key, shown -> shown.reload(preview))));
            case PrOverviewMsg.PreviewLoadFailed(PullRequestSummary key, Failure failure) ->
                Step.of(overview.withPreview(Preview.updated(overview.preview(), key, shown -> shown.failed(failure))));
            case PrOverviewMsg.Toggled(), PrOverviewMsg.Tick(), PrOverviewMsg.UnseenKept() -> Step.of(overview);
            case PrOverviewMsg.Chosen(Screen screen) -> Step.of(overview);
        };
    }

    /**
     * {@code next}, also reading the pull requests again, unless a read is still running.
     */
    public static Next reload(Next next) {
        return loaded(next, LOADS);
    }

    /**
     * {@code next}, also reading the pull requests again while they are shown, e.g. as something changed on
     * GitHub.
     */
    public static Next reloadIfShown(Next next) {
        return next.model().screen() == Screen.PULL_REQUESTS ? reload(next) : next;
    }

    /**
     * {@code next}, also running {@code loads}, but the ones still running.
     */
    private static Next loaded(Next next, List<Cmd> loads) {
        return new Next(
                next.model(),
                next.cmds().appendAll(notRunning(loads, next.model().jobs())));
    }

    /**
     * Those of {@code loads} that do not run in {@code jobs}.
     */
    private static List<Cmd> notRunning(List<Cmd> loads, Jobs jobs) {
        return loads.filter(load -> !jobs.runs(load));
    }

    /**
     * The unseen pull requests of the repository after {@code read}, when they were {@code before}, if they were
     * read before (ADR 29). The highest number is the one of the pull request opened last, open or not, so one that
     * is reopened is not new. The open ones above the highest number read before were opened since, though the user's
     * own are not unseen; the ones not open anymore are left out. Read for the first time, none are unseen, else
     * every open one would be.
     */
    static UnseenPullRequests unseen(Option<UnseenPullRequests> before, RepositoryPullRequests read) {
        List<PullRequestSummary> open = read.open().pullRequests();
        // The open ones count too, should GitHub not have said which was opened last.
        int highest =
                open.map(PullRequestSummary::number).append(read.latest()).max().getOrElse(0);
        return before.map(kept -> new UnseenPullRequests(
                        Math.max(kept.highest(), highest),
                        kept.numbers()
                                .retainAll(open.map(PullRequestSummary::number))
                                .addAll(open.filter(pr -> pr.number() > kept.highest()
                                                && !read.viewer().exists(pr.author()::equalsIgnoreCase))
                                        .map(PullRequestSummary::number))))
                .getOrElse(() -> UnseenPullRequests.upTo(highest));
    }

    /**
     * {@code step}, with {@code pullRequest} seen, if it was not yet.
     */
    private static Step seen(Step step, PullRequestSummary pullRequest) {
        PrOverview overview = step.overview();
        return overview.isUnseen(pullRequest)
                ? kept(
                        new Step(
                                overview.withUnseen(overview.unseen().map(unseen -> unseen.seen(pullRequest.number()))),
                                step.cmds()),
                        false)
                : step;
    }

    /**
     * {@code step}, also keeping the unseen pull requests it has for the next session, unless they are kept
     * {@code already}.
     */
    private static Step kept(Step step, boolean already) {
        PrOverview overview = step.overview();
        return already
                ? step
                : new Step(
                        overview,
                        step.cmds()
                                .appendAll(overview.repositoryName()
                                        .flatMap(name -> overview.unseen()
                                                .map(unseen -> new SaveUnseenPullRequests(name, unseen)))));
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

    /**
     * Why {@code list} could not be read; the pull requests read before stay, as reading them again can fail for a
     * while, say offline.
     */
    private static Loadable<PullRequestList> failed(Loadable<PullRequestList> list, Failure failure) {
        return list.failed(failure);
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
