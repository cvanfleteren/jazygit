package net.vanfleteren.jazygit.feature.branch.rebase;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface RebaseMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RebaseUpdate.update(model, this);
    }

    /**
     * Where the checked out branch is rebased onto: the highlighted branch, or its base branch (ADR 91).
     */
    enum Target {
        ONTO,
        BASE
    }

    /**
     * The user asked to rebase the checked out branch, on {@code highlighted}; where onto is still to be chosen.
     */
    record Requested(BranchName highlighted) implements RebaseMsg {}

    /**
     * The user chose where to rebase onto.
     */
    record Chosen(Target target) implements RebaseMsg {}
}
