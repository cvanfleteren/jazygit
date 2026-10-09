package net.vanfleteren.jazygit.state.update;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.CommitMsg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Cmd.Commit;
import net.vanfleteren.jazygit.state.Cmd.StageForCommit;
import net.vanfleteren.jazygit.state.CommitMsg.Cancelled;
import net.vanfleteren.jazygit.state.CommitMsg.Confirmed;
import net.vanfleteren.jazygit.state.CommitMsg.Done;
import net.vanfleteren.jazygit.state.CommitMsg.Failed;
import net.vanfleteren.jazygit.state.CommitMsg.Requested;
import net.vanfleteren.jazygit.state.CommitMsg.StageAllCancelled;
import net.vanfleteren.jazygit.state.CommitMsg.StageAllConfirmed;
import net.vanfleteren.jazygit.state.CommitMsg.StageForCommitFailed;
import net.vanfleteren.jazygit.state.CommitMsg.StagedForCommit;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.List;
import java.util.Optional;

/**
 * Committing what is staged.
 */
public final class CommitUpdate {

    private CommitUpdate() {
    }

    public static Next update(Model model, CommitMsg msg) {
        return switch (msg) {
            case Requested() -> requested(model);
            case StageAllCancelled() -> Next.of(model.withStageAllPrompt(false));
            case StageAllConfirmed() -> stageAllConfirmed(model);
            case StagedForCommit() -> Update.refresh(model.withCommitOpen(true));
            case StageForCommitFailed(String message) ->
                    Update.refresh(model.withError(Optional.of("Staging failed: " + message)));
            case Cancelled() -> Next.of(model.withCommitOpen(false));
            case Confirmed(String summary, String description) -> confirmed(model, summary, description);
            case Done() -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String message) -> Update.refresh(model.withError(Optional.of("Commit failed: " + message)));
        };
    }

    /**
     * Commits what is staged; when nothing is, offers to stage everything first.
     */
    private static Next requested(Model model) {
        List<FileEntry> files = Update.files(model);
        if (files.stream().anyMatch(FileEntry::staged)) {
            return Next.of(model.withError(Optional.empty()).withCommitOpen(true));
        }
        if (files.isEmpty()) {
            return Next.of(model.withError(Optional.of("Nothing to commit")));
        }
        return Next.of(model.withError(Optional.empty()).withStageAllPrompt(true));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = Update.files(model).stream().filter(FileEntry::unstaged).map(FileEntry::path).toList();
        return model.stageAllPrompt()
                ? Next.of(model.withStageAllPrompt(false), new StageForCommit(paths))
                : Next.of(model);
    }

    private static Next confirmed(Model model, String summary, String description) {
        String trimmed = summary.strip();
        // A blank summary keeps the dialog open.
        return model.commitOpen() && !trimmed.isEmpty()
                ? Next.of(model.withCommitOpen(false).withError(Optional.empty()),
                new Commit(trimmed, description.strip()))
                : Next.of(model);
    }
}
