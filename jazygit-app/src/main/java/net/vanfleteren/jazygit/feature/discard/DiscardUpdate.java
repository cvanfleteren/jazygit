package net.vanfleteren.jazygit.feature.discard;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.feature.discard.DiscardCmd.Discard;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Chosen;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Requested;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Discarding changes.
 */
public final class DiscardUpdate {

    private DiscardUpdate() {}

    public static Next update(Model model, DiscardMsg msg) {
        return switch (msg) {
            case Requested(List<FileEntry> files) -> requested(model, files);
            case Chosen(Scope scope) -> chosen(model, scope);
        };
    }

    private static Next requested(Model model, List<FileEntry> files) {
        return Update.ask(model, Option.when(!files.isEmpty(), () -> new DiscardPopup(files)));
    }

    private static Next chosen(Model model, Scope scope) {
        // Without unstaged changes there is nothing to discard of that kind.
        return Update.confirm(
                model,
                DiscardPopup.class,
                popup -> Option.of(popup)
                        .filter(open -> scope == Scope.ALL || open.hasUnstaged())
                        .map(open -> new Discard(plan(open.files(), scope))));
    }

    /**
     * How each file has to be treated to discard it: untracked ones are deleted, tracked ones are checked
     * out again, and files that were only just added are removed altogether. A renamed file is removed, and
     * its origin checked out again.
     */
    static DiscardPlan plan(List<FileEntry> files, Scope scope) {
        Predicate<FileEntry> affected = scope == Scope.ALL ? f -> true : FileEntry::unstaged;
        List<FileEntry> picked = files.filter(affected);
        List<String> untracked = FileEntry.paths(picked, f -> f.type() == ChangeType.UNTRACKED);
        List<FileEntry> tracked = picked.filter(f -> f.type() != ChangeType.UNTRACKED);
        Predicate<FileEntry> removed =
                f -> f.type() == ChangeType.RENAMED || f.type() == ChangeType.ADDED && f.staged();
        return scope == Scope.UNSTAGED
                ? new DiscardPlan(untracked, FileEntry.paths(tracked, f -> true), List.empty(), List.empty())
                : new DiscardPlan(
                        untracked,
                        List.empty(),
                        FileEntry.paths(tracked, removed.negate()).appendAll(tracked.flatMap(FileEntry::origin)),
                        FileEntry.paths(tracked, removed));
    }
}
