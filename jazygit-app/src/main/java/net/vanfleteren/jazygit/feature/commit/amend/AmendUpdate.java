package net.vanfleteren.jazygit.feature.commit.amend;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg.Requested;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg.StageAllConfirmed;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget.LastCommit;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget.Older;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Amending the last commit, or any other commit of the checked out branch, with the staged changes, keeping its
 * message.
 */
public final class AmendUpdate {

    private AmendUpdate() {}

    public static Next update(Model model, AmendMsg msg) {
        return switch (msg) {
            case Requested(Option<Sha> sha) -> requested(model, sha);
            case Cancelled() -> Next.of(model.withoutPopup(AmendPopup.class).withoutPopup(AmendStageAllPopup.class));
            case Confirmed() -> confirmed(model);
            case StageAllConfirmed() -> stageAllConfirmed(model);
        };
    }

    /**
     * Asks for confirmation, when the commit can be amended and there is something to amend it with. No commit, or
     * the one HEAD is, is the last commit.
     */
    private static Next requested(Model model, Option<Sha> sha) {
        Option<Sha> head = model.repo().status().toOption().flatMap(RepoStatus::headCommit);
        return sha.filter(older -> !head.contains(older))
                .fold(
                        () -> ask(model, new LastCommit()),
                        older -> model.repo()
                                .commit(older)
                                .map(commit -> commit.isMerge()
                                        ? Next.of(model.fail(new AppError.MergeNotAmendable()))
                                        : ask(model, new Older(older)))
                                .getOrElse(() -> Next.of(model)));
    }

    private static Next ask(Model model, AmendTarget target) {
        return Update.withFiles(
                model,
                files -> files.isEmpty()
                        ? Next.of(model.fail(new AppError.NothingToAmend()))
                        : Update.ask(model, new AmendPopup(target)));
    }

    /**
     * Amends with the staged files; when none is staged, asks whether to amend with all files instead.
     */
    private static Next confirmed(Model model) {
        return Update.files(model).exists(FileEntry::staged)
                ? Update.confirm(
                        model, AmendPopup.class, popup -> Option.of(new AmendCmd(popup.target(), List.empty())))
                : model.popup(AmendPopup.class)
                        .map(popup -> Update.ask(
                                model.withoutPopup(AmendPopup.class), new AmendStageAllPopup(popup.target())))
                        .getOrElse(() -> Next.of(model));
    }

    private static Next stageAllConfirmed(Model model) {
        List<String> paths = FileEntry.paths(Update.files(model), FileEntry::unstaged);
        return Update.confirm(model, AmendStageAllPopup.class, popup -> Option.of(new AmendCmd(popup.target(), paths)));
    }
}
