package net.vanfleteren.jazygit.feature.branch.nuke;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
