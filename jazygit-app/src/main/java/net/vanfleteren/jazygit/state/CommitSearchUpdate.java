package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Searching the log of HEAD: the line above the commits pane holds the query while it is typed, and running it lists
 * what it finds in the pane in place of the log, until it is cleared. A query that filters nothing clears it.
 */
public final class CommitSearchUpdate {

    private CommitSearchUpdate() {}

    public static Next update(Model model, CommitSearchMsg msg) {
        return switch (msg) {
            case CommitSearchMsg.Opened() ->
                Next.of(model.mapRepoView(repoView -> repoView.withSearchLine(Option.of(
                        repoView.commitSearch().map(CommitSearch::text).getOrElse("")))));
            case CommitSearchMsg.Edited(String text) ->
                Next.of(model.mapRepoView(repoView ->
                        repoView.withSearchLine(repoView.searchLine().map(open -> text))));
            case CommitSearchMsg.Cancelled() ->
                Next.of(model.mapRepoView(repoView -> repoView.withSearchLine(Option.none())));
            case CommitSearchMsg.Submitted() ->
                model.repoView().searchLine().map(text -> run(model, text)).getOrElse(() -> Next.of(model));
            case CommitSearchMsg.Cleared() ->
                Next.of(model.mapRepoView(
                        repoView -> repoView.withSearchLine(Option.none()).withCommitSearch(Option.none())));
        };
    }

    private static Next run(Model model, String text) {
        CommitSearch search = CommitSearch.loading(text.strip());
        Model closed = model.mapRepoView(repoView -> repoView.withSearchLine(Option.none()));
        return search.query().isEmpty()
                ? Next.of(closed.mapRepoView(repoView -> repoView.withCommitSearch(Option.none())))
                : Next.of(closed.mapRepoView(repoView -> repoView.withCommitSearch(Option.of(search))), search.load());
    }
}
