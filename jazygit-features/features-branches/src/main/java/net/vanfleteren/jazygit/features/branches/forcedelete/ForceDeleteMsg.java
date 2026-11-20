package net.vanfleteren.jazygit.features.branches.forcedelete;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Deleting a local branch that is not merged, losing the commits only it has.
 */
public sealed interface ForceDeleteMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ForceDeleteUpdate.update(model, this);
    }

    /**
     * Git refused to delete {@code branch} locally for {@code cmd}, which ran {@code commands}, because it is not
     * merged; {@code lost} commits are on no other branch, remote-tracking branch or tag.
     */
    record NotFullyMerged(OperationCmd cmd, BranchName branch, int lost, List<ExecutedCommand> commands)
            implements ForceDeleteMsg {}

    /**
     * The user agreed to lose the commits only the branch has by deleting it anyway.
     */
    record Confirmed() implements ForceDeleteMsg {}
}
