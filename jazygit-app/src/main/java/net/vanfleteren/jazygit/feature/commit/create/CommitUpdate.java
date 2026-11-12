package net.vanfleteren.jazygit.feature.commit.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.CommitError;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg.*;
import net.vanfleteren.jazygit.feature.commit.create.CreateCommitCmd.StageForCommit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;

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
        return Update.withFiles(
                model,
                files -> files.exists(FileEntry::staged)
                        ? Update.ask(model, new CommitPopup())
                        : files.isEmpty()
                                ? Next.of(model.fail(new CommitError.NothingToCommit()))
                                : Update.ask(model, new StageAllPopup()));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = FileEntry.paths(Update.files(model), FileEntry::unstaged);
        return Update.confirm(model, StageAllPopup.class, popup -> Option.of(new StageForCommit(paths)));
    }
}
