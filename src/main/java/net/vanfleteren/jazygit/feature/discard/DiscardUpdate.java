package net.vanfleteren.jazygit.feature.discard;

import net.vanfleteren.jazygit.feature.discard.DiscardCmd.Discard;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Cancelled;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Chosen;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Done;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Failed;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Requested;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Discarding changes.
 */
public final class DiscardUpdate {

    private DiscardUpdate() {
    }

    public static Next update(Model model, DiscardMsg msg) {
        return switch (msg) {
            case Requested(List<FileEntry> files) -> requested(model, files);
            case Cancelled() -> Next.of(model.withoutPopup(DiscardPopup.class));
            case Chosen(Scope scope) -> chosen(model, scope);
            case Done() -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.discard.failed", message))));
        };
    }

    private static Next requested(Model model, List<FileEntry> files) {
        return files.isEmpty() ? Next.of(model) : Next.of(model.openPopup(new DiscardPopup(files)));
    }

    private static Next chosen(Model model, Scope scope) {
        return model.popup(DiscardPopup.class)
                // Without unstaged changes there is nothing to discard of that kind.
                .filter(popup -> scope == Scope.ALL || popup.hasUnstaged())
                .map(popup -> Next.of(model.withoutPopup(DiscardPopup.class).withError(Optional.empty()),
                        new Discard(plan(popup.files(), scope))))
                .orElseGet(() -> Next.of(model));
    }

    /**
     * How each file has to be treated to discard it: untracked ones are deleted, tracked ones are checked
     * out again, and files that were only just added are removed altogether.
     */
    static DiscardPlan plan(List<FileEntry> files, Scope scope) {
        Predicate<FileEntry> affected = scope == Scope.ALL ? f -> true : FileEntry::unstaged;
        List<FileEntry> picked = files.stream().filter(affected).toList();
        List<String> untracked = paths(picked, f -> f.type() == ChangeType.UNTRACKED);
        List<FileEntry> tracked = picked.stream().filter(f -> f.type() != ChangeType.UNTRACKED).toList();
        if (scope == Scope.UNSTAGED) {
            return new DiscardPlan(untracked, paths(tracked, f -> true), List.of(), List.of());
        }
        return new DiscardPlan(untracked, List.of(),
                paths(tracked, f -> f.type() != ChangeType.ADDED || !f.staged()),
                paths(tracked, f -> f.type() == ChangeType.ADDED && f.staged()));
    }

    private static List<String> paths(List<FileEntry> files, Predicate<FileEntry> filter) {
        return files.stream().filter(filter).map(FileEntry::path).toList();
    }
}
