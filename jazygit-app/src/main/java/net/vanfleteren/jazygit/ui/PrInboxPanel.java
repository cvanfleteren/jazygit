package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.github.prs.PrOverview;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * PROTOTYPE (branch prototype/pr-panel): the one pane of the pull requests screen, an inbox of sections by why a pull
 * request matters to the user. Enter or Space on a section header folds it; the folds are view state kept here.
 */
public final class PrInboxPanel {

    /**
     * A section of the inbox.
     */
    enum Section {
        REVIEW_REQUESTED("prs.section.reviewRequested"),
        MINE_FAILING("prs.section.mineFailing"),
        MINE_WAITING("prs.section.mineWaiting"),
        REPOSITORY("prs.section.repository");

        final String key;

        Section(String key) {
            this.key = key;
        }
    }

    /**
     * A row: a section header, or a pull request in one.
     */
    sealed interface Entry {}

    record Header(Section section, int count, boolean folded) implements Entry {}

    record Item(PullRequestSummary pr, boolean anyRepository) implements Entry {}

    private final LoadableList<List<Entry>> list;
    private final Keymap keymap = Keymap.standard();
    private Set<Section> folded = HashSet.of(Section.REPOSITORY);
    // The entries last made, and what they were made from, so the list is only rebuilt when they change.
    private Option<Object> madeFrom = Option.none();
    private Loadable<List<Entry>> entries = Loadable.loading();

    PrInboxPanel(Consumer<Msg> dispatch, Consumer<String> openPullRequest, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get(Scopes.PR_INBOX.titleKey()),
                Scopes.PR_INBOX.id(),
                LoadableList.Reselect.<Entry, String>following(PrInboxPanel::key),
                highlights);
        list.whenEmpty(Messages.get("prs.empty"));
        list.onKey(event -> {
            Option<Entry> entry = list.selected(all -> all);
            if ((event.code() == KeyCode.ENTER || event.isChar(' ')) && entry.exists(Header.class::isInstance)) {
                Section section = ((Header) entry.get()).section();
                return Option.of(
                        () -> folded = folded.contains(section) ? folded.remove(section) : folded.add(section));
            }
            if (event.code() == KeyCode.ENTER) {
                return selected().map(pr -> () -> openPullRequest.accept(Scopes.PR_INBOX.id()));
            }
            return PaneKeys.sending(keymap.handle(Scopes.PR_INBOX, event, selected()), dispatch);
        });
    }

    private static String key(Entry entry) {
        return switch (entry) {
            case Header header -> "section:" + header.section();
            case Item item -> item.pr().repository() + "#" + item.pr().number();
        };
    }

    public String id() {
        return Scopes.PR_INBOX.id();
    }

    public Panel render(Model model, String focusedId, String activeId) {
        PrOverview overview = model.prOverview();
        Object from = List.of(overview.reviewRequested(), overview.authored(), overview.repository(), folded);
        if (!madeFrom.contains(from)) {
            madeFrom = Option.of(from);
            entries = entries(overview, folded);
        }
        return list.render(
                entries,
                all -> all.map(entry -> row(model, entry)),
                id().equals(focusedId),
                id().equals(activeId),
                Pane.title(Messages.get(Scopes.PR_INBOX.titleKey())));
    }

    private static LoadableList.Row row(Model model, Entry entry) {
        return switch (entry) {
            case Header(Section section, int count, boolean isFolded) ->
                LoadableList.Row.of(
                        LoadableList.Seg.styled(
                                (isFolded ? "▸ " : "▾ ") + Messages.get(section.key), Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" (" + count + ")", Theme.Classes.DIM));
            case Item(PullRequestSummary pr, boolean anyRepository) -> {
                LoadableList.Row row = PullRequestsPanel.row(
                        pr,
                        Duration.between(pr.updatedAt(), model.now()),
                        anyRepository,
                        Option.none(),
                        model.prOverview().isUnseen(pr));
                yield new LoadableList.Row(row.segs().prepend(LoadableList.Seg.of("  ")));
            }
        };
    }

    /**
     * The sections, once the user's lists were read; the repository's are added once they are read too. A pull
     * request shows in the first section it fits.
     */
    static Loadable<List<Entry>> entries(PrOverview overview, Set<Section> folded) {
        return overview.authored().map(authored -> {
            List<PullRequestSummary> review = overview.reviewRequested()
                    .map(PullRequestList::pullRequests)
                    .getOrElse(List.empty());
            List<PullRequestSummary> mine = authored.pullRequests();
            List<PullRequestSummary> failing = mine.filter(pr -> pr.checks() == Checks.FAILING);
            List<PullRequestSummary> waiting = mine.removeAll(failing);
            List<PullRequestSummary> taken = review.appendAll(mine);
            List<PullRequestSummary> repository = overview.repository()
                    .map(PullRequestList::pullRequests)
                    .getOrElse(List.empty())
                    .filter(pr -> !taken.exists(other ->
                            other.number() == pr.number() && other.repository().equalsIgnoreCase(pr.repository())));
            return section(Section.REVIEW_REQUESTED, review, true, folded)
                    .appendAll(section(Section.MINE_FAILING, failing, true, folded))
                    .appendAll(section(Section.MINE_WAITING, waiting, true, folded))
                    .appendAll(section(Section.REPOSITORY, repository, false, folded));
        });
    }

    private static List<Entry> section(
            Section section, List<PullRequestSummary> prs, boolean anyRepository, Set<Section> folded) {
        boolean isFolded = folded.contains(section);
        List<Entry> items = isFolded ? List.empty() : prs.map(pr -> new Item(pr, anyRepository));
        return items.prepend(new Header(section, prs.size(), isFolded));
    }

    /**
     * The highlighted pull request, when a pull request and not a header is highlighted.
     */
    public Option<PullRequestSummary> selected() {
        return list.selected(all -> all)
                .flatMap(entry -> entry instanceof Item item ? Option.of(item.pr()) : Option.none());
    }

    public Option<Msg> selectionChange(Model model) {
        return selected()
                .filter(pr -> !model.prOverview().highlighted().contains(pr))
                .map(PrOverviewMsg.Highlighted::new);
    }
}
