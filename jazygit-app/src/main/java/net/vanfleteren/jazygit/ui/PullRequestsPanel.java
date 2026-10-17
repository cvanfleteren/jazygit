package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;

/**
 * A pane of the pull requests screen, listing pull requests: how long ago each changed, its number, title and
 * author, and how its checks and review are doing. Drafts are dimmed.
 */
public final class PullRequestsPanel {

    /**
     * The id of the pane listing the pull requests of the repository.
     */
    public static final String REPOSITORY_ID = Scopes.PR_REPOSITORY.id();

    private final LoadableList<List<PullRequestSummary>> list;
    private final Keymap keymap = Keymap.standard();
    private final Clock clock;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     * @param clock    tells how long ago the pull requests changed
     */
    public PullRequestsPanel(Consumer<Msg> dispatch, Clock clock) {
        this(dispatch, clock, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted pull request is shown, also when the pane is not in use
     */
    public PullRequestsPanel(Consumer<Msg> dispatch, Clock clock, Theme.Highlights highlights) {
        this.clock = clock;
        list = new LoadableList<>(
                Messages.get(Scopes.PR_REPOSITORY.titleKey()),
                REPOSITORY_ID,
                pullRequests -> pullRequests.map(this::row),
                LoadableList.Reselect.following(pr -> pr.repository() + "#" + pr.number()),
                highlights);
        list.whenEmpty(Messages.get("prs.empty"));
        list.onKey(event -> event.code() == KeyCode.ENTER
                ? Option.none()
                : PaneKeys.sending(keymap.handle(Scopes.PR_REPOSITORY, event, selected()), dispatch));
    }

    private LoadableList.Row row(PullRequestSummary pr) {
        return row(pr, Duration.between(pr.updatedAt(), clock.instant()));
    }

    /**
     * The row of {@code pr}, which changed {@code since} ago.
     */
    static LoadableList.Row row(PullRequestSummary pr, Duration since) {
        String plain = pr.draft() ? Theme.Classes.DIM : "";
        return new LoadableList.Row(List.of(
                        Option.of(LoadableList.Seg.of("%4s ".formatted(TimeText.age(since)))),
                        Option.of(LoadableList.Seg.styled(
                                "#" + pr.number(), pr.draft() ? Theme.Classes.DIM : Theme.Classes.PULL_REQUEST)),
                        PullRequestSymbols.checks(pr.checks()).map(PullRequestsPanel::symbol),
                        PullRequestSymbols.review(pr.review()).map(PullRequestsPanel::symbol),
                        Option.of(seg(" " + pr.title(), plain)),
                        Option.of(LoadableList.Seg.styled(" " + pr.author(), Theme.Classes.DIM)))
                .flatMap(seg -> seg));
    }

    private static LoadableList.Seg seg(String text, String cssClass) {
        return cssClass.isEmpty() ? LoadableList.Seg.of(text) : LoadableList.Seg.styled(text, cssClass);
    }

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }

    /**
     * @param focusedId the id of the element that has the focus
     * @param activeId  the id of the left pane in use, whose highlighted row shows
     */
    public Panel render(Model model, String focusedId, String activeId) {
        return render(model.prOverview().repository(), focusedId, activeId);
    }

    Panel render(Loadable<List<PullRequestSummary>> pullRequests, String focusedId, String activeId) {
        return list.render(pullRequests, REPOSITORY_ID.equals(focusedId), REPOSITORY_ID.equals(activeId));
    }

    /**
     * The highlighted pull request, once they are shown.
     */
    public Option<PullRequestSummary> selected() {
        return list.selected(pullRequests -> pullRequests);
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
