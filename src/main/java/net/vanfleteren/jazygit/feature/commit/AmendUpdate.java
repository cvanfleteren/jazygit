package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.AmendMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.AmendMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.AmendMsg.Requested;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Amend;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

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
        return Update.withFiles(model, files -> files.isEmpty()
                ? Next.of(model.fail(new AppError.NothingToAmend()))
                : Update.ask(model, new AmendPopup()));
    }

    /**
     * Amends with the staged files; when none is staged, all files are added first.
     */
    private static Next confirmed(Model model) {
        List<FileEntry> files = Update.files(model);
        List<String> stage = files.exists(FileEntry::staged)
                ? List.of()
                : FileEntry.paths(files, FileEntry::unstaged);
        return Update.confirm(model, AmendPopup.class, popup -> Option.of(new Amend(stage)));
    }
}
