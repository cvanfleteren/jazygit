package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StagedForCommit;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageForCommitFailed;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageAllConfirmed;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageAllCancelled;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.Requested;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.Failed;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.Done;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.Confirmed;

import net.vanfleteren.jazygit.feature.commit.CommitMsg.Cancelled;

import net.vanfleteren.jazygit.feature.commit.CommitCmd.StageForCommit;

import net.vanfleteren.jazygit.feature.commit.CommitCmd.Commit;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.git.model.FileEntry;
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
                    Update.refresh(model.withError(Optional.of(Messages.get("error.staging.failed", message))));
            case Cancelled() -> Next.of(model.withCommitOpen(false));
            case Confirmed(String summary, String description) -> confirmed(model, summary, description);
            case Done() -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String message) -> Update.refresh(model.withError(Optional.of(Messages.get("error.commit.failed", message))));
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
            return Next.of(model.withError(Optional.of(Messages.get("error.commit.nothing"))));
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
