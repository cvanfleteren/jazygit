package net.vanfleteren.jazygit.feature.branch.pull;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface PullMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PullUpdate.update(model, this);
    }

    /**
     * The user asked to pull a branch.
     */
    record Requested(BranchName branch) implements PullMsg {}
}
