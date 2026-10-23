package net.vanfleteren.jazygit.feature.branch.commits;

import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
