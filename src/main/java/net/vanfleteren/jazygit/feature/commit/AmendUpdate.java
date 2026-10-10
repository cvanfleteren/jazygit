package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Amend;

import net.vanfleteren.jazygit.feature.commit.AmendMsg.Requested;

import net.vanfleteren.jazygit.feature.commit.AmendMsg.Failed;

import net.vanfleteren.jazygit.feature.commit.AmendMsg.Done;

import net.vanfleteren.jazygit.feature.commit.AmendMsg.Confirmed;

import net.vanfleteren.jazygit.feature.commit.AmendMsg.Cancelled;

import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import java.util.List;
import java.util.Optional;

/**
 * Amending the last commit.
 */
public final class AmendUpdate {

    private AmendUpdate() {
    }

    public static Next update(Model model, AmendMsg msg) {
        return switch (msg) {
            case Requested() -> requested(model);
            case Cancelled() -> Next.of(model.withoutPopup(AmendPopup.class));
            case Confirmed() -> confirmed(model);
            case Done() -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String message) -> Update.refresh(model.withError(Optional.of(Messages.get("error.amend.failed", message))));
        };
    }

    /**
     * Asks for confirmation, when the last commit has something to be amended with.
     */
    private static Next requested(Model model) {
        return Update.files(model).isEmpty()
                ? Next.of(model.withError(Optional.of(Messages.get("error.amend.nothing"))))
                : Next.of(model.withError(Optional.empty()).openPopup(new AmendPopup()));
    }

    /**
     * Amends with the staged files; when none is staged, all files are added first.
     */
    private static Next confirmed(Model model) {
        List<FileEntry> files = Update.files(model);
        List<String> stage = files.stream().anyMatch(FileEntry::staged)
                ? List.of()
                : files.stream().filter(FileEntry::unstaged).map(FileEntry::path).toList();
        return model.popup(AmendPopup.class).isPresent()
                ? Next.of(model.withoutPopup(AmendPopup.class), new Amend(stage))
                : Next.of(model);
    }
}
