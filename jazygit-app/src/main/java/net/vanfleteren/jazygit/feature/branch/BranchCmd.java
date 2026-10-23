package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * A command that acts on a branch. Each branch operation has its own, in its subpackage.
 */
public interface BranchCmd extends GitOperationCmd {

    /**
     * The branch the command acts on.
     */
    BranchName branch();

    @Override
    default Option<String> subject() {
        return Option.of(branch().value());
    }

    /**
     * A command that keeps its branch busy while it runs, as it talks to the remote or deletes it.
     */
    interface KeepsBranchBusy extends BranchCmd {

        @Override
        default Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch()));
        }
    }

    /**
     * A command that leaves a local branch checked out, new or not. Once it succeeded, the branches pane shows the
     * local branches, where that branch is then highlighted, also when it was started from the remote ones.
     */
    interface ChecksOutBranch extends BranchCmd {

        @Override
        default Msg succeeded(List<ExecutedCommand> commands) {
            return new CheckoutMsg.CheckedOut(this, commands);
        }
    }
}
