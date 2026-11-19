package net.vanfleteren.jazygit.features.branches.branch.tabs;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;

public sealed interface BranchesTabMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BranchesTabUpdate.update(model, this);
    }

    /**
     * The user asked for another list of the branches pane: the one {@code by} lists further, going round; -1 for
     * the previous one, 1 for the next one.
     */
    record Switched(int by) implements BranchesTabMsg {}

    /**
     * The user clicked the name of {@code tab} in the title of the branches pane.
     */
    record Chosen(BranchesTab tab) implements BranchesTabMsg {}
}
