package net.vanfleteren.jazygit.features.changes.commit.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.changes.commit.CommitError;
import net.vanfleteren.jazygit.features.changes.commit.create.CommitMsg.*;
import net.vanfleteren.jazygit.features.changes.commit.create.CreateCommitCmd.StageForCommit;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Committing what is staged.
 */
public final class CommitUpdate {

    private CommitUpdate() {}

    public static Next update(Model model, CommitMsg msg) {
        return switch (msg) {
            case Requested() -> requested(model);
            case StageAllConfirmed() -> stageAllConfirmed(model);
            case StagedForCommit(var cmd, List<ExecutedCommand> commands) ->
                OperationUpdate.succeeded(model, cmd, commands).map(m -> m.openPopupIfFree(new CommitPopup()));
        };
    }

    /**
     * Commits what is staged; when nothing is, offers to stage everything first.
     */
    private static Next requested(Model model) {
        return model.repo()
                .files()
                .map(files -> files.exists(FileEntry::staged)
                        ? PopupUpdate.ask(model, new CommitPopup())
                        : files.isEmpty()
                                ? Next.of(model.fail(new CommitError.NothingToCommit()))
                                : PopupUpdate.ask(model, new StageAllPopup()))
                .getOrElse(() -> Next.of(model));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = FileEntry.paths(model.repo().files().getOrElse(List.empty()), FileEntry::unstaged);
        return PopupUpdate.confirm(model, StageAllPopup.class, popup -> Option.of(new StageForCommit(paths)));
    }
}
