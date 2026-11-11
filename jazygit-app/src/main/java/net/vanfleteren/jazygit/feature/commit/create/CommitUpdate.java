package net.vanfleteren.jazygit.feature.commit.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg.*;
import net.vanfleteren.jazygit.feature.commit.create.CreateCommitCmd.StageForCommit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Committing what is staged.
 */
public final class CommitUpdate {

    private CommitUpdate() {}

    public static Next update(Model model, CommitMsg msg) {
        return switch (msg) {
            case Requested() -> requested(model);
            case StageAllConfirmed() -> stageAllConfirmed(model);
            case StagedForCommit(var cmd, List<ExecutedCommand> commands) -> {
                Next staged = OperationUpdate.succeeded(model, cmd, commands);
                yield new Next(staged.model().openPopupIfFree(new CommitPopup()), staged.cmds());
            }
        };
    }

    /**
     * Commits what is staged; when nothing is, offers to stage everything first.
     */
    private static Next requested(Model model) {
        return model.repo()
                .files()
                .map(files -> files.exists(FileEntry::staged)
                        ? OperationUpdate.ask(model, new CommitPopup())
                        : files.isEmpty()
                                ? Next.of(model.fail(new AppError.NothingToCommit()))
                                : OperationUpdate.ask(model, new StageAllPopup()))
                .getOrElse(() -> Next.of(model));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = FileEntry.paths(model.repo().files().getOrElse(List.empty()), FileEntry::unstaged);
        return OperationUpdate.confirm(model, StageAllPopup.class, popup -> Option.of(new StageForCommit(paths)));
    }
}
