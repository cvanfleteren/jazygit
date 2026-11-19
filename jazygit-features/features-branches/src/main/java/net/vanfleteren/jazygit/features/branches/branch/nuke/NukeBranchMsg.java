package net.vanfleteren.jazygit.features.branches.branch.nuke;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface NukeBranchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return NukeBranchUpdate.update(model, this);
    }

    /**
     * The user asked to nuke a branch; that is still to be confirmed.
     */
    record Requested(BranchName branch) implements NukeBranchMsg {}

    record Confirmed() implements NukeBranchMsg {}

    /**
     * The nuke ran {@code commands}, and did not delete all it was to delete.
     *
     * @param left     what is left, named as the dialog named it
     * @param failures why, one for each step that failed
     */
    record NotAllNuked(NukeBranchCmd cmd, List<ExecutedCommand> commands, List<String> left, List<Failure> failures)
            implements NukeBranchMsg {}
}
