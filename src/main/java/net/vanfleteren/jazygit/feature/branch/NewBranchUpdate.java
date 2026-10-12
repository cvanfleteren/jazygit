package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CreateBranch;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Loadable;
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
        return model.commits() instanceof Loadable.Loaded<List<Commit>>(var commits) && index >= 0 && index < commits.size()
                ? Next.of(model.openPopup(new NewBranchPopup(commits.get(index).sha().value(), commits.get(index).shortSha())))
                : Next.of(model);
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        // A blank name keeps the dialog open.
        return model.popup(NewBranchPopup.class).map(NewBranchPopup::base)
                .filter(base -> !trimmed.isEmpty())
                .map(base -> Next.of(model.withoutPopup(NewBranchPopup.class).clearError(),
                        new CreateBranch(trimmed, base)))
                .getOrElse(() -> Next.of(model));
    }
}
