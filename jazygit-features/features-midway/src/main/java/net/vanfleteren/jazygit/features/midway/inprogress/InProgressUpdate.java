package net.vanfleteren.jazygit.features.midway.inprogress;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.InProgressAction;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Continuing, aborting or skipping the operation the repository stopped in the middle of: a merge, a rebase, a
 * cherry-pick or a revert.
 */
public final class InProgressUpdate {

    private InProgressUpdate() {}

    public static Next update(Model model, InProgressMsg msg) {
        return switch (msg) {
            case InProgressMsg.Requested() -> requested(model);
            case InProgressMsg.MarkersRead(List<MarkedFile> marked) -> ask(model, marked.map(MarkedFile::path));
            case InProgressMsg.Chosen(InProgressAction action) -> chosen(model, action);
        };
    }

    /**
     * Asks what to do with what the repository is in the middle of; but the files resolved since it stopped are read
     * for conflict markers first, which continuing would commit (ADR 198). With nothing in progress, nothing happens.
     */
    private static Next requested(Model model) {
        Option<RepoStatus> status = model.repo()
                .status()
                .toOption()
                .filter(read -> read.inProgress().isDefined());
        return status.map(RepoStatus::resolved)
                .filter(resolved -> !resolved.isEmpty())
                .map(resolved -> Next.of(model, new ReadResolvedMarkersCmd(resolved)))
                .getOrElse(() -> ask(model, List.empty()));
    }

    /**
     * Asks what to do with it, {@code marked} being the files resolved that still have conflict markers.
     */
    private static Next ask(Model model, List<RepoPath> marked) {
        return PopupUpdate.ask(
                model,
                model.repo()
                        .status()
                        .toOption()
                        .flatMap(status -> status.inProgress()
                                .map(inProgress ->
                                        new InProgressPopup(inProgress, status.conflicts(), marked, status.plan()))));
    }

    /**
     * Continuing starts right away, but is refused while conflicts remain, as git would. Aborting and skipping
     * throw work away, so they are confirmed first. A merge cannot be skipped.
     */
    private static Next chosen(Model model, InProgressAction action) {
        return model.popup(InProgressPopup.class)
                .filter(popup ->
                        action != InProgressAction.SKIP || popup.inProgress().skippable())
                .map(popup -> {
                    Model closed = model.withoutPopup(InProgressPopup.class);
                    return action != InProgressAction.CONTINUE
                            ? PopupUpdate.ask(closed, new InProgressConfirmPopup(popup.inProgress(), action))
                            : popup.conflicts() > 0
                                    ? Next.of(closed.fail(new InProgressError.ConflictsRemain(popup.conflicts())))
                                    : OperationUpdate.start(
                                            closed.clearError(), new InProgressCmd(popup.inProgress(), action));
                })
                .getOrElse(() -> Next.of(model));
    }
}
