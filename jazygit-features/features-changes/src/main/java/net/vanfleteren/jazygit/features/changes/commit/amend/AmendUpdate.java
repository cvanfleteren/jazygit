package net.vanfleteren.jazygit.features.changes.commit.amend;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.CommitError;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheck;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendMsg.Checked;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendMsg.Confirmed;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendMsg.Requested;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendMsg.StageAllConfirmed;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendTarget.LastCommit;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendTarget.Older;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Amending the last commit, or any other commit of the checked out branch, with the staged changes, keeping its
 * message.
 */
public final class AmendUpdate {

    private AmendUpdate() {}

    public static Next update(Model model, AmendMsg msg) {
        return switch (msg) {
            case Requested(Option<Sha> sha) -> requested(model, sha);
            case Confirmed() -> confirmed(model);
            case StageAllConfirmed() -> stageAllConfirmed(model);
            case Checked(var target, var warning) -> PopupUpdate.ask(model, new AmendPopup(target, warning));
        };
    }

    /**
     * Asks for confirmation, when the commit can be amended and there is something to amend it with. No commit, or
     * the one HEAD is, is the last commit.
     */
    private static Next requested(Model model, Option<Sha> sha) {
        Option<Sha> head = head(model);
        return sha.filter(older -> !head.contains(older))
                .fold(
                        () -> ask(model, new LastCommit()),
                        older -> model.commit(older)
                                .map(commit -> commit.isMerge()
                                        ? Next.of(model.fail(new CommitError.MergeNotAmendable()))
                                        : ask(model, new Older(older)))
                                .getOrElse(() -> Next.of(model)));
    }

    /**
     * Asks to confirm amending {@code target}, warning when it is pushed already.
     */
    private static Next ask(Model model, AmendTarget target) {
        Option<Sha> amended = switch (target) {
            case LastCommit() -> head(model);
            case Older(var sha) -> Option.of(sha);
        };
        return model.repo()
                .files()
                .map(files -> files.isEmpty()
                        ? Next.of(model.fail(new CommitError.NothingToAmend()))
                        : amended.map(sha -> PushedCheck.first(model, sha, target, () -> unwarned(model, target)))
                                .getOrElse(() -> unwarned(model, target)))
                .getOrElse(() -> Next.of(model));
    }

    private static Next unwarned(Model model, AmendTarget target) {
        return PopupUpdate.ask(model, new AmendPopup(target));
    }

    private static Option<Sha> head(Model model) {
        return model.repo().status().toOption().flatMap(RepoStatus::headCommit);
    }

    /**
     * Amends with the staged files; when none is staged, asks whether to amend with all files instead.
     */
    private static Next confirmed(Model model) {
        return model.repo().files().getOrElse(List.empty()).exists(FileEntry::staged)
                ? PopupUpdate.confirm(
                        model, AmendPopup.class, popup -> Option.of(new AmendCmd(popup.target(), List.empty())))
                : model.popup(AmendPopup.class)
                        .map(popup -> PopupUpdate.ask(
                                model.withoutPopup(AmendPopup.class), new AmendStageAllPopup(popup.target())))
                        .getOrElse(() -> Next.of(model));
    }

    private static Next stageAllConfirmed(Model model) {
        List<RepoPath> paths = FileEntry.paths(model.repo().files().getOrElse(List.empty()), FileEntry::unstaged);
        return PopupUpdate.confirm(
                model, AmendStageAllPopup.class, popup -> Option.of(new AmendCmd(popup.target(), paths)));
    }
}
