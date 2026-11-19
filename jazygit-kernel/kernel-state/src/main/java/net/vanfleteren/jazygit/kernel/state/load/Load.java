package net.vanfleteren.jazygit.kernel.state.load;

import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads part of the repository state into the {@link Model}.
 */
public sealed interface Load extends Cmd
        permits Targeted, LoadBranches, CheckMetadata, LoadWorktreeChanges, LoadAhead, CheckFileDiff {

    @Override
    default Msg failed(Failure failure) {
        return new LoadMsg.Failed(this, failure);
    }
}
