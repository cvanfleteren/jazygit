package net.vanfleteren.jazygit.features.changes.commit.search;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.Targeted;

/**
 * The search of the log of HEAD: the line above the commits pane it is typed in, and the search that ran last, whose
 * results the commits pane lists in place of the log until it is cleared (ADR 158).
 *
 * @param line   the text of the line, while it is open
 * @param search the search that ran last, until it is cleared
 */
@With
public record CommitSearchView(Option<String> line, Option<CommitSearch> search) {

    public static final CommitSearchView NONE = new CommitSearchView(Option.none(), Option.none());

    /**
     * Where the model keeps the search. Its results are read again when reading them failed, or once the log was read
     * again, as HEAD moved; the commits it found can be acted on as those of the log.
     */
    public static final Part<CommitSearchView> PART = Part.of("changes.commit.search", NONE)
            .rereading((model, view) -> view.loads())
            .knowingCommits((view, sha) -> view.search().flatMap(search -> search.commit(sha)))
            .reacting((model, view, event) -> switch (event) {
                case KernelEvent.LogRead() -> new Next(model, List.narrow(view.loads()));
                case KernelEvent.OperationDone _,
                        KernelEvent.Fetched _,
                        KernelEvent.RepositoryOpened _,
                        KernelEvent.ScreenShown _ -> Next.of(model);
            });

    /**
     * The load of the results of the search that ran, if one did.
     */
    List<Targeted<?>> loads() {
        return search.<Targeted<?>>map(SearchCommits::of).toList();
    }
}
