package net.vanfleteren.jazygit.features.github.panes;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.MergeStatus;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.features.github.prs.PrScopes;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.ui.github.PullRequestSymbols;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * A pane of the pull requests screen, listing pull requests: how long ago each changed, the name of its repository
 * when the list is of any, its number, how its checks and review are doing, its title and its author. Drafts are dimmed.
 * One of the repository that was opened and not seen yet has a mark before its number (ADR 29). When more match than
 * were read, the title says how many.
 */
public final class PullRequestsPanel implements LeftPane {

    /**
     * What marks a pull request that was opened and not seen yet, in its row and in the name of the screen.
     */
    public static final String UNSEEN_MARK = "●";

    private final Scope<PullRequestSummary> scope;
    private final Function<PrOverview, Loadable<PullRequestList>> pullRequests;
    private final boolean anyRepository;
    private final LoadableList<PullRequestList> list;
    private final Keymap keymap = Keymap.standard();

    /**
     * @param scope        the pane, whose id and keys it has
     * @param pullRequests what it lists
     * @param dispatch     receives the messages for the user's actions, on the render thread
     * @param openPullRequest called with the id of the pane when Enter is pressed on a pull request, to move the
     *                     focus to its content
     * @param highlights   how the highlighted pull request is shown, also when the pane is not in use
     */
    private PullRequestsPanel(
            Scope<PullRequestSummary> scope,
            Function<PrOverview, Loadable<PullRequestList>> pullRequests,
            boolean anyRepository,
            Consumer<Msg> dispatch,
            Consumer<PaneId> openPullRequest,
            Theme.Highlights highlights) {
        this.scope = scope;
        this.pullRequests = pullRequests;
        this.anyRepository = anyRepository;
        list = new LoadableList<>(
                Messages.get(scope.titleKey()),
                scope.id(),
                (previous, next, selected) -> LoadableList.Reselect.<PullRequestSummary, String>following(
                                pr -> pr.repository() + "#" + pr.number().value())
                        .apply(previous.pullRequests(), next.pullRequests(), selected),
                highlights);
        list.whenEmpty(Messages.get("github.prs.empty"));
        list.onKey(event -> event.isConfirm()
                ? selected().map(pr -> () -> openPullRequest.accept(scope.pane()))
                : PaneKeys.sending(keymap.handle(scope, event, selected()), dispatch));
    }

    /**
     * The times the panes of the pull requests show while their screen is (ADR 124): how long ago each was updated.
     */
    public static List<ShownTime> shownTimes(Model model) {
        PrOverview overview = model.part(PrOverview.PART);
        return List.of(overview.reviewRequested(), overview.authored(), overview.repository())
                .flatMap(list -> list.toOption().toList())
                .flatMap(PullRequestList::pullRequests)
                .map(pr -> ShownTime.reading(now -> TimeText.age(Duration.between(pr.updatedAt(), now))));
    }

    /**
     * The panes of the pull requests screen, top to bottom: the ones of the repository, the ones that ask for the
     * user's review, and the ones they opened.
     */
    public static List<LeftPane> all(
            Consumer<Msg> dispatch, Consumer<PaneId> openPullRequest, Theme.Highlights highlights) {
        return List.of(
                new PullRequestsPanel(
                        PrScopes.REPOSITORY, PrOverview::repository, false, dispatch, openPullRequest, highlights),
                new PullRequestsPanel(
                        PrScopes.REVIEW_REQUESTED,
                        PrOverview::reviewRequested,
                        true,
                        dispatch,
                        openPullRequest,
                        highlights),
                new PullRequestsPanel(
                        PrScopes.AUTHORED, PrOverview::authored, true, dispatch, openPullRequest, highlights));
    }

    /**
     * The row of {@code pr}, which changed {@code since} ago.
     *
     * @param anyRepository whether the list is of any repository, so the row names the one of {@code pr}, without its
     *                      owner to keep the row short; the content panel shows it in full
     */
    static LoadableList.Row row(PullRequestSummary pr, Duration since, boolean anyRepository) {
        return row(pr, since, anyRepository, Option.none(), false);
    }

