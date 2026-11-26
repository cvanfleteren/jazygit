package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.Chosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.EditChosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.HandedBack;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.Kept;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.MarkedEditChosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.MarkedKept;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.MergeToolChosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.MergirafChosen;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.Requested;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.RestoreRequested;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveMsg.ToolsRead;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.edit.FileEditor;
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
            case Requested(FileEntry file) -> requested(model, file);
            case ToolsRead(RepoPath path, ConflictTools tools, Option<Integer> firstConflict, ConflictFile written) ->
                Next.of(model.popup(ResolvePopup.class)
                        .filter(popup -> popup.file().path().equals(path))
                        .map(popup -> model.openPopup(popup.read(tools, firstConflict, written)))
                        .getOrElse(model));
            case MergeToolChosen() ->
                handOver(
                        model,
                        popup -> popup.tools()
                                .mergesWithTool(popup.file().conflict().get()),
                        popup -> new ResolveCmd.MergeTool(popup.file().path(), popup.tools()));
            case MergirafChosen() -> solve(model);
            case HandedBack(
                    ResolveCmd cmd,
                    List<ExecutedCommand> commands,
                    RepoPath path,
                    boolean unmerged,
                    Option<MarkedFile> marked) ->
                OperationUpdate.succeeded(model, cmd, commands)
                        .map(done ->
                                left(path, unmerged, marked).map(done::inform).getOrElse(done));
            case Chosen(Conflict.Version version) ->
                model.popup(ResolvePopup.class)
                        .map(popup -> keep(model.withoutPopup(ResolvePopup.class), popup.file(), version))
                        .getOrElse(() -> Next.of(model));
            case EditChosen() ->
                model.popup(ResolvePopup.class)
                        .map(popup -> edit(
                                model.withoutPopup(ResolvePopup.class),
                                popup.file().path(),
                                popup.firstConflict()))
                        .getOrElse(() -> Next.of(model));
            case RestoreRequested(RepoPath path) -> restore(model, path);
            case MarkedKept(Conflict.Version version) ->
                model.popup(MarkersLeftPopup.class)
                        .flatMap(popup -> marked(popup)
                                .map(file -> keep(model.withoutPopup(MarkersLeftPopup.class), file, version)))
                        .getOrElse(() -> Next.of(model));
            case MarkedEditChosen() ->
                model.popup(MarkersLeftPopup.class)
                        .flatMap(popup -> popup.marked().headOption())
                        .map(file -> edit(
                                model.withoutPopup(MarkersLeftPopup.class),
                                file.path(),
                                file.lines().headOption()))
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

    /**
     * Opens the file at {@code path} in an editor: at {@code line}, its first conflict, when that was read; else it is
     * read first (ADR 201), by the feature that opens files (ADR 206).
     */
    private static Next edit(Model model, RepoPath path, Option<Integer> line) {
        return Events.happened(Next.of(model), new KernelEvent.FileToOpen(path.value(), line, Option.none()));
    }

    /**
     * Opens the resolve dialog of {@code file}, if it is in conflict, and reads what it can be handed to and where its
     * first conflict starts, which the dialog offers once they are read (ADR 201).
     */
    private static Next requested(Model model, FileEntry file) {
        Option<ResolvePopup> popup = file.conflict()
                .flatMap(conflict -> Sides.of(model))
                .map(sides -> new ResolvePopup(
                        file, sides, FileEditor.of(model), ConflictTools.NONE, Option.none(), ConflictFile.AS_WRITTEN));
        return popup.map(open -> PopupUpdate.ask(model, open).and(new ReadToolsCmd(file.path())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Solves the file of the resolve dialog with mergiraf: as it is when it has its bases, after writing its conflict
     * again when it is as git wrote it; and, when it was edited since, which writing it again loses, once the user
     * confirmed that (ADR 201).
     */
    private static Next solve(Model model) {
        return model.popup(ResolvePopup.class)
                .filter(popup ->
                        popup.tools().solvesWithMergiraf(popup.file().conflict().get()))
                .map(popup -> popup.written() == ConflictFile.EDITED
                        ? PopupUpdate.ask(
                                model.withoutPopup(ResolvePopup.class),
                                new SolveOverEditsPopup(popup.file().path()))
                        : OperationUpdate.start(
                                model.withoutPopup(ResolvePopup.class).clearError(),
                                new ResolveCmd.Solve(popup.file().path(), popup.rewrites())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Closes the resolve dialog and starts what {@code cmd} makes of it, when it {@code offers} that.
     */
    private static Next handOver(Model model, Predicate<ResolvePopup> offers, Function<ResolvePopup, ResolveCmd> cmd) {
        return model.popup(ResolvePopup.class)
                .filter(offers)
                .map(popup -> OperationUpdate.start(
                        model.withoutPopup(ResolvePopup.class).clearError(), cmd.apply(popup)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * What is left to do with the file at {@code path}, handed back by a tool: staging it, when it is still in
     * conflict without markers; its conflicts, when it has markers left; nothing, when git took it as resolved.
     */
    private static Option<HandedBackNotice> left(RepoPath path, boolean unmerged, Option<MarkedFile> marked) {
        return marked.<HandedBackNotice>map(file -> new HandedBackNotice.MarkersLeft(
                        path, file.lines().size(), file.lines().head()))
                .orElse(() -> Option.when(unmerged, () -> new HandedBackNotice.NoMarkersLeft(path)));
    }

    /**
     * Brings back the conflict of the file at {@code path}, if it was resolved since the operation stopped.
     */
    private static Next restore(Model model, RepoPath path) {
        boolean resolved = model.repo()
                .status()
                .toOption()
                .exists(status -> status.resolved().contains(path));
        return resolved
                ? OperationUpdate.start(model.clearError(), new ResolveCmd.Restore(path))
                : Next.of(model.fail(new ResolveError.NothingToRestore(path)));
    }
}
