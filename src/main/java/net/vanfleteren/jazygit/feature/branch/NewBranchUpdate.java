package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Update;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CreateBranch;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Requested;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating a branch.
 */
public final class NewBranchUpdate {

    private NewBranchUpdate() {
    }

    public static Next update(Model model, NewBranchMsg msg) {
        return switch (msg) {
            case Requested(String base) -> Next.of(model.openPopup(new NewBranchPopup(base)));
            case NewBranchMsg.RequestedAtCommit(int index) -> atCommit(model, index);
            case Cancelled() -> Next.of(model.withoutPopup(NewBranchPopup.class));
            case Confirmed(String name) -> confirmed(model, name);
        };
    }

    private static Next atCommit(Model model, int index) {
        return model.commitAt(index)
                .map(commit -> Next.of(model.openPopup(new NewBranchPopup(commit.sha().value(), commit.shortSha()))))
                .getOrElse(() -> Next.of(model));
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        // A blank name keeps the dialog open.
        return Update.confirm(model, NewBranchPopup.class, popup -> Option.of(trimmed)
                .filter(branch -> !branch.isEmpty())
                .map(branch -> new CreateBranch(branch, popup.base())));
    }
}
