package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Commit;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.StageForCommit;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Requested;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageAllCancelled;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageAllConfirmed;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StagedForCommit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

/**
 * Committing what is staged.
 */
public final class CommitUpdate {

    private CommitUpdate() {
    }

    public static Next update(Model model, CommitMsg msg) {
        return switch (msg) {
            case Requested() -> requested(model);
            case StageAllCancelled() -> Next.of(model.withoutPopup(StageAllPopup.class));
            case StageAllConfirmed() -> stageAllConfirmed(model);
            case StagedForCommit(var cmd, List<String> commands) ->
                    Update.refresh(OperationUpdate.done(model, cmd, commands).openPopup(new CommitPopup()));
            case Cancelled() -> Next.of(model.withoutPopup(CommitPopup.class));
            case Confirmed(CommitMessage message) -> confirmed(model, message.stripped());
        };
    }

    /**
     * Commits what is staged; when nothing is, offers to stage everything first.
     */
    private static Next requested(Model model) {
        List<FileEntry> files = Update.files(model);
        if (files.exists(FileEntry::staged)) {
            return Next.of(model.clearError().openPopup(new CommitPopup()));
        }
        if (files.isEmpty()) {
            return Next.of(model.fail(new AppError.NothingToCommit()));
        }
        return Next.of(model.clearError().openPopup(new StageAllPopup()));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = Update.files(model).filter(FileEntry::unstaged).map(FileEntry::path).toList();
        return model.popup(StageAllPopup.class).isDefined()
                ? Next.of(model.withoutPopup(StageAllPopup.class), new StageForCommit(paths))
                : Next.of(model);
    }

    private static Next confirmed(Model model, CommitMessage message) {
        // A blank summary keeps the dialog open.
        return model.popup(CommitPopup.class).isDefined() && message.hasSummary()
                ? Next.of(model.withoutPopup(CommitPopup.class).clearError(), new Commit(message))
                : Next.of(model);
    }
}
