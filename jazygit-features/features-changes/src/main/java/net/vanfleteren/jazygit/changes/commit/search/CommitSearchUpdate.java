package net.vanfleteren.jazygit.changes.commit.search;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Searching the log of HEAD: the line above the commits pane holds the query while it is typed, and running it lists
 * what it finds in the pane in place of the log, until it is cleared. A query that filters nothing clears it.
 */
public final class CommitSearchUpdate {

    private CommitSearchUpdate() {}

    public static Next update(Model model, CommitSearchMsg msg) {
        CommitSearchView view = model.part(CommitSearchView.PART);
        return switch (msg) {
            case CommitSearchMsg.Opened() ->
                shown(
                        model,
                        view.withLine(
                                Option.of(view.search().map(CommitSearch::text).getOrElse(""))));
            case CommitSearchMsg.Edited(String text) ->
                shown(model, view.withLine(view.line().map(open -> text)));
            case CommitSearchMsg.Cancelled() -> shown(model, view.withLine(Option.none()));
            case CommitSearchMsg.Submitted() ->
                view.line().map(text -> run(model, view, text)).getOrElse(() -> Next.of(model));
            case CommitSearchMsg.Cleared() -> shown(model, CommitSearchView.NONE);
        };
    }

    private static Next run(Model model, CommitSearchView view, String text) {
        CommitSearch search = CommitSearch.loading(text.strip());
        CommitSearchView closed = view.withLine(Option.none());
        return search.query().isEmpty()
                ? shown(model, closed.withSearch(Option.none()))
                : Next.of(
                        model.withPart(CommitSearchView.PART, closed.withSearch(Option.of(search))),
                        SearchCommits.of(search));
    }

    private static Next shown(Model model, CommitSearchView view) {
        return Next.of(model.withPart(CommitSearchView.PART, view));
    }
}
