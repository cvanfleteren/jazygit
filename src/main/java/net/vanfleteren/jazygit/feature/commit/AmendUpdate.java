package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.commit.AmendMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.AmendMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.AmendMsg.Requested;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Amend;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

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
        };
    }

    /**
     * Asks for confirmation, when the last commit has something to be amended with.
     */
    private static Next requested(Model model) {
        return Update.files(model).isEmpty()
                ? Next.of(model.fail(new AppError.NothingToAmend()))
                : Next.of(model.clearError().openPopup(new AmendPopup()));
    }

    /**
     * Amends with the staged files; when none is staged, all files are added first.
     */
    private static Next confirmed(Model model) {
        List<FileEntry> files = Update.files(model);
        List<String> stage = files.exists(FileEntry::staged)
                ? List.of()
                : files.filter(FileEntry::unstaged).map(FileEntry::path).toList();
        return model.popup(AmendPopup.class).isDefined()
                ? Next.of(model.withoutPopup(AmendPopup.class), new Amend(stage))
                : Next.of(model);
    }
}
