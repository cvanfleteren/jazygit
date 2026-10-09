package net.vanfleteren.jazygit.state.update;

import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.AmendMsg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.AmendMsg.Cancelled;
import net.vanfleteren.jazygit.state.AmendMsg.Confirmed;
import net.vanfleteren.jazygit.state.AmendMsg.Done;
import net.vanfleteren.jazygit.state.AmendMsg.Failed;
import net.vanfleteren.jazygit.state.AmendMsg.Requested;
import net.vanfleteren.jazygit.state.Cmd.Amend;
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
            case Cancelled() -> Next.of(model.withAmendPrompt(false));
            case Confirmed() -> confirmed(model);
            case Done() -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String message) -> Update.refresh(model.withError(Optional.of("Amend failed: " + message)));
        };
    }

    /**
     * Asks for confirmation, when the last commit has something to be amended with.
     */
    private static Next requested(Model model) {
        return Update.files(model).isEmpty()
                ? Next.of(model.withError(Optional.of("Nothing to amend the last commit with")))
                : Next.of(model.withError(Optional.empty()).withAmendPrompt(true));
    }

    /**
     * Amends with the staged files; when none is staged, all files are added first.
     */
    private static Next confirmed(Model model) {
        List<FileEntry> files = Update.files(model);
        List<String> stage = files.stream().anyMatch(FileEntry::staged)
                ? List.of()
                : files.stream().filter(FileEntry::unstaged).map(FileEntry::path).toList();
        return model.amendPrompt()
                ? Next.of(model.withAmendPrompt(false), new Amend(stage))
                : Next.of(model);
    }
}
