package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Commit;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.StageForCommit;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Done;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Failed;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.Requested;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageAllCancelled;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageAllConfirmed;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StageForCommitFailed;
import net.vanfleteren.jazygit.feature.commit.CommitMsg.StagedForCommit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CommandLog;
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
            case StagedForCommit(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.openPopup(new CommitPopup()), "log.title.stage", commands));
            case StageForCommitFailed(String message) ->
                    Update.refresh(model.withError(Option.of(Messages.get("error.staging.failed", message))));
            case Cancelled() -> Next.of(model.withoutPopup(CommitPopup.class));
            case Confirmed(String summary, String description) -> confirmed(model, summary, description);
            case Done(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Option.none()), "log.title.commit", commands));
            case Failed(String message) -> Update.refresh(model.withError(Option.of(Messages.get("error.commit.failed", message))));
        };
    }

    /**
     * Commits what is staged; when nothing is, offers to stage everything first.
     */
    private static Next requested(Model model) {
        List<FileEntry> files = Update.files(model);
        if (files.exists(FileEntry::staged)) {
            return Next.of(model.withError(Option.none()).openPopup(new CommitPopup()));
        }
        if (files.isEmpty()) {
            return Next.of(model.withError(Option.of(Messages.get("error.commit.nothing"))));
        }
        return Next.of(model.withError(Option.none()).openPopup(new StageAllPopup()));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = Update.files(model).filter(FileEntry::unstaged).map(FileEntry::path).toList();
        return model.popup(StageAllPopup.class).isDefined()
                ? Next.of(model.withoutPopup(StageAllPopup.class), new StageForCommit(paths))
                : Next.of(model);
    }

    private static Next confirmed(Model model, String summary, String description) {
        String trimmed = summary.strip();
        // A blank summary keeps the dialog open.
        return model.popup(CommitPopup.class).isDefined() && !trimmed.isEmpty()
                ? Next.of(model.withoutPopup(CommitPopup.class).withError(Option.none()),
                new Commit(trimmed, description.strip()))
                : Next.of(model);
    }
}
