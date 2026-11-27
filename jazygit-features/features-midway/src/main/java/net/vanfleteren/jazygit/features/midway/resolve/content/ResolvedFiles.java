package net.vanfleteren.jazygit.features.midway.resolve.content;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.features.changes.panes.files.FileMark;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * What the files pane shows of the files resolved since the operation in progress stopped (ADR 198): a {@code ✓}
 * after each, and how many of them there are in its title.
 */
public final class ResolvedFiles {

    private static final List<LoadableList.Seg> RESOLVED = List.of(LoadableList.Seg.styled(" ✓", Theme.Classes.STAGED));

    private ResolvedFiles() {}

    /**
     * The mark of the files resolved in {@code model}, if any is. A file resolved to what HEAD has, without changes,
     * is dimmed, as it is listed only to be found again; one in conflict again is not marked.
     */
    public static Option<FileMark> mark(Model model) {
        Set<RepoPath> resolved = model.repo()
                .status()
                .toOption()
                .map(status -> status.resolved().toSet())
                .getOrElse(HashSet.empty());
        return Option.when(
                !resolved.isEmpty(),
                () -> entry -> Option.when(
                        resolved.contains(entry.path()) && entry.conflict().isEmpty(),
                        () -> new FileMark.Mark(RESOLVED, !entry.changed())));
    }

    /**
     * How far the resolving of the conflicts of the operation in progress got, once one of them is resolved:
     * {@code 2 of 4 resolved}.
     */
    public static Option<Phrase> progress(Model model) {
        return model.repo().status().toOption().flatMap(ResolvedFiles::progress);
    }

    private static Option<Phrase> progress(RepoStatus status) {
        int done = status.resolved().size();
        return Option.when(
                status.inProgress().isDefined() && done > 0,
                () -> Phrase.of(
                        "midway.panes.files.resolved", Phrase.count(done), Phrase.count(done + status.conflicts())));
    }
}
