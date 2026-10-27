package net.vanfleteren.jazygit.feature.branch.merge;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface MergeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return MergeUpdate.update(model, this);
    }

    /**
     * The user asked to merge {@code branch} into the checked out branch; how is still to be chosen.
     */
    record Requested(BranchName branch) implements MergeMsg {}

    /**
     * The user chose how to merge.
     */
    record Chosen(MergeMode mode) implements MergeMsg {}
}
