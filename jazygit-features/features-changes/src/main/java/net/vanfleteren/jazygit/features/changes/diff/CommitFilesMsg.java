package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;

public sealed interface CommitFilesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommitFilesUpdate.update(model, this);
    }

    /**
     * The user asked for the files {@code of} changed, from the pane {@code pane}.
     */
    record Opened(Changes of, PaneId pane) implements CommitFilesMsg {}

    /**
     * The user asked for the files the branch {@code branch} changes: those a part knows of it, as its pull request's,
     * else those against its base (ADR 61, 171).
     */
    record BranchFilesRequested(BranchName branch) implements CommitFilesMsg {}

    /**
     * The user closed the files of the commit or the stash, to see the files of the working tree again.
     */
    record Closed() implements CommitFilesMsg {}

    /**
     * The user asked to list the next of the files of a change with remarks: all of them, those with an unresolved
     * remark thread, or those with any (ADR 57).
     */
    record ListedCycled() implements CommitFilesMsg {}

    /**
     * Lists all files of the commit the files end at, or only those changed again (ADR 128).
     */
    record AllToggled() implements CommitFilesMsg {}

    /**
     * The files of {@code from}, while they are listed, are to be listed as those of {@code to}, as a pull request that
     * moved on to other commits since its files were read (ADR 55).
     */
    record Moved(Changes from, Changes to) implements CommitFilesMsg {}
}
