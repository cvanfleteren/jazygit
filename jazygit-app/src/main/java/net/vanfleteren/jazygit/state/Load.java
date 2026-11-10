package net.vanfleteren.jazygit.state;

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
