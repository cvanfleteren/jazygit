package net.vanfleteren.jazygit.features.branches.branch.commits;

import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface BranchCommitsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BranchCommitsUpdate.update(model, this);
    }

    /**
     * The user asked for the commits of {@code branch}, local or remote, in place of the branches.
     */
    record Opened(BranchRef branch) implements BranchCommitsMsg {}

    /**
     * The user closed the commits of the branch, to see the branches again.
     */
    record Closed() implements BranchCommitsMsg {}
}