    /**
     * The row of {@code pr}, as {@link #row(PullRequestSummary, Duration, boolean)}, with how {@code merge} goes
     * instead of its checks and review while it goes, or went (ADR 24).
     *
     * @param unseen whether {@code pr} was opened and not seen yet, which the space before its number marks
     */
    static LoadableList.Row row(
            PullRequestSummary pr, Duration since, boolean anyRepository, Option<MergeStatus> merge, boolean unseen) {
        String plain = pr.draft() ? Theme.Classes.DIM : "";
        String repository =
                anyRepository ? pr.repository().substring(pr.repository().indexOf('/') + 1) : "";
        String age = "%4s".formatted(TimeText.age(since));
        List<LoadableList.Seg> start = unseen
                ? List.of(LoadableList.Seg.of(age), LoadableList.Seg.styled(UNSEEN_MARK, Theme.Classes.UNSEEN))
                : List.of(LoadableList.Seg.of(age + " "));
        return new LoadableList.Row(start.append(LoadableList.Seg.styled(
                        repository + "#" + pr.number().value(),
                        pr.draft() ? Theme.Classes.DIM : Theme.Classes.PULL_REQUEST))
                .appendAll(PullRequestSymbols.state(pr.stack(), pr.checks(), pr.review(), merge)
                        .map(PullRequestsPanel::symbol))
                .appendAll(List.of(
                        seg(" " + pr.title(), plain), LoadableList.Seg.styled(" " + pr.author(), Theme.Classes.DIM))));
    }

    private static LoadableList.Seg seg(String text, String cssClass) {
        return cssClass.isEmpty() ? LoadableList.Seg.of(text) : LoadableList.Seg.styled(text, cssClass);
    }

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }

    /**
     * The title of the pane: its name, and how many were read of how many match when that is not all of them.
     */
    static String title(String name, Loadable<PullRequestList> pullRequests) {
        return pullRequests
                .toOption()
                .filter(PullRequestList::truncated)
                .map(read -> Messages.get(
                        "github.prs.truncated",
                        name,
                        String.valueOf(read.pullRequests().size()),
                        String.valueOf(read.total())))
                .getOrElse(name);
    }

    /**
     * The pane, as the focus is on it.
     */
    @Override
    public PaneId pane() {
        return scope.pane();
    }

    /**
     * @param focus what has the focus, if anything
     * @param inUse the left pane in use, whose highlighted row shows
     */
    @Override
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        Loadable<PullRequestList> shown = pullRequests.apply(model.part(PrOverview.PART));
        return list.render(
                shown,
                read -> read.pullRequests()
                        .map(pr -> row(
                                pr,
                                Duration.between(pr.updatedAt(), model.now()),
                                anyRepository,
                                // The numbers of the merges are of this repository.
                                Option.when(
                                                model.part(PrOverview.PART)
                                                        .ofThisRepository(pr)
                                                        .getOrElse(false),
                                                () -> model.part(GitHubState.PART)
                                                        .merge(pr.number()))
                                        .flatMap(merge -> merge),
                                model.part(PrOverview.PART).isUnseen(pr))),
                focus.contains(scope.pane()),
                inUse.contains(scope.pane()),
                Pane.title(title(Messages.get(scope.titleKey()), shown)));
    }

    /**
     * The highlighted pull request, once they are shown.
     */
    public Option<PullRequestSummary> selected() {
        return list.selected(PullRequestList::pullRequests);
    }

    /**
     * The message to send when the highlighted pull request is not the one {@code model} has highlighted.
     */
    @Override
    public Option<Msg> selectionChange(Model model) {
        return selected()
                .filter(pr -> !model.part(PrOverview.PART).highlighted().contains(pr))
                .map(PrOverviewMsg.Highlighted::new);
    }
}
