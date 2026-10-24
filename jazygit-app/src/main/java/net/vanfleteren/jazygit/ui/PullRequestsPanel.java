package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Scope;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.prs.PrOverview;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * A pane of the pull requests screen, listing pull requests: how long ago each changed, the name of its repository
 * when the list is of any, its number, how its checks and review are doing, its title and its author. Drafts are dimmed.
 * When more match than were read, the title says how many.
 */
public final class PullRequestsPanel {

    /**
     * The id of the pane listing the pull requests that ask for the user's review, the first one.
     */
    public static final String REVIEW_REQUESTED_ID = Scopes.PR_REVIEW_REQUESTED.id();

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
            Consumer<String> openPullRequest,
            Theme.Highlights highlights) {
        this.scope = scope;
        this.pullRequests = pullRequests;
        this.anyRepository = anyRepository;
        list = new LoadableList<>(
                Messages.get(scope.titleKey()),
                scope.id(),
                (previous, next, selected) -> LoadableList.Reselect.<PullRequestSummary, String>following(
                                pr -> pr.repository() + "#" + pr.number())
                        .apply(previous.pullRequests(), next.pullRequests(), selected),
                highlights);
        list.whenEmpty(Messages.get("prs.empty"));
        list.onKey(event -> event.code() == KeyCode.ENTER
                ? selected().map(pr -> () -> openPullRequest.accept(scope.id()))
                : PaneKeys.sending(keymap.handle(scope, event, selected()), dispatch));
    }

    /**
     * The panes of the pull requests screen, top to bottom: the ones that ask for the user's review, the ones they
     * opened, and the ones of the repository.
     */
    public static List<PullRequestsPanel> all(
            Consumer<Msg> dispatch, Consumer<String> openPullRequest, Theme.Highlights highlights) {
        return List.of(
                new PullRequestsPanel(
                        Scopes.PR_REPOSITORY, PrOverview::repository, false, dispatch, openPullRequest, highlights),
                new PullRequestsPanel(
                        Scopes.PR_REVIEW_REQUESTED,
                        PrOverview::reviewRequested,
                        true,
                        dispatch,
                        openPullRequest,
                        highlights),
                new PullRequestsPanel(
                        Scopes.PR_AUTHORED, PrOverview::authored, true, dispatch, openPullRequest, highlights));
    }

    /**
     * The pane that gets the focus when the screen is shown the first time: the first one that lists any.
     */
    public static String firstPane(PrOverview overview) {
        return List.of(Scopes.PR_REVIEW_REQUESTED.id(), Scopes.PR_AUTHORED.id(), Scopes.PR_REPOSITORY.id())
                .zip(List.of(overview.reviewRequested(), overview.authored(), overview.repository()))
                .find(pane ->
                        pane._2.toOption().exists(read -> !read.pullRequests().isEmpty()))
                .map(pane -> pane._1)
                .getOrElse(REVIEW_REQUESTED_ID);
    }

    /**
     * The row of {@code pr}, which changed {@code since} ago.
     *
     * @param anyRepository whether the list is of any repository, so the row names the one of {@code pr}, without its
     *                      owner to keep the row short; the content panel shows it in full
     */
    static LoadableList.Row row(PullRequestSummary pr, Duration since, boolean anyRepository) {
        return row(pr, since, anyRepository, Option.none());
    }

    /**
     * The row of {@code pr}, as {@link #row(PullRequestSummary, Duration, boolean)}, with how {@code merge} goes
     * instead of its checks and review while it goes, or went (ADR 24).
     */
    static LoadableList.Row row(
            PullRequestSummary pr, Duration since, boolean anyRepository, Option<MergeStatus> merge) {
        String plain = pr.draft() ? Theme.Classes.DIM : "";
        String repository =
                anyRepository ? pr.repository().substring(pr.repository().indexOf('/') + 1) : "";
        return new LoadableList.Row(List.of(
                        LoadableList.Seg.of("%4s ".formatted(TimeText.age(since))),
                        LoadableList.Seg.styled(
                                repository + "#" + pr.number(),
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
                        "prs.truncated",
                        name,
                        String.valueOf(read.pullRequests().size()),
                        String.valueOf(read.total())))
                .getOrElse(name);
    }

    public String id() {
        return scope.id();
    }

    /**
     * @param focusedId the id of the element that has the focus
     * @param activeId  the id of the left pane in use, whose highlighted row shows
     */
    public Panel render(Model model, String focusedId, String activeId) {
        Loadable<PullRequestList> shown = pullRequests.apply(model.prOverview());
        return list.render(
                shown,
                read -> read.pullRequests()
                        .map(pr -> row(
                                pr,
                                Duration.between(pr.updatedAt(), model.now()),
                                anyRepository,
                                // The numbers of the merges are of this repository.
                                Option.when(
                                                model.prOverview()
                                                        .ofThisRepository(pr)
                                                        .getOrElse(false),
                                                () -> model.gitHub().merge(pr.number()))
                                        .flatMap(merge -> merge))),
                id().equals(focusedId),
                id().equals(activeId),
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
    public Option<Msg> selectionChange(Model model) {
        return selected()
                .filter(pr -> !model.prOverview().highlighted().contains(pr))
                .map(PrOverviewMsg.Highlighted::new);
    }
}
