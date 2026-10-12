package net.vanfleteren.jazygit.feature.selection;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * The user moved the highlight in one of the panes.
 */
public sealed interface SelectionMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SelectionUpdate.update(model, this);
    }

    /**
     * The user highlighted a branch in the branches pane.
     */
    record BranchSelected(String branch) implements SelectionMsg {
    }

    /**
     * The user highlighted a commit in the commits pane.
     */
    record CommitSelected(Sha sha) implements SelectionMsg {
    }

    /**
     * The user highlighted a node of the files tree; {@code files} are the changed files under it.
     */
    record FilesSelected(List<FileEntry> files) implements SelectionMsg {
    }
}
