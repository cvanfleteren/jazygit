package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.features.changes.edit.EditMsg;
import net.vanfleteren.jazygit.features.changes.edit.EditUpdate;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.Chosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.EditChosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.Kept;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.MarkedEditChosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.MarkedKept;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.Requested;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.RestoreRequested;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Resolving a file in conflict by keeping one side of it, and bringing a conflict back (ADR 198).
 */
public final class ResolveUpdate {

    private ResolveUpdate() {}

    public static Next update(Model model, ResolveMsg msg) {
        return switch (msg) {
            case Kept(FileEntry file, Conflict.Version version) -> keep(model, file, version);
            case Requested(FileEntry file) ->
                PopupUpdate.ask(
                        model,
                        file.conflict().isDefined()
                                ? Sides.of(model).map(sides -> new ResolvePopup(file, sides))
                                : Option.none());
            case Chosen(Conflict.Version version) ->
                model.popup(ResolvePopup.class)
                        .map(popup -> keep(model.withoutPopup(ResolvePopup.class), popup.file(), version))
                        .getOrElse(() -> Next.of(model));
            case EditChosen() ->
                model.popup(ResolvePopup.class)
                        .map(popup -> edit(
                                model.withoutPopup(ResolvePopup.class),
                                popup.file().path()))
                        .getOrElse(() -> Next.of(model));
            case RestoreRequested(String path) -> restore(model, path);
            case MarkedKept(Conflict.Version version) ->
                model.popup(MarkersLeftPopup.class)
                        .flatMap(popup -> marked(popup)
                                .map(file -> keep(model.withoutPopup(MarkersLeftPopup.class), file, version)))
                        .getOrElse(() -> Next.of(model));
            case MarkedEditChosen() ->
                model.popup(MarkersLeftPopup.class)
                        .flatMap(popup -> popup.marked().headOption())
                        .map(file -> edit(model.withoutPopup(MarkersLeftPopup.class), file.path()))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * The one file staging asked about, in conflict, when it asked about one only: a side is kept of a file at a time.
     */
    public static Option<FileEntry> marked(MarkersLeftPopup popup) {
        return Option.when(popup.marked().size() == 1, () -> popup.marked().head())
                .flatMap(marked -> popup.files().find(file -> file.path().equals(marked.path())))
                .filter(file -> file.conflict().isDefined());
    }

    /**
     * Keeps {@code version}'s side of {@code file}, if it is in conflict; a file that is not has nothing to keep.
     */
    private static Next keep(Model model, FileEntry file, Conflict.Version version) {
        return file.conflict()
                .map(conflict ->
                        OperationUpdate.start(model.clearError(), new ResolveCmd.Keep(file.path(), conflict, version)))
                .getOrElse(() -> Next.of(model));
    }

    private static Next edit(Model model, String path) {
        return EditUpdate.update(model, new EditMsg.Requested(path));
    }

    /**
     * Brings back the conflict of the file at {@code path}, if it was resolved since the operation stopped.
     */
    private static Next restore(Model model, String path) {
        boolean resolved = model.repo()
                .status()
                .toOption()
                .exists(status -> status.resolved().contains(path));
        return resolved
                ? OperationUpdate.start(model.clearError(), new ResolveCmd.Restore(path))
                : Next.of(model.fail(new ResolveError.NothingToRestore(path)));
    }
}
