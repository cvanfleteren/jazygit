package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * The user moved the highlight in one of the panes.
 */
public sealed interface SelectionMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SelectionUpdate.update(model, this);
    }

    /**
     * The user highlighted a branch, local or remote, in the branches pane.
     */
    record BranchSelected(BranchRef branch) implements SelectionMsg {}

    /**
     * The user highlighted a commit in the commits pane.
     */
    record CommitSelected(Sha sha) implements SelectionMsg {}

    /**
     * The user highlighted a commit in the divergence the branches pane lists.
     */
    record DivergenceCommitSelected(Sha sha) implements SelectionMsg {}

    /**
     * The user highlighted a node of the files tree; {@code files} are the changed files under it.
     */
    record FilesSelected(List<FileEntry> files) implements SelectionMsg {}

    /**
     * The user highlighted the stash {@code sha} in the stash pane.
     */
    record StashSelected(Sha sha) implements SelectionMsg {}
}
