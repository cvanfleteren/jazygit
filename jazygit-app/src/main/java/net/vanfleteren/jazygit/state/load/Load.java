package net.vanfleteren.jazygit.state.load;

import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads part of the repository state into the {@link Model}.
 */
public sealed interface Load extends Cmd
        permits Targeted, DiffLoad, LoadBranches, CheckMetadata, LoadWorktreeChanges, LoadAhead, CheckFileDiff {

    @Override
    default Msg failed(Failure failure) {
        return new LoadMsg.Failed(this, failure);
    }
}
