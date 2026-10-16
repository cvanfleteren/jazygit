package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.AmendCommitMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.AmendCommitMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.AmendCommitMsg.Requested;
import net.vanfleteren.jazygit.feature.commit.AmendCommitMsg.StageAllConfirmed;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.AmendCommit;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Amending any commit of the checked out branch with the staged changes, keeping its message.
 */
public final class AmendCommitUpdate {

    private AmendCommitUpdate() {}

    public static Next update(Model model, AmendCommitMsg msg) {
        return switch (msg) {
            case Requested(Sha sha) -> requested(model, sha);
            case Cancelled() ->
                Next.of(model.withoutPopup(AmendCommitPopup.class).withoutPopup(AmendCommitStageAllPopup.class));
            case Confirmed() -> confirmed(model);
            case StageAllConfirmed() -> stageAllConfirmed(model);
        };
    }

    /**
     * Asks for confirmation, when the commit can be amended and there is something to amend it with.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha)
                .map(commit -> commit.isMerge()
                        ? Next.of(model.fail(new AppError.MergeNotAmendable()))
                        : Update.withFiles(
                                model,
                                files -> files.isEmpty()
                                        ? Next.of(model.fail(new AppError.NothingToAmend()))
                                        : Update.ask(model, new AmendCommitPopup(commit))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Amends with the staged files; when none is staged, asks whether to amend with all files instead.
     */
    private static Next confirmed(Model model) {
        return Update.files(model).exists(FileEntry::staged)
                ? Update.confirm(model, AmendCommitPopup.class, popup -> Option.of(amend(popup.commit(), List.empty())))
                : model.popup(AmendCommitPopup.class)
                        .map(popup -> Update.ask(
                                model.withoutPopup(AmendCommitPopup.class),
                                new AmendCommitStageAllPopup(popup.commit())))
                        .getOrElse(() -> Next.of(model));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = FileEntry.paths(Update.files(model), FileEntry::unstaged);
        return Update.confirm(model, AmendCommitStageAllPopup.class, popup -> Option.of(amend(popup.commit(), paths)));
    }

    private static AmendCommit amend(Commit commit, List<String> stage) {
        return new AmendCommit(commit.sha(), stage);
    }
}
